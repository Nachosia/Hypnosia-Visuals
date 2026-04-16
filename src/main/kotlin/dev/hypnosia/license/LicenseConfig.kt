package dev.hypnosia.license

import net.fabricmc.loader.api.FabricLoader
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class LicenseConfig(
    val licenseKey: String?,
) {
    companion object {
        private const val CONFIG_FILE_NAME = "license.properties"
        private val licenseRegex = Regex("^[A-Za-z0-9]{32}$")

        fun loadOrCreate(): LicenseConfig {
            val configDir = FabricLoader.getInstance().configDir.resolve("hypnosia")
            val configFile = configDir.resolve(CONFIG_FILE_NAME)

            if (!configFile.exists()) {
                configDir.createDirectories()
                val defaults = Properties()
                defaults["license.key"] = ""
                configFile.outputStream().use { output ->
                    defaults.store(
                        output,
                        "Hypnosia license config. Put only your 32-character license key here. Server settings are not stored on the client.",
                    )
                }
                return LicenseConfig(licenseKey = null)
            }

            val properties = Properties()
            configFile.inputStream().use(properties::load)

            val key = properties.getProperty("license.key")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { licenseRegex.matches(it) }
                ?.uppercase()

            return LicenseConfig(
                licenseKey = key,
            )
        }
    }
}
