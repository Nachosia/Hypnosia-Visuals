package dev.hypnosia.hud

import dev.hypnosia.license.HypnosiaPaths
import java.nio.file.Files
import java.util.Properties

object WatermarkSettings {
    enum class Version {
        V1,
        V2,
    }

    enum class Module(val key: String, val label: String) {
        VISUAL_ICON("visual_icon", "Visuals"),
        ROLE("role", "Role"),
        NICK("nick", "Nick"),
        FPS("fps", "FPS"),
        SERVER("server", "Server"),
        PING("ping", "Ping"),
        RAM("ram", "RAM"),
        CPU("cpu", "CPU"),
    }

    private const val FILE_NAME = "watermark.properties"
    private const val VERSION_KEY = "version"
    private val values = linkedMapOf<Module, Boolean>().apply {
        Module.entries.forEach { put(it, true) }
    }
    private var version = Version.V2
    private var loaded = false

    fun version(): Version {
        ensureLoaded()
        return version
    }

    fun setVersion(value: Version) {
        ensureLoaded()
        version = value
        save()
    }

    fun toggleVersion() {
        setVersion(if (version() == Version.V1) Version.V2 else Version.V1)
    }

    fun isEnabled(module: Module): Boolean {
        ensureLoaded()
        return values[module] == true
    }

    fun setEnabled(module: Module, enabled: Boolean) {
        ensureLoaded()
        values[module] = enabled
        save()
    }

    fun toggle(module: Module) {
        setEnabled(module, !isEnabled(module))
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val file = HypnosiaPaths.rootFile(FILE_NAME)
            if (!Files.exists(file)) {
                save()
                return@runCatching
            }
            val props = Properties()
            Files.newInputStream(file).use(props::load)
            version = runCatching { Version.valueOf(props.getProperty(VERSION_KEY, Version.V2.name)) }.getOrDefault(Version.V2)
            Module.entries.forEach { module ->
                values[module] = props.getProperty(module.key, "true").toBooleanStrictOrNull() ?: true
            }
        }
    }

    private fun save() {
        runCatching {
            val file = HypnosiaPaths.rootFile(FILE_NAME)
            val props = Properties()
            props[VERSION_KEY] = version.name
            Module.entries.forEach { module ->
                props[module.key] = (values[module] == true).toString()
            }
            Files.newOutputStream(file).use { props.store(it, "Hypnosia watermark settings") }
        }
    }
}
