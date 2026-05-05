package dev.hypnosia.license

import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class AccountConfig(
    val accountKey: String?,
    val accountId: Int?,
) {
    companion object {
        private const val CONFIG_FILE_NAME = "account.properties"
        private val accountKeyRegex = Regex("^[A-Za-z0-9]{32}$")

        fun loadOrCreate(): AccountConfig {
            val configFile = HypnosiaPaths.rootFile(CONFIG_FILE_NAME)

            if (!configFile.exists()) {
                configFile.parent.createDirectories()
                val defaults = Properties()
                defaults["account.key"] = ""
                defaults["account.id"] = ""
                configFile.outputStream().use { output ->
                    defaults.store(output, "Hypnosia account config. This file stores only your public account key.")
                }
                return AccountConfig(accountKey = null, accountId = null)
            }

            val properties = Properties()
            configFile.inputStream().use(properties::load)
            val key = properties.getProperty("account.key")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { accountKeyRegex.matches(it) }
                ?.uppercase()
            val id = properties.getProperty("account.id")
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > 0 }

            return AccountConfig(accountKey = key, accountId = id)
        }

        fun save(accountKey: String, accountId: Int) {
            require(accountKeyRegex.matches(accountKey)) { "Invalid account key" }
            require(accountId > 0) { "Invalid account id" }

            val configFile = HypnosiaPaths.rootFile(CONFIG_FILE_NAME)
            configFile.parent.createDirectories()

            val properties = Properties()
            properties["account.key"] = accountKey.uppercase()
            properties["account.id"] = accountId.toString()
            configFile.outputStream().use { output ->
                properties.store(output, "Hypnosia account config. This file stores only your public account key.")
            }
        }
    }
}
