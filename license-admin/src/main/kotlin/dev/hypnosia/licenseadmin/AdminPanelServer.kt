package dev.hypnosia.licenseadmin

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 9090
private const val DEFAULT_DATA_FILE = "data/licenses.tsv"
private const val DEFAULT_BACKUP_DIR = "data/backups"
private val roles = setOf("USER", "PREMIUM", "QA", "ADMIN", "OWNER")
private val licenseRegex = Regex("^[A-Z0-9]{32}$")

fun main() {
    val host = env("HYPNOSIA_ADMIN_HOST") ?: DEFAULT_HOST
    val port = env("HYPNOSIA_ADMIN_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val password = env("HYPNOSIA_ADMIN_PASSWORD")

    if (password == null) {
        System.err.println("HYPNOSIA_ADMIN_PASSWORD is required.")
        return
    }

    val storage = LicenseStorage(Path.of(env("HYPNOSIA_LICENSE_DATA") ?: DEFAULT_DATA_FILE))
    val backupDir = Path.of(env("HYPNOSIA_BACKUP_DIR") ?: DEFAULT_BACKUP_DIR)
    val auth = BasicAuth(
        username = env("HYPNOSIA_ADMIN_USER") ?: "admin",
        password = password,
    )

    val panel = AdminPanel(storage, backupDir, auth)
    val server = HttpServer.create(InetSocketAddress(host, port), 0)
    server.createContext("/") { exchange -> panel.route(exchange) }
    server.executor = Executors.newFixedThreadPool(4)
    server.start()

    println("Hypnosia admin panel listening on http://$host:$port")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(2) })
    CountDownLatch(1).await()
}

private class AdminPanel(
    private val storage: LicenseStorage,
    private val backupDir: Path,
    private val auth: BasicAuth,
) {
    fun route(exchange: HttpExchange) {
        try {
            if (!auth.authorized(exchange)) {
                return unauthorized(exchange)
            }

            when (exchange.requestURI.path) {
                "/", "/licenses" -> requireMethod(exchange, "GET") { index(exchange) }
                "/create" -> requireMethod(exchange, "POST") { create(exchange) }
                "/update" -> requireMethod(exchange, "POST") { update(exchange) }
                "/reset-hwid" -> requireMethod(exchange, "POST") { resetHwid(exchange) }
                "/delete" -> requireMethod(exchange, "POST") { delete(exchange) }
                "/backup" -> requireMethod(exchange, "POST") { backup(exchange) }
                else -> text(exchange, 404, "Not found")
            }
        } catch (error: IllegalArgumentException) {
            redirect(exchange, "/?error=${url(error.message ?: "Invalid request")}")
        } catch (error: Throwable) {
            error.printStackTrace()
            redirect(exchange, "/?error=${url(error.message ?: "Server error")}")
        }
    }

    private fun index(exchange: HttpExchange) {
        val records = storage.all()
        val message = query(exchange, "msg")
        val error = query(exchange, "error")
        val stats = LicenseStats.from(records)

        html(exchange, page("Hypnosia Admin") {
            append("<section class=\"hero\">")
            append("<div><h1>Hypnosia Admin</h1><p>Local panel through SSH tunnel only.</p></div>")
            append("<form method=\"post\" action=\"/backup\"><button>Backup now</button></form>")
            append("</section>")

            if (message != null) append("<div class=\"notice ok\">${esc(message)}</div>")
            if (error != null) append("<div class=\"notice err\">${esc(error)}</div>")

            append("<section class=\"stats\">")
            stat("Total", stats.total)
            stat("Bound", stats.bound)
            stat("Not bound", stats.notBound)
            stat("Disabled", stats.disabled)
            stat("Expired", stats.expired)
            append("</section>")

            append("<section class=\"card\"><h2>Create license</h2>")
            append("<form class=\"grid\" method=\"post\" action=\"/create\">")
            selectRole()
            input("expires", "Expires", "never", "YYYY-MM-DD or never")
            input("key", "Custom key", "", "empty = generate")
            append("<button>Create</button>")
            append("</form></section>")

            append("<section class=\"card\"><h2>Licenses</h2>")
            if (records.isEmpty()) {
                append("<p class=\"muted\">No licenses yet.</p>")
            } else {
                append("<div class=\"table\">")
                append("<div class=\"row head\"><span>Key</span><span>Role</span><span>State</span><span>Expires</span><span>HWID</span><span>Actions</span></div>")
                records.forEach { record -> licenseRow(record) }
                append("</div>")
            }
            append("</section>")
        })
    }

    private fun StringBuilder.licenseRow(record: LicenseRecord) {
        val state = when {
            record.disabled -> "disabled"
            record.isExpired() -> "expired"
            record.hwidHash == null -> "not-bound"
            else -> "bound"
        }

        append("<div class=\"row\">")
        append("<code>${esc(record.licenseKey)}</code>")
        append("<span>${esc(record.role)}</span>")
        append("<span class=\"pill $state\">$state</span>")
        append("<span>${esc(record.expiresAt?.substringBefore('T') ?: "never")}</span>")
        append("<span>${esc(record.hwidHash?.take(12) ?: "-")}</span>")
        append("<div class=\"actions\">")
        append("<form method=\"post\" action=\"/update\">")
        hidden("key", record.licenseKey)
        append("<select name=\"role\">")
        roles.forEach { role ->
            val selected = if (role == record.role) " selected" else ""
            append("<option$selected>$role</option>")
        }
        append("</select>")
        append("<input name=\"expires\" value=\"${esc(record.expiresAt?.substringBefore('T') ?: "never")}\">")
        append("<label><input type=\"checkbox\" name=\"disabled\" value=\"true\" ${if (record.disabled) "checked" else ""}> disabled</label>")
        append("<button>Save</button>")
        append("</form>")
        postButton("/reset-hwid", record.licenseKey, "Reset HWID")
        postButton("/delete", record.licenseKey, "Delete", danger = true)
        append("</div>")
        append("</div>")
    }

    private fun create(exchange: HttpExchange) {
        val form = exchange.form()
        val role = form["role"]?.uppercaseRole() ?: throw IllegalArgumentException("Invalid role")
        val expiresAt = parseExpiresArgument(form["expires"])
        val key = form["key"]?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: generateLicenseKey()
        require(licenseRegex.matches(key)) { "License key must be 32 uppercase letters/digits" }

        storage.create(
            LicenseRecord(
                licenseKey = key,
                role = role,
                hwidHash = null,
                createdAt = Instant.now().toString(),
                boundAt = null,
                expiresAt = expiresAt,
                disabled = false,
            ),
        )
        redirect(exchange, "/?msg=${url("Created $key")}")
    }

    private fun update(exchange: HttpExchange) {
        val form = exchange.form()
        val key = form.key()
        val role = form["role"]?.uppercaseRole() ?: throw IllegalArgumentException("Invalid role")
        val expiresAt = parseExpiresArgument(form["expires"])
        val disabled = form["disabled"] == "true"
        storage.updateExisting(key) { copy(role = role, expiresAt = expiresAt, disabled = disabled) }
        redirect(exchange, "/?msg=${url("Updated $key")}")
    }

    private fun resetHwid(exchange: HttpExchange) {
        val key = exchange.form().key()
        storage.updateExisting(key) { copy(hwidHash = null, boundAt = null) }
        redirect(exchange, "/?msg=${url("HWID reset $key")}")
    }

    private fun delete(exchange: HttpExchange) {
        val key = exchange.form().key()
        storage.deleteExisting(key)
        redirect(exchange, "/?msg=${url("Deleted $key")}")
    }

    private fun backup(exchange: HttpExchange) {
        backupDir.createDirectories()
        val target = backupDir.resolve("licenses-${Instant.now().toString().replace(":", "-")}.tsv")
        storage.copyTo(target)
        redirect(exchange, "/?msg=${url("Backup created ${target.fileName}")}")
    }

    private fun requireMethod(exchange: HttpExchange, method: String, block: () -> Unit) {
        if (exchange.requestMethod != method) {
            text(exchange, 405, "Method not allowed")
            return
        }
        block()
    }
}

private class LicenseStorage(private val file: Path) {
    private val lock = ReentrantReadWriteLock()

    init {
        file.parent?.createDirectories()
        if (!file.exists()) {
            file.writeText("")
        }
    }

    fun all(): List<LicenseRecord> {
        return lock.read { readAll().sortedByDescending { it.createdAt } }
    }

    fun create(record: LicenseRecord) {
        lock.write {
            val records = readAll().toMutableList()
            require(records.none { it.licenseKey == record.licenseKey }) { "License already exists" }
            records += record
            writeAll(records)
        }
    }

    fun updateExisting(key: String, transform: LicenseRecord.() -> LicenseRecord) {
        lock.write {
            var found = false
            val records = readAll().map { record ->
                if (record.licenseKey == key) {
                    found = true
                    record.transform()
                } else {
                    record
                }
            }
            require(found) { "License not found" }
            writeAll(records)
        }
    }

    fun deleteExisting(key: String) {
        lock.write {
            val records = readAll()
            require(records.any { it.licenseKey == key }) { "License not found" }
            writeAll(records.filterNot { it.licenseKey == key })
        }
    }

    fun copyTo(target: Path) {
        lock.read {
            target.parent?.createDirectories()
            Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readAll(): List<LicenseRecord> {
        return file.readLines(StandardCharsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull(LicenseRecord::fromLine)
    }

    private fun writeAll(records: List<LicenseRecord>) {
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(records.joinToString("\n") { it.toLine() } + if (records.isEmpty()) "" else "\n")
        runCatching {
            Files.move(
                tmp,
                file,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private data class LicenseRecord(
    val licenseKey: String,
    val role: String,
    val hwidHash: String?,
    val createdAt: String,
    val boundAt: String?,
    val expiresAt: String?,
    val disabled: Boolean,
) {
    fun isExpired(): Boolean {
        val expires = expiresAt ?: return false
        return runCatching { Instant.parse(expires).isBefore(Instant.now()) }.getOrDefault(false)
    }

    fun toLine(): String {
        return listOf(
            licenseKey,
            role,
            hwidHash ?: "",
            createdAt,
            boundAt ?: "",
            expiresAt ?: "",
            disabled.toString(),
        ).joinToString("\t") { it.replace("\t", " ") }
    }

    companion object {
        fun fromLine(line: String): LicenseRecord? {
            val parts = line.split('\t')
            if (parts.size < 7) return null
            return LicenseRecord(
                licenseKey = parts[0],
                role = parts[1],
                hwidHash = parts[2].ifBlank { null },
                createdAt = parts[3],
                boundAt = parts[4].ifBlank { null },
                expiresAt = parts[5].ifBlank { null },
                disabled = parts[6].toBooleanStrictOrNull() ?: false,
            )
        }
    }
}

private data class LicenseStats(
    val total: Int,
    val bound: Int,
    val notBound: Int,
    val disabled: Int,
    val expired: Int,
) {
    companion object {
        fun from(records: List<LicenseRecord>): LicenseStats {
            return LicenseStats(
                total = records.size,
                bound = records.count { it.hwidHash != null },
                notBound = records.count { it.hwidHash == null },
                disabled = records.count { it.disabled },
                expired = records.count { it.isExpired() },
            )
        }
    }
}

private class BasicAuth(private val username: String, private val password: String) {
    private val expected = "Basic " + Base64.getEncoder()
        .encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))

    fun authorized(exchange: HttpExchange): Boolean {
        val provided = exchange.requestHeaders.getFirst("Authorization") ?: return false
        return MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

private fun unauthorized(exchange: HttpExchange) {
    exchange.responseHeaders.set("WWW-Authenticate", """Basic realm="Hypnosia Admin"""")
    text(exchange, 401, "Unauthorized")
}

private fun page(title: String, body: StringBuilder.() -> Unit): String {
    val content = buildString(body)
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${esc(title)}</title>
          <style>
            :root { color-scheme: dark; --bg:#080808; --panel:#111; --line:#272727; --text:#f4f4f4; --muted:#9a9aa2; --accent:#72e0ff; --danger:#ff6969; }
            * { box-sizing: border-box; }
            body { margin: 0; padding: 28px; background: var(--bg); color: var(--text); font: 15px/1.45 system-ui, Segoe UI, Arial, sans-serif; }
            h1, h2 { margin: 0; }
            h1 { font-size: 30px; }
            h2 { font-size: 18px; margin-bottom: 14px; }
            p { color: var(--muted); margin: 6px 0 0; }
            button, input, select { border: 1px solid var(--line); border-radius: 8px; background: #0b0b0b; color: var(--text); padding: 9px 11px; }
            button { cursor: pointer; background: #181818; font-weight: 650; }
            button:hover { border-color: var(--accent); }
            code { font-family: ui-monospace, Cascadia Mono, Consolas, monospace; }
            .hero, .card, .stats > div { border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
            .hero { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 20px; margin-bottom: 18px; }
            .card { padding: 18px; margin-top: 18px; overflow-x: auto; }
            .stats { display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)); gap: 12px; }
            .stats > div { padding: 14px; }
            .stats b { display: block; color: var(--muted); font-size: 12px; text-transform: uppercase; }
            .stats span { display: block; font-size: 24px; font-weight: 750; margin-top: 4px; }
            .grid { display: grid; grid-template-columns: 180px 220px 1fr auto; gap: 10px; align-items: end; }
            .field label { display: block; color: var(--muted); font-size: 12px; margin-bottom: 5px; }
            .field input, .field select { width: 100%; }
            .notice { padding: 12px 14px; border-radius: 8px; margin: 12px 0; border: 1px solid var(--line); }
            .notice.ok { color: #97f2a8; }
            .notice.err { color: var(--danger); }
            .table { min-width: 1050px; }
            .row { display: grid; grid-template-columns: 280px 90px 100px 130px 100px 1fr; gap: 10px; align-items: center; padding: 10px 0; border-top: 1px solid var(--line); }
            .row.head { color: var(--muted); font-size: 12px; text-transform: uppercase; border-top: 0; }
            .actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
            .actions form { display: flex; gap: 8px; align-items: center; }
            .actions input { width: 118px; }
            .pill { display:inline-flex; width:max-content; padding: 4px 8px; border-radius: 999px; border: 1px solid var(--line); color: var(--muted); }
            .pill.bound { color:#97f2a8; }
            .pill.not-bound { color:#ffd37a; }
            .pill.disabled, .pill.expired { color:var(--danger); }
            .danger { color: var(--danger); }
            .muted { color: var(--muted); }
          </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()
}

private fun StringBuilder.stat(label: String, value: Int) {
    append("<div><b>${esc(label)}</b><span>$value</span></div>")
}

private fun StringBuilder.input(name: String, label: String, value: String, placeholder: String) {
    append("<div class=\"field\"><label>${esc(label)}</label><input name=\"${esc(name)}\" value=\"${esc(value)}\" placeholder=\"${esc(placeholder)}\"></div>")
}

private fun StringBuilder.selectRole() {
    append("<div class=\"field\"><label>Role</label><select name=\"role\">")
    roles.forEach { append("<option>$it</option>") }
    append("</select></div>")
}

private fun StringBuilder.hidden(name: String, value: String) {
    append("<input type=\"hidden\" name=\"${esc(name)}\" value=\"${esc(value)}\">")
}

private fun StringBuilder.postButton(action: String, key: String, label: String, danger: Boolean = false) {
    append("<form method=\"post\" action=\"${esc(action)}\">")
    hidden("key", key)
    append("<button${if (danger) " class=\"danger\"" else ""}>${esc(label)}</button>")
    append("</form>")
}

private fun HttpExchange.form(): Map<String, String> {
    val raw = requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    if (raw.isBlank()) return emptyMap()
    return raw.split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=')
        if (name.isBlank()) return@mapNotNull null
        val value = pair.substringAfter('=', "")
        decode(name) to decode(value)
    }.toMap()
}

private fun Map<String, String>.key(): String {
    val key = get("key")?.trim()?.uppercase(Locale.ROOT) ?: throw IllegalArgumentException("Missing key")
    require(licenseRegex.matches(key)) { "Invalid key" }
    return key
}

private fun query(exchange: HttpExchange, name: String): String? {
    return exchange.requestURI.rawQuery
        ?.split('&')
        ?.mapNotNull {
            val key = it.substringBefore('=')
            val value = it.substringAfter('=', "")
            if (decode(key) == name) decode(value) else null
        }
        ?.firstOrNull()
}

private fun parseExpiresArgument(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank() || raw.equals("never", ignoreCase = true)) return null
    return try {
        LocalDate.parse(raw)
            .plusDays(1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toString()
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("Date must be YYYY-MM-DD or never")
    }
}

private fun generateLicenseKey(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    return buildString(32) {
        repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}

private fun String.uppercaseRole(): String? {
    val role = trim().uppercase(Locale.ROOT)
    return role.takeIf { it in roles }
}

private fun html(exchange: HttpExchange, body: String) {
    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
    write(exchange, 200, body)
}

private fun text(exchange: HttpExchange, code: Int, body: String) {
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    write(exchange, code, body)
}

private fun redirect(exchange: HttpExchange, location: String) {
    exchange.responseHeaders.set("Location", location)
    exchange.sendResponseHeaders(303, -1)
    exchange.close()
}

private fun write(exchange: HttpExchange, code: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun decode(value: String): String {
    return URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private fun url(value: String): String {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
}

private fun esc(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun env(name: String): String? {
    return System.getenv(name)?.takeIf { it.isNotBlank() }
}
