package dev.hypnosia.license

import dev.hypnosia.HypnosiaClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object AccountManager {
    // Public license/account API. Admin panel is still localhost-only on the VPS.
    private const val DEFAULT_API_URL = "https://api.nachosia.site"
    private val licenseRegex = Regex("^[A-Za-z0-9]{32}$")
    private val cloudConfigKeyRegex = Regex("^[A-Za-z0-9]{8}$")
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build()

    private val stateRef = AtomicReference<AccountState>(AccountState.NotChecked)
    private val sessionFutureRef = AtomicReference<CompletableFuture<AccountState>?>(null)
    private val notificationPollRef = AtomicReference<CompletableFuture<List<String>>?>(null)
    private var lastNotificationPollMs: Long = 0L

    val state: AccountState
        get() = stateRef.get()

    val sessionRoles: List<LicenseRole>
        get() = (state as? AccountState.Valid)?.session?.roles ?: listOf(LicenseRole.USER)

    fun hasAccountKey(): Boolean {
        return AccountConfig.loadOrCreate().accountKey != null
    }

    fun startSessionAsync(): CompletableFuture<AccountState> {
        sessionFutureRef.get()?.let { return it }

        val accountConfig = AccountConfig.loadOrCreate()
        val legacyLicense = LicenseConfig.loadOrCreate().licenseKey
        val future = when {
            accountConfig.accountKey != null -> infoAsync(accountConfig.accountKey)
            legacyLicense != null -> createAsync().thenCompose { created ->
                if (created is AccountState.Valid) {
                    applyRoleKeyAsync(legacyLicense)
                } else {
                    CompletableFuture.completedFuture(created)
                }
            }
            else -> completed(AccountState.NoAccount)
        }

        return if (sessionFutureRef.compareAndSet(null, future)) {
            future
        } else {
            sessionFutureRef.get() ?: future
        }
    }

    fun refreshSessionAsync(): CompletableFuture<AccountState> {
        val accountKey = AccountConfig.loadOrCreate().accountKey
            ?: return completed(AccountState.NoAccount)
        return infoAsync(accountKey)
    }

    fun createAsync(): CompletableFuture<AccountState> {
        return postAccount("/api/account/create", extraFields = emptyMap())
    }

    fun markOnlineAsync(displayName: String?): CompletableFuture<Boolean> {
        val session = state as? AccountState.Valid ?: return CompletableFuture.completedFuture(false)
        return postJson(
            "/api/session/online",
            mapOf(
                "accountKey" to session.session.accountKey,
                "hwidHash" to HardwareFingerprint.currentHash64(),
                "displayName" to displayName.orEmpty(),
            ),
        ).thenApply { response ->
            response.statusCode() in 200..299 && boolValue(response.body(), "ok") == true
        }.exceptionally { false }
    }

    fun markOfflineAsync(): CompletableFuture<Boolean> {
        val accountKey = AccountConfig.loadOrCreate().accountKey ?: return CompletableFuture.completedFuture(false)
        return postJson(
            "/api/session/offline",
            mapOf(
                "accountKey" to accountKey,
                "hwidHash" to HardwareFingerprint.currentHash64(),
            ),
        ).thenApply { response ->
            response.statusCode() in 200..299 && boolValue(response.body(), "ok") == true
        }.exceptionally { false }
    }

    fun tickNotifications(client: MinecraftClient) {
        val player = client.player ?: return
        state as? AccountState.Valid ?: return

        val now = System.currentTimeMillis()
        if (now - lastNotificationPollMs < 5000L) return
        if (notificationPollRef.get() != null) return

        lastNotificationPollMs = now
        val future = pollNotificationsAsync()
        if (!notificationPollRef.compareAndSet(null, future)) return

        future.whenComplete { messages, _ ->
            notificationPollRef.set(null)
            if (messages.isNullOrEmpty()) return@whenComplete
            client.execute {
                messages.forEach { message ->
                    player.sendMessage(Text.literal("Nachosia $message"), false)
                }
            }
        }
    }

    fun setNameAsync(displayName: String): CompletableFuture<AccountState> {
        val current = (state as? AccountState.Valid)?.session
            ?: return CompletableFuture.completedFuture(AccountState.NoAccount)
        return postAccount(
            path = "/api/account/set-name",
            accountKey = current.accountKey,
            extraFields = mapOf("displayName" to displayName.take(32)),
        )
    }

    fun setContactAsync(contact: String): CompletableFuture<AccountState> {
        val current = (state as? AccountState.Valid)?.session
            ?: return CompletableFuture.completedFuture(AccountState.NoAccount)
        return postAccount(
            path = "/api/account/set-contact",
            accountKey = current.accountKey,
            extraFields = mapOf("contact" to contact.take(96)),
        )
    }

    fun applyRoleKeyAsync(licenseKey: String): CompletableFuture<AccountState> {
        val normalized = licenseKey.trim().uppercase()
        if (!licenseRegex.matches(normalized)) {
            return CompletableFuture.completedFuture(AccountState.ServerRejected("LICENSE_FORMAT"))
        }

        val session = state as? AccountState.Valid
        val base = if (session == null) createAsync() else CompletableFuture.completedFuture(session)
        return base.thenCompose { created ->
            val current = (created as? AccountState.Valid)?.session
                ?: return@thenCompose CompletableFuture.completedFuture(created)
            postAccount(
                path = "/api/account/apply-key",
                accountKey = current.accountKey,
                extraFields = mapOf("licenseKey" to normalized),
            ).thenApply { result ->
                if (result is AccountState.Valid) {
                    LicenseConfig.saveLicenseKey(normalized)
                }
                result
            }
        }
    }

    fun saveCloudConfigAsync(name: String): CompletableFuture<CloudSaveResult> {
        val source = resolveLocalConfigFile(name)
        if (!source.exists()) {
            return CompletableFuture.completedFuture(CloudSaveResult.Error("LOCAL_CONFIG_NOT_FOUND: ${source.name}"))
        }

        val payload = Base64.getEncoder().encodeToString(source.readBytes())
        val session = state as? AccountState.Valid
        val base = if (session == null) createAsync() else CompletableFuture.completedFuture(session)
        return base.thenCompose { created ->
            val current = (created as? AccountState.Valid)?.session
            val fields = linkedMapOf(
                "hwidHash" to HardwareFingerprint.currentHash64(),
                "name" to source.name.removeSuffix(".json"),
                "payloadBase64" to payload,
            )
            if (current != null) {
                fields["accountKey"] = current.accountKey
            }
            LicenseConfig.loadOrCreate().licenseKey?.let { fields["license"] = it }

            postJson("/api/cloud-config/save", fields)
                .thenApply { response ->
                    if (response.statusCode() !in 200..299) {
                        return@thenApply CloudSaveResult.Error("HTTP_${response.statusCode()}")
                    }
                    val body = response.body()
                    if (boolValue(body, "ok") != true) {
                        return@thenApply CloudSaveResult.Error(stringValue(body, "status") ?: "INVALID_RESPONSE")
                    }

                    val accountKey = stringValue(body, "accountKey")
                    val accountId = intValue(body, "accountId")
                    if (accountKey != null && accountId != null) {
                        AccountConfig.save(accountKey, accountId)
                    }

                    CloudSaveResult.Saved(
                        configKey = stringValue(body, "configKey") ?: return@thenApply CloudSaveResult.Error("CONFIG_KEY_MISSING"),
                        used = intValue(body, "used") ?: 0,
                        limit = intValue(body, "limit") ?: 3,
                    )
                }
        }.exceptionally { CloudSaveResult.Error(it.message ?: "NETWORK_ERROR") }
    }

    fun loadCloudConfigAsync(configKey: String, outputName: String?): CompletableFuture<CloudLoadResult> {
        val normalized = configKey.trim().uppercase()
        if (!cloudConfigKeyRegex.matches(normalized)) {
            return CompletableFuture.completedFuture(CloudLoadResult.Error("CONFIG_KEY_FORMAT"))
        }

        val fields = linkedMapOf("configKey" to normalized)
        (state as? AccountState.Valid)?.session?.let { session ->
            fields["accountKey"] = session.accountKey
            fields["hwidHash"] = HardwareFingerprint.currentHash64()
        }

        return postJson("/api/cloud-config/load", fields)
            .thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    return@thenApply CloudLoadResult.Error("HTTP_${response.statusCode()}")
                }
                val body = response.body()
                if (boolValue(body, "ok") != true) {
                    return@thenApply CloudLoadResult.Error(stringValue(body, "status") ?: "INVALID_RESPONSE")
                }
                val payload = stringValue(body, "payloadBase64") ?: return@thenApply CloudLoadResult.Error("PAYLOAD_MISSING")
                val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
                    ?: return@thenApply CloudLoadResult.Error("PAYLOAD_FORMAT")
                val finalName = outputName?.takeIf { it.isNotBlank() } ?: stringValue(body, "name") ?: normalized
                val target = resolveLocalConfigFile(finalName)
                target.parent.createDirectories()
                target.writeBytes(bytes)
                CloudLoadResult.Loaded(target.name)
            }
            .exceptionally { CloudLoadResult.Error(it.message ?: "NETWORK_ERROR") }
    }

    fun listCloudConfigsAsync(): CompletableFuture<CloudListResult> {
        val current = (state as? AccountState.Valid)?.session
            ?: return CompletableFuture.completedFuture(CloudListResult.Error("NO_ACCOUNT"))
        return postJson(
            "/api/cloud-config/list",
            mapOf(
                "accountKey" to current.accountKey,
                "hwidHash" to HardwareFingerprint.currentHash64(),
            ),
        ).thenApply { response ->
            if (response.statusCode() !in 200..299) {
                return@thenApply CloudListResult.Error("HTTP_${response.statusCode()}")
            }
            val body = response.body()
            if (boolValue(body, "ok") != true) {
                return@thenApply CloudListResult.Error(stringValue(body, "status") ?: "INVALID_RESPONSE")
            }
            CloudListResult.Listed(
                used = intValue(body, "used") ?: 0,
                limit = intValue(body, "limit") ?: 3,
                configs = configSummaries(body),
            )
        }.exceptionally { CloudListResult.Error(it.message ?: "NETWORK_ERROR") }
    }

    fun deleteCloudConfigAsync(nameOrKey: String): CompletableFuture<CloudDeleteResult> {
        val current = (state as? AccountState.Valid)?.session
            ?: return CompletableFuture.completedFuture(CloudDeleteResult.Error("NO_ACCOUNT"))
        val raw = nameOrKey.trim()
        val keyFuture = if (cloudConfigKeyRegex.matches(raw.uppercase())) {
            CompletableFuture.completedFuture(raw.uppercase())
        } else {
            listCloudConfigsAsync().thenApply { list ->
                val listed = list as? CloudListResult.Listed ?: return@thenApply null
                listed.configs.firstOrNull {
                    it.name.equals(raw, ignoreCase = true) ||
                        it.name.equals(raw.removeSuffix(".json"), ignoreCase = true)
                }?.configKey
            }
        }

        return keyFuture.thenCompose { key ->
            if (key == null) {
                return@thenCompose CompletableFuture.completedFuture(CloudDeleteResult.Error("CONFIG_NOT_FOUND"))
            }
            postJson(
                "/api/cloud-config/delete",
                mapOf(
                    "accountKey" to current.accountKey,
                    "hwidHash" to HardwareFingerprint.currentHash64(),
                    "configKey" to key,
                ),
            ).thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    return@thenApply CloudDeleteResult.Error("HTTP_${response.statusCode()}")
                }
                val body = response.body()
                if (boolValue(body, "ok") == true) {
                    CloudDeleteResult.Deleted(key)
                } else {
                    CloudDeleteResult.Error(stringValue(body, "status") ?: "INVALID_RESPONSE")
                }
            }
        }.exceptionally { CloudDeleteResult.Error(it.message ?: "NETWORK_ERROR") }
    }

    private fun pollNotificationsAsync(): CompletableFuture<List<String>> {
        val current = (state as? AccountState.Valid)?.session
            ?: return CompletableFuture.completedFuture(emptyList())
        return postJson(
            "/api/notifications/poll",
            mapOf(
                "accountKey" to current.accountKey,
                "hwidHash" to HardwareFingerprint.currentHash64(),
            ),
        ).thenApply { response ->
            if (response.statusCode() !in 200..299) return@thenApply emptyList()
            val body = response.body()
            if (boolValue(body, "ok") != true) return@thenApply emptyList()
            notificationMessages(body)
        }.exceptionally { emptyList() }
    }

    private fun infoAsync(accountKey: String): CompletableFuture<AccountState> {
        return postAccount(
            path = "/api/account/info",
            accountKey = accountKey,
            extraFields = emptyMap(),
        )
    }

    private fun postAccount(
        path: String,
        accountKey: String? = null,
        extraFields: Map<String, String>,
    ): CompletableFuture<AccountState> {
        val fields = linkedMapOf("hwidHash" to HardwareFingerprint.currentHash64())
        if (accountKey != null) fields["accountKey"] = accountKey
        fields.putAll(extraFields)

        return postJson(path, fields)
            .thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    return@thenApply AccountState.ServerRejected("HTTP_${response.statusCode()}")
                }
                parseAccountResponse(response.body())
            }
            .exceptionally { AccountState.NetworkError(it.message ?: "NETWORK_ERROR") }
            .thenApply { result ->
                stateRef.set(result)
                if (result is AccountState.Valid) {
                    AccountConfig.save(result.session.accountKey, result.session.accountId)
                }
                result
            }
    }

    private fun parseAccountResponse(body: String): AccountState {
        if (boolValue(body, "ok") != true) {
            return AccountState.ServerRejected(stringValue(body, "status") ?: "INVALID_RESPONSE")
        }
        val accountId = intValue(body, "accountId") ?: return AccountState.InvalidResponse
        val accountKey = stringValue(body, "accountKey") ?: return AccountState.InvalidResponse
        val roles = stringArray(body, "roles").mapNotNull(LicenseRole::parse).ifEmpty { listOf(LicenseRole.USER) }
        val roleIcons = stringObject(body, "roleIcons")
        return AccountState.Valid(
            AccountSession(
                accountId = accountId,
                accountKey = accountKey,
                createdAt = stringValue(body, "createdAt") ?: "",
                displayName = stringValue(body, "displayName"),
                contact = stringValue(body, "contact"),
                roles = roles,
                roleIcons = roleIcons,
                cloudUsed = intValue(body, "cloudUsed") ?: 0,
                cloudLimit = intValue(body, "cloudLimit") ?: 3,
                status = stringValue(body, "status") ?: "OK",
            ),
        )
    }

    private fun completed(state: AccountState): CompletableFuture<AccountState> {
        stateRef.set(state)
        return CompletableFuture.completedFuture(state)
    }

    private fun postJson(path: String, fields: Map<String, String>): CompletableFuture<HttpResponse<String>> {
        val apiUri = secureApiUri(DEFAULT_API_URL)
            ?: return CompletableFuture.failedFuture(IllegalStateException("INSECURE_ENDPOINT"))
        val request = HttpRequest.newBuilder(apiUri.resolve(path))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonObject(fields)))
            .build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun secureApiUri(apiUrl: String): URI? {
        val uri = runCatching { URI.create(apiUrl.trimEnd('/') + "/") }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val localDev = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (scheme != "https" && !(scheme == "http" && localDev)) return null
        return uri
    }

    private fun resolveLocalConfigFile(name: String): java.nio.file.Path {
        val safeName = name.trim()
            .replace('\\', '/')
            .substringAfterLast('/')
            .ifBlank { "config" }
        val fileName = if (safeName.endsWith(".json", ignoreCase = true)) safeName else "$safeName.json"
        return HypnosiaPaths.configsDir.resolve(fileName)
    }

    private fun configSummaries(body: String): List<CloudConfigSummary> {
        val array = Regex(""""configs"\s*:\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        return Regex("""\{([^{}]*)}""")
            .findAll(array)
            .mapNotNull { match ->
                val item = match.value
                val key = stringValue(item, "configKey") ?: return@mapNotNull null
                CloudConfigSummary(
                    configKey = key,
                    name = stringValue(item, "name") ?: key,
                    disabled = boolValue(item, "disabled") ?: false,
                    updatedAt = stringValue(item, "updatedAt") ?: "",
                )
            }
            .toList()
    }

    private fun notificationMessages(body: String): List<String> {
        val array = Regex(""""notifications"\s*:\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        return Regex("""\{([^{}]*)}""")
            .findAll(array)
            .mapNotNull { match -> stringValue(match.value, "message") }
            .toList()
    }

    private fun jsonObject(fields: Map<String, String>): String {
        return fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            """"${escapeJson(key)}":"${escapeJson(value)}""""
        }
    }

    private fun boolValue(json: String, name: String): Boolean? {
        return Regex(""""${Regex.escape(name)}"\s*:\s*(true|false)""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBooleanStrictOrNull()
    }

    private fun intValue(json: String, name: String): Int? {
        return Regex(""""${Regex.escape(name)}"\s*:\s*(\d+)""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun stringValue(json: String, name: String): String? {
        val nullPattern = Regex(""""${Regex.escape(name)}"\s*:\s*null""")
        if (nullPattern.containsMatchIn(json)) return null
        return Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.unescapeJson()
    }

    private fun stringArray(json: String, name: String): List<String> {
        val body = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(body)
            .map { it.groupValues[1].unescapeJson() }
            .toList()
    }

    private fun stringObject(json: String, name: String): Map<String, String> {
        val body = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL)
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyMap()
        return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .findAll(body)
            .associate { it.groupValues[1].unescapeJson() to it.groupValues[2].unescapeJson() }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun String.unescapeJson(): String {
        return replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}

data class AccountSession(
    val accountId: Int,
    val accountKey: String,
    val createdAt: String,
    val displayName: String?,
    val contact: String?,
    val roles: List<LicenseRole>,
    val roleIcons: Map<String, String>,
    val cloudUsed: Int,
    val cloudLimit: Int,
    val status: String,
)

data class CloudConfigSummary(
    val configKey: String,
    val name: String,
    val disabled: Boolean,
    val updatedAt: String,
)

sealed class AccountState {
    data object NotChecked : AccountState()
    data object NoAccount : AccountState()
    data object InvalidResponse : AccountState()
    data class Valid(val session: AccountSession) : AccountState()
    data class ServerRejected(val reason: String) : AccountState()
    data class NetworkError(val message: String) : AccountState()
}

sealed class CloudSaveResult {
    data class Saved(val configKey: String, val used: Int, val limit: Int) : CloudSaveResult()
    data class Error(val reason: String) : CloudSaveResult()
}

sealed class CloudLoadResult {
    data class Loaded(val fileName: String) : CloudLoadResult()
    data class Error(val reason: String) : CloudLoadResult()
}

sealed class CloudListResult {
    data class Listed(val used: Int, val limit: Int, val configs: List<CloudConfigSummary>) : CloudListResult()
    data class Error(val reason: String) : CloudListResult()
}

sealed class CloudDeleteResult {
    data class Deleted(val configKey: String) : CloudDeleteResult()
    data class Error(val reason: String) : CloudDeleteResult()
}
