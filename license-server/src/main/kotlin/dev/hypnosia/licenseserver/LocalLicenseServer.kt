package dev.hypnosia.licenseserver

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
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
private const val DEFAULT_PORT = 8080
private const val DEFAULT_DATA_FILE = "data/licenses.tsv"
private const val DEFAULT_CLOUD_CONFIG_FILE = "data/cloud-configs.tsv"
private const val MAX_CLOUD_CONFIGS_PER_HWID = 3
private const val MAX_CLOUD_CONFIG_PAYLOAD_BYTES = 64 * 1024
private val roles = setOf("USER", "PREMIUM", "QA", "ADMIN", "OWNER")
private val licenseRegex = Regex("^[A-Z0-9]{32}$")
private val cloudConfigKeyRegex = Regex("^[A-Z0-9]{8}$")
private val hwidHashRegex = Regex("^[A-Fa-f0-9]{64}$")

fun main(args: Array<String>) {
    val host = env("HYPNOSIA_LICENSE_HOST") ?: DEFAULT_HOST
    val port = env("HYPNOSIA_LICENSE_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val dataFile = Path.of(env("HYPNOSIA_LICENSE_DATA") ?: DEFAULT_DATA_FILE)
    val cloudConfigFile = Path.of(env("HYPNOSIA_CLOUD_CONFIG_DATA") ?: DEFAULT_CLOUD_CONFIG_FILE)

    val storage = LicenseStorage(dataFile)
    val cloudConfigStorage = CloudConfigStorage(cloudConfigFile)

    if (args.isNotEmpty()) {
        ConsoleAdmin(storage).execute(args.toList())
        return
    }

    val server = HttpServer.create(InetSocketAddress(host, port), 0)
    server.createContext("/api/license/check") { exchange -> checkLicense(exchange, storage) }
    server.createContext("/api/cloud-config/save") { exchange -> saveCloudConfig(exchange, cloudConfigStorage) }
    server.createContext("/api/cloud-config/load") { exchange -> loadCloudConfig(exchange, cloudConfigStorage) }
    server.createContext("/api/cloud-config/delete") { exchange -> deleteCloudConfig(exchange, cloudConfigStorage) }
    server.createContext("/health") { exchange -> json(exchange, 200, """{"ok":true}""") }
    server.executor = Executors.newFixedThreadPool(8)
    server.start()

    println("Hypnosia license server listening on http://$host:$port")
    println("Data file: ${dataFile.toAbsolutePath()}")
    println("Type 'help' for commands.")

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(2)
    })

    val consoleEnabled = env("HYPNOSIA_LICENSE_CONSOLE")
        ?.toBooleanStrictOrNull()
        ?: true

    if (consoleEnabled) {
        ConsoleAdmin(storage).loop()
        server.stop(0)
    } else {
        CountDownLatch(1).await()
    }
}

private class ConsoleAdmin(private val storage: LicenseStorage) {
    fun loop() {
        while (true) {
            print("hypnosia-license> ")
            val line = readlnOrNull()?.trim() ?: break
            if (line.isBlank()) {
                continue
            }

            val args = splitArgs(line)
            if (!execute(args)) {
                return
            }
        }
    }

    fun execute(args: List<String>): Boolean {
        val command = args.firstOrNull()?.lowercase(Locale.ROOT) ?: return true
        try {
            when (command) {
                "help", "?" -> help()
                "list", "ls" -> list()
                "show" -> show(args)
                "create", "new" -> create(args)
                "role" -> role(args)
                "expires", "expire" -> expires(args)
                "disable" -> setDisabled(args, disabled = true)
                "enable" -> setDisabled(args, disabled = false)
                "reset", "reset-hwid" -> resetHwid(args)
                "delete", "del", "remove" -> delete(args)
                "exit", "quit", "stop" -> return false
                else -> println("Unknown command '$command'. Type 'help'.")
            }
        } catch (error: IllegalArgumentException) {
            println("Error: ${error.message}")
        } catch (error: Throwable) {
            println("Unexpected error: ${error.message}")
            error.printStackTrace()
        }
        return true
    }

    private fun help() {
        println(
            """
            Commands:
              license-server <command> [args]

            Interactive/server commands:
              list
              show <key>
              create <role> [YYYY-MM-DD|never] [custom-32-char-key]
              role <key> <role>
              expires <key> <YYYY-MM-DD|never>
              disable <key>
              enable <key>
              reset-hwid <key>
              delete <key>
              exit

            Roles: ${roles.joinToString(", ")}
            """.trimIndent(),
        )
    }

    private fun list() {
        val records = storage.all()
        if (records.isEmpty()) {
            println("No licenses.")
            return
        }

        records.forEach { record ->
            val state = when {
                record.disabled -> "disabled"
                record.isExpired() -> "expired"
                record.hwidHash == null -> "not-bound"
                else -> "bound"
            }
            println(
                "${record.licenseKey} | ${record.role} | $state | expires=${record.expiresAt ?: "never"} | hwid=${record.hwidHash?.take(12) ?: "-"}",
            )
        }
    }

    private fun show(args: List<String>) {
        val key = args.getKey(1)
        val record = storage.find(key) ?: throw IllegalArgumentException("License not found")
        println("key       : ${record.licenseKey}")
        println("role      : ${record.role}")
        println("disabled  : ${record.disabled}")
        println("created   : ${record.createdAt}")
        println("expires   : ${record.expiresAt ?: "never"}")
        println("boundAt   : ${record.boundAt ?: "-"}")
        println("hwidHash  : ${record.hwidHash ?: "-"}")
    }

    private fun create(args: List<String>) {
        val role = args.getOrNull(1)?.uppercaseRole() ?: throw IllegalArgumentException("Usage: create <role> [YYYY-MM-DD|never] [key]")
        val expiresAt = parseExpiresArgument(args.getOrNull(2))
        val key = args.getOrNull(3)?.uppercase(Locale.ROOT) ?: generateLicenseKey()
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
        println("Created: $key | role=$role | expires=${expiresAt ?: "never"}")
    }

    private fun role(args: List<String>) {
        val key = args.getKey(1)
        val role = args.getOrNull(2)?.uppercaseRole() ?: throw IllegalArgumentException("Usage: role <key> <role>")
        storage.updateExisting(key) { copy(role = role) }
        println("Updated role: $key -> $role")
    }

    private fun expires(args: List<String>) {
        val key = args.getKey(1)
        val expiresAt = parseExpiresArgument(args.getOrNull(2))
        storage.updateExisting(key) { copy(expiresAt = expiresAt) }
        println("Updated expires: $key -> ${expiresAt ?: "never"}")
    }

    private fun setDisabled(args: List<String>, disabled: Boolean) {
        val key = args.getKey(1)
        storage.updateExisting(key) { copy(disabled = disabled) }
        println("${if (disabled) "Disabled" else "Enabled"}: $key")
    }

    private fun resetHwid(args: List<String>) {
        val key = args.getKey(1)
        storage.updateExisting(key) { copy(hwidHash = null, boundAt = null) }
        println("HWID reset: $key")
    }

    private fun delete(args: List<String>) {
        val key = args.getKey(1)
        storage.deleteExisting(key)
        println("Deleted: $key")
    }

    private fun List<String>.getKey(index: Int): String {
        val key = getOrNull(index)?.uppercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Missing license key")
        require(licenseRegex.matches(key)) { "Invalid license key format" }
        return key
    }

    private fun String.uppercaseRole(): String? {
        val role = trim().uppercase(Locale.ROOT)
        return role.takeIf { it in roles }
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

    fun find(key: String): LicenseRecord? {
        return lock.read { readAll().firstOrNull { it.licenseKey == key } }
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

    fun checkAndBind(key: String, hwidHash: String): LicenseCheckResult {
        return lock.write {
            val records = readAll().toMutableList()
            val index = records.indexOfFirst { it.licenseKey == key }
            if (index == -1) {
                return@write LicenseCheckResult.Invalid("LICENSE_NOT_FOUND")
            }

            val record = records[index]
            if (record.disabled) {
                return@write LicenseCheckResult.Invalid("LICENSE_DISABLED")
            }
            if (record.isExpired()) {
                return@write LicenseCheckResult.Invalid("LICENSE_EXPIRED")
            }

            if (record.hwidHash == null) {
                val updated = record.copy(
                    hwidHash = hwidHash.uppercase(Locale.ROOT),
                    boundAt = Instant.now().toString(),
                )
                records[index] = updated
                writeAll(records)
                return@write LicenseCheckResult.Valid(updated.role, "BOUND_NOW", updated.expiresAt)
            }

            if (!record.hwidHash.equals(hwidHash, ignoreCase = true)) {
                return@write LicenseCheckResult.Invalid("HWID_MISMATCH")
            }

            LicenseCheckResult.Valid(record.role, "OK", record.expiresAt)
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
            Files.move(
                tmp,
                file,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
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
            if (parts.size < 7) {
                return null
            }
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

private class CloudConfigStorage(private val file: Path) {
    private val lock = ReentrantReadWriteLock()

    init {
        file.parent?.createDirectories()
        if (!file.exists()) {
            file.writeText("")
        }
    }

    fun create(
        ownerHwidHash: String,
        ownerLicenseKey: String?,
        name: String,
        payloadBase64: String,
    ): CloudConfigCreateResult {
        return lock.write {
            val records = readAll().toMutableList()
            val used = records.count { it.ownerHwidHash.equals(ownerHwidHash, ignoreCase = true) && !it.disabled }
            if (used >= MAX_CLOUD_CONFIGS_PER_HWID) {
                return@write CloudConfigCreateResult.LimitReached(used)
            }

            val now = Instant.now().toString()
            val key = generateCloudConfigKey(records.asSequence().map { it.configKey }.toSet())
            records += CloudConfigRecord(
                configKey = key,
                ownerHwidHash = ownerHwidHash.uppercase(Locale.ROOT),
                ownerLicenseKey = ownerLicenseKey,
                name = name,
                createdAt = now,
                updatedAt = now,
                disabled = false,
                payloadBase64 = payloadBase64,
            )
            writeAll(records)
            CloudConfigCreateResult.Created(key, used + 1)
        }
    }

    fun findActive(key: String): CloudConfigRecord? {
        return lock.read {
            readAll().firstOrNull { it.configKey == key && !it.disabled }
        }
    }

    fun deleteOwned(key: String, ownerHwidHash: String): Boolean {
        return lock.write {
            val records = readAll()
            val owned = records.any {
                it.configKey == key && it.ownerHwidHash.equals(ownerHwidHash, ignoreCase = true)
            }
            if (!owned) {
                return@write false
            }
            writeAll(records.filterNot { it.configKey == key })
            true
        }
    }

    private fun readAll(): List<CloudConfigRecord> {
        return file.readLines(StandardCharsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull(CloudConfigRecord::fromLine)
    }

    private fun writeAll(records: List<CloudConfigRecord>) {
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

private sealed class CloudConfigCreateResult {
    data class Created(val configKey: String, val used: Int) : CloudConfigCreateResult()
    data class LimitReached(val used: Int) : CloudConfigCreateResult()
}

private data class CloudConfigRecord(
    val configKey: String,
    val ownerHwidHash: String,
    val ownerLicenseKey: String?,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val disabled: Boolean,
    val payloadBase64: String,
) {
    fun toLine(): String {
        return listOf(
            configKey,
            ownerHwidHash,
            ownerLicenseKey ?: "",
            name,
            createdAt,
            updatedAt,
            disabled.toString(),
            payloadBase64,
        ).joinToString("\t") { it.replace("\t", " ") }
    }

    companion object {
        fun fromLine(line: String): CloudConfigRecord? {
            val parts = line.split('\t')
            if (parts.size < 8) {
                return null
            }
            return CloudConfigRecord(
                configKey = parts[0],
                ownerHwidHash = parts[1],
                ownerLicenseKey = parts[2].ifBlank { null },
                name = parts[3],
                createdAt = parts[4],
                updatedAt = parts[5],
                disabled = parts[6].toBooleanStrictOrNull() ?: false,
                payloadBase64 = parts[7],
            )
        }
    }
}

private sealed class LicenseCheckResult {
    data class Valid(val role: String, val status: String, val expiresAt: String?) : LicenseCheckResult()
    data class Invalid(val reason: String) : LicenseCheckResult()
}

private fun checkLicense(exchange: HttpExchange, storage: LicenseStorage) {
    if (exchange.requestMethod != "POST") {
        return text(exchange, 405, "Method not allowed")
    }

    val body = exchange.bodyString()
    val license = jsonString(body, "license")?.trim()?.uppercase(Locale.ROOT)
    val hwidHash = jsonString(body, "hwidHash")?.trim()

    if (license == null || !licenseRegex.matches(license)) {
        return json(exchange, 200, invalidJson("LICENSE_FORMAT"))
    }
    if (hwidHash == null || !hwidHashRegex.matches(hwidHash)) {
        return json(exchange, 200, invalidJson("HWID_FORMAT"))
    }

    val result = storage.checkAndBind(license, hwidHash)
    json(exchange, 200, result.toJson())
}

private fun saveCloudConfig(exchange: HttpExchange, storage: CloudConfigStorage) {
    if (exchange.requestMethod != "POST") {
        return text(exchange, 405, "Method not allowed")
    }

    val body = exchange.bodyString()
    val hwidHash = jsonString(body, "hwidHash")?.trim()?.uppercase(Locale.ROOT)
    val ownerLicenseKey = jsonString(body, "license")?.trim()?.uppercase(Locale.ROOT)
        ?.takeIf { licenseRegex.matches(it) }
    val name = jsonString(body, "name")?.trim()?.take(64)?.takeIf { it.isNotBlank() } ?: "Shared config"
    val payloadBase64 = jsonString(body, "payloadBase64")?.trim()

    if (hwidHash == null || !hwidHashRegex.matches(hwidHash)) {
        return json(exchange, 200, cloudError("HWID_FORMAT"))
    }
    if (payloadBase64 == null) {
        return json(exchange, 200, cloudError("PAYLOAD_MISSING"))
    }

    val payloadBytes = runCatching { java.util.Base64.getDecoder().decode(payloadBase64) }.getOrNull()
    if (payloadBytes == null || payloadBytes.isEmpty()) {
        return json(exchange, 200, cloudError("PAYLOAD_FORMAT"))
    }
    if (payloadBytes.size > MAX_CLOUD_CONFIG_PAYLOAD_BYTES) {
        return json(exchange, 200, cloudError("PAYLOAD_TOO_LARGE"))
    }

    val result = storage.create(
        ownerHwidHash = hwidHash,
        ownerLicenseKey = ownerLicenseKey,
        name = name,
        payloadBase64 = payloadBase64,
    )

    when (result) {
        is CloudConfigCreateResult.Created -> {
            json(
                exchange,
                200,
                """{"ok":true,"configKey":"${result.configKey}","used":${result.used},"limit":$MAX_CLOUD_CONFIGS_PER_HWID}""",
            )
        }
        is CloudConfigCreateResult.LimitReached -> {
            json(exchange, 200, """{"ok":false,"status":"LIMIT_REACHED","used":${result.used},"limit":$MAX_CLOUD_CONFIGS_PER_HWID}""")
        }
    }
}

private fun loadCloudConfig(exchange: HttpExchange, storage: CloudConfigStorage) {
    if (exchange.requestMethod != "POST") {
        return text(exchange, 405, "Method not allowed")
    }

    val body = exchange.bodyString()
    val key = jsonString(body, "configKey")?.trim()?.uppercase(Locale.ROOT)
    if (key == null || !cloudConfigKeyRegex.matches(key)) {
        return json(exchange, 200, cloudError("CONFIG_KEY_FORMAT"))
    }

    val record = storage.findActive(key)
        ?: return json(exchange, 200, cloudError("CONFIG_NOT_FOUND"))

    json(
        exchange,
        200,
        """{"ok":true,"configKey":"${record.configKey}","name":"${jsonEscape(record.name)}","payloadBase64":"${jsonEscape(record.payloadBase64)}","updatedAt":"${record.updatedAt}"}""",
    )
}

private fun deleteCloudConfig(exchange: HttpExchange, storage: CloudConfigStorage) {
    if (exchange.requestMethod != "POST") {
        return text(exchange, 405, "Method not allowed")
    }

    val body = exchange.bodyString()
    val key = jsonString(body, "configKey")?.trim()?.uppercase(Locale.ROOT)
    val hwidHash = jsonString(body, "hwidHash")?.trim()?.uppercase(Locale.ROOT)
    if (key == null || !cloudConfigKeyRegex.matches(key)) {
        return json(exchange, 200, cloudError("CONFIG_KEY_FORMAT"))
    }
    if (hwidHash == null || !hwidHashRegex.matches(hwidHash)) {
        return json(exchange, 200, cloudError("HWID_FORMAT"))
    }

    if (!storage.deleteOwned(key, hwidHash)) {
        return json(exchange, 200, cloudError("CONFIG_NOT_FOUND"))
    }

    json(exchange, 200, """{"ok":true,"status":"DELETED"}""")
}

private fun cloudError(status: String): String {
    return """{"ok":false,"status":"${jsonEscape(status)}"}"""
}

private fun LicenseCheckResult.toJson(): String {
    return when (this) {
        is LicenseCheckResult.Valid -> {
            """{"valid":true,"status":"$status","role":"$role","expiresAt":${jsonNullable(expiresAt)}}"""
        }
        is LicenseCheckResult.Invalid -> invalidJson(reason)
    }
}

private fun invalidJson(reason: String): String {
    return """{"valid":false,"status":"${jsonEscape(reason)}"}"""
}

private fun parseExpiresArgument(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank() || raw.equals("never", ignoreCase = true)) {
        return null
    }
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
        repeat(32) {
            append(alphabet[random.nextInt(alphabet.length)])
        }
    }
}

private fun generateCloudConfigKey(existing: Set<String>): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    repeat(100) {
        val key = buildString(8) {
            repeat(8) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
        if (key !in existing) {
            return key
        }
    }
    throw IllegalStateException("Could not generate unique cloud config key")
}

private fun splitArgs(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (char in line) {
        when {
            char == '"' -> quoted = !quoted
            char.isWhitespace() && !quoted -> {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            }
            else -> current.append(char)
        }
    }
    if (current.isNotEmpty()) {
        result += current.toString()
    }
    return result
}

private fun HttpExchange.bodyString(): String {
    return requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

private fun jsonString(json: String, name: String): String? {
    val pattern = Regex(""""${Regex.escape(name)}"\s*:\s*"([^"]*)"""")
    return pattern.find(json)?.groupValues?.getOrNull(1)
}

private fun text(exchange: HttpExchange, code: Int, body: String) {
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    write(exchange, code, body)
}

private fun json(exchange: HttpExchange, code: Int, body: String) {
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    write(exchange, code, body)
}

private fun write(exchange: HttpExchange, code: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun jsonNullable(value: String?): String {
    return value?.let { """"${jsonEscape(it)}"""" } ?: "null"
}

private fun jsonEscape(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

private fun env(name: String): String? {
    return System.getenv(name)?.takeIf { it.isNotBlank() }
}
