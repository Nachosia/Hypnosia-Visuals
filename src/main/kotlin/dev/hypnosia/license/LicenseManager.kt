package dev.hypnosia.license

import dev.hypnosia.HypnosiaClient
import net.fabricmc.loader.api.FabricLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

object LicenseManager {
    private const val DEFAULT_API_URL = ""
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build()

    private val stateRef = AtomicReference<LicenseState>(LicenseState.NotChecked)

    val state: LicenseState
        get() = stateRef.get()

    fun refreshAsync(): CompletableFuture<LicenseState> {
        val config = LicenseConfig.loadOrCreate()
        val licenseKey = config.licenseKey

        if (licenseKey == null) {
            return completed(LicenseState.NoKey)
        }

        val apiUrl = config.apiUrlOverride ?: DEFAULT_API_URL.takeIf { it.isNotBlank() }
        if (apiUrl == null) {
            return completed(LicenseState.NoEndpoint)
        }

        val requestBody = licenseRequestJson(
            licenseKey = licenseKey,
            hwid = HardwareFingerprint.currentKey32(),
            hwidHash = HardwareFingerprint.currentHash64(),
            modVersion = modVersion(),
        )

        val request = HttpRequest.newBuilder(URI.create(apiUrl.trimEnd('/') + "/api/license/check"))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> parseResponse(response.statusCode(), response.body()) }
            .exceptionally { LicenseState.NetworkError(it.message ?: "Network error") }
            .thenApply { result ->
                stateRef.set(result)
                result
            }
    }

    private fun completed(state: LicenseState): CompletableFuture<LicenseState> {
        stateRef.set(state)
        return CompletableFuture.completedFuture(state)
    }

    private fun parseResponse(statusCode: Int, body: String): LicenseState {
        if (statusCode !in 200..299) {
            return LicenseState.ServerRejected("HTTP_$statusCode")
        }

        val valid = Regex(""""valid"\s*:\s*(true|false)""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBooleanStrictOrNull()
            ?: return LicenseState.InvalidResponse

        val status = stringValue(body, "status")

        if (!valid) {
            return LicenseState.ServerRejected(status ?: "INVALID")
        }

        return LicenseState.Valid(
            role = stringValue(body, "role") ?: "USER",
            status = status ?: "OK",
        )
    }

    private fun licenseRequestJson(
        licenseKey: String,
        hwid: String,
        hwidHash: String,
        modVersion: String,
    ): String {
        return buildString {
            append('{')
            appendJsonField("license", licenseKey)
            append(',')
            appendJsonField("hwid", hwid)
            append(',')
            appendJsonField("hwidHash", hwidHash)
            append(',')
            appendJsonField("modVersion", modVersion)
            append('}')
        }
    }

    private fun StringBuilder.appendJsonField(name: String, value: String) {
        append('"').append(escapeJson(name)).append('"')
        append(':')
        append('"').append(escapeJson(value)).append('"')
    }

    private fun stringValue(json: String, name: String): String? {
        val pattern = Regex(""""${Regex.escape(name)}"\s*:\s*"([^"]*)"""")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun modVersion(): String {
        return FabricLoader.getInstance()
            .getModContainer(HypnosiaClient.MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
    }
}

sealed class LicenseState {
    data object NotChecked : LicenseState()
    data object NoKey : LicenseState()
    data object NoEndpoint : LicenseState()
    data object InvalidResponse : LicenseState()
    data class Valid(val role: String, val status: String) : LicenseState()
    data class ServerRejected(val reason: String) : LicenseState()
    data class NetworkError(val message: String) : LicenseState()
}
