package dev.hypnosia.other

import dev.hypnosia.config.HypnosiaClientSettings
import dev.hypnosia.license.HypnosiaPaths
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.exists

object FriendsManager {
    private const val MODULE_KEY = "module.other.friends.enabled"
    private const val FILE_NAME = "friends.txt"

    private val lock = Any()
    private var loaded = false
    private var enabledLoaded = false
    private var cachedEnabled = true
    private var friends = emptySet<String>()

    fun isEnabled(): Boolean {
        if (!enabledLoaded) {
            cachedEnabled = HypnosiaClientSettings.boolean(MODULE_KEY, true)
            enabledLoaded = true
        }
        return cachedEnabled
    }

    fun setEnabled(enabled: Boolean) {
        cachedEnabled = enabled
        enabledLoaded = true
        HypnosiaClientSettings.set(MODULE_KEY, enabled.toString())
    }

    fun decorateTabName(entry: PlayerListEntry, original: Text): Text {
        if (!isEnabled()) return original
        if (!isFriend(entry.profile.name)) return original

        return Text.literal("★ ")
            .formatted(Formatting.AQUA)
            .append(original.copy().formatted(Formatting.WHITE))
    }

    fun reload() {
        synchronized(lock) {
            loaded = false
            enabledLoaded = false
            friends = emptySet()
        }
    }

    private fun isFriend(name: String): Boolean {
        synchronized(lock) {
            ensureLoadedLocked()
            return friends.contains(name.lowercase())
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true

        val file = HypnosiaPaths.rootFile(FILE_NAME)
        friends = if (file.exists()) {
            runCatching {
                Files.readAllLines(file, StandardCharsets.UTF_8)
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { it.lowercase() }
                    .toSet()
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
    }
}
