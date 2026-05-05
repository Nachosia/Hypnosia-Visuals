package dev.hypnosia.hud

import dev.hypnosia.license.HypnosiaPaths
import java.nio.file.Files
import java.util.Properties

object HudModuleSettings {
    enum class Module(val key: String) {
        HOTBAR("hotbar"),
        ARMOR("armor"),
        PLAYER_INFO("player_info"),
        INVENTORY("inventory"),
        COOLDOWNS("cooldowns"),
        POTIONS("potions"),
        HOTKEYS("hotkeys"),
    }

    enum class Version {
        V1,
        V2,
        V3,
        V4,
    }

    enum class PlayerInfoMode {
        BPS,
        TPS,
        CORDS,
        ALL,
    }

    enum class PlayerInfoPart {
        BPS,
        TPS,
        CORDS,
    }

    enum class Axis {
        X,
        Y,
    }

    data class State(
        var enabled: Boolean,
        var version: Version,
        var axis: Axis,
        var x: Float,
        var y: Float,
        var slotHighlight: Boolean,
        var playerInfoMode: PlayerInfoMode = PlayerInfoMode.ALL,
        var playerInfoBps: Boolean = true,
        var playerInfoTps: Boolean = true,
        var playerInfoCords: Boolean = true,
        var playerInfoBpsX: Float = 0.02f,
        var playerInfoBpsY: Float = 0.18f,
        var playerInfoTpsX: Float = 0.075f,
        var playerInfoTpsY: Float = 0.18f,
        var playerInfoCordsX: Float = 0.13f,
        var playerInfoCordsY: Float = 0.18f,
    )

    private const val FILE_NAME = "hud-modules.properties"
    private val states = linkedMapOf(
        Module.HOTBAR to State(enabled = true, version = Version.V1, axis = Axis.X, x = 0.5f, y = 0.94f, slotHighlight = false),
        Module.ARMOR to State(enabled = true, version = Version.V1, axis = Axis.X, x = 0.84f, y = 0.86f, slotHighlight = false),
        Module.PLAYER_INFO to State(enabled = false, version = Version.V1, axis = Axis.X, x = 0.02f, y = 0.18f, slotHighlight = false, playerInfoMode = PlayerInfoMode.ALL),
        Module.INVENTORY to State(enabled = false, version = Version.V1, axis = Axis.X, x = 0.02f, y = 0.27f, slotHighlight = false),
        Module.COOLDOWNS to State(enabled = false, version = Version.V1, axis = Axis.X, x = 0.02f, y = 0.39f, slotHighlight = false),
        Module.POTIONS to State(enabled = false, version = Version.V1, axis = Axis.X, x = 0.02f, y = 0.51f, slotHighlight = false),
        Module.HOTKEYS to State(enabled = false, version = Version.V1, axis = Axis.X, x = 0.02f, y = 0.63f, slotHighlight = false),
    )
    private var loaded = false

    fun state(module: Module): State {
        ensureLoaded()
        return states.getValue(module)
    }

    fun isEnabled(module: Module): Boolean = state(module).enabled

    fun setEnabled(module: Module, enabled: Boolean) {
        state(module).enabled = enabled
        save()
    }

    fun toggleVersion(module: Module) {
        toggleVersion(module, maxVersion = Version.V2)
    }

    fun toggleVersion(module: Module, maxVersion: Version) {
        val state = state(module)
        state.version = if (state.version.ordinal >= maxVersion.ordinal) {
            Version.V1
        } else {
            Version.entries[state.version.ordinal + 1]
        }
        save()
    }

    fun togglePlayerInfoMode() {
        val state = state(Module.PLAYER_INFO)
        state.playerInfoMode = PlayerInfoMode.entries[(state.playerInfoMode.ordinal + 1) % PlayerInfoMode.entries.size]
        save()
    }

    fun togglePlayerInfoPart(part: PlayerInfoPart) {
        val state = state(Module.PLAYER_INFO)
        when (part) {
            PlayerInfoPart.BPS -> state.playerInfoBps = !state.playerInfoBps
            PlayerInfoPart.TPS -> state.playerInfoTps = !state.playerInfoTps
            PlayerInfoPart.CORDS -> state.playerInfoCords = !state.playerInfoCords
        }
        save()
    }

    fun setPlayerInfoPartPosition(part: PlayerInfoPart, x: Float, y: Float) {
        val state = state(Module.PLAYER_INFO)
        when (part) {
            PlayerInfoPart.BPS -> {
                state.playerInfoBpsX = x
                state.playerInfoBpsY = y
            }
            PlayerInfoPart.TPS -> {
                state.playerInfoTpsX = x
                state.playerInfoTpsY = y
            }
            PlayerInfoPart.CORDS -> {
                state.playerInfoCordsX = x
                state.playerInfoCordsY = y
            }
        }
        save()
    }

    fun toggleAxis(module: Module) {
        val state = state(module)
        state.axis = if (state.axis == Axis.X) Axis.Y else Axis.X
        save()
    }

    fun setX(module: Module, value: Float) {
        val state = state(module)
        if (module == Module.PLAYER_INFO) {
            val delta = value - state.x
            state.playerInfoBpsX = (state.playerInfoBpsX + delta).coerceIn(0.0f, 1.0f)
            state.playerInfoTpsX = (state.playerInfoTpsX + delta).coerceIn(0.0f, 1.0f)
            state.playerInfoCordsX = (state.playerInfoCordsX + delta).coerceIn(0.0f, 1.0f)
        }
        state.x = value
        save()
    }

    fun setY(module: Module, value: Float) {
        val state = state(module)
        if (module == Module.PLAYER_INFO) {
            val delta = value - state.y
            state.playerInfoBpsY = (state.playerInfoBpsY + delta).coerceIn(0.0f, 1.0f)
            state.playerInfoTpsY = (state.playerInfoTpsY + delta).coerceIn(0.0f, 1.0f)
            state.playerInfoCordsY = (state.playerInfoCordsY + delta).coerceIn(0.0f, 1.0f)
        }
        state.y = value
        save()
    }

    fun setPosition(module: Module, x: Float, y: Float) {
        val state = state(module)
        state.x = x
        state.y = y
        save()
    }

    fun toggleSlotHighlight(module: Module) {
        val state = state(module)
        state.slotHighlight = !state.slotHighlight
        save()
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
            Module.entries.forEach { module ->
                val state = states.getValue(module)
                val prefix = module.key
                state.enabled = props.getProperty("$prefix.enabled", state.enabled.toString())
                    .toBooleanStrictOrNull() ?: state.enabled
                state.version = runCatching {
                    Version.valueOf(props.getProperty("$prefix.version", state.version.name))
                }.getOrDefault(state.version)
                state.axis = runCatching {
                    Axis.valueOf(props.getProperty("$prefix.axis", state.axis.name))
                }.getOrDefault(state.axis)
                state.x = props.getProperty("$prefix.x", state.x.toString()).toFloatOrNull() ?: state.x
                state.y = props.getProperty("$prefix.y", state.y.toString()).toFloatOrNull() ?: state.y
                state.slotHighlight = props.getProperty("$prefix.slotHighlight", state.slotHighlight.toString())
                    .toBooleanStrictOrNull() ?: state.slotHighlight
                state.playerInfoMode = runCatching {
                    PlayerInfoMode.valueOf(props.getProperty("$prefix.playerInfoMode", state.playerInfoMode.name))
                }.getOrDefault(state.playerInfoMode)
                state.playerInfoBps = props.getProperty("$prefix.playerInfoBps", state.playerInfoBps.toString())
                    .toBooleanStrictOrNull() ?: state.playerInfoBps
                state.playerInfoTps = props.getProperty("$prefix.playerInfoTps", state.playerInfoTps.toString())
                    .toBooleanStrictOrNull() ?: state.playerInfoTps
                state.playerInfoCords = props.getProperty("$prefix.playerInfoCords", state.playerInfoCords.toString())
                    .toBooleanStrictOrNull() ?: state.playerInfoCords
                state.playerInfoBpsX = props.getProperty("$prefix.playerInfoBpsX", state.playerInfoBpsX.toString()).toFloatOrNull() ?: state.playerInfoBpsX
                state.playerInfoBpsY = props.getProperty("$prefix.playerInfoBpsY", state.playerInfoBpsY.toString()).toFloatOrNull() ?: state.playerInfoBpsY
                state.playerInfoTpsX = props.getProperty("$prefix.playerInfoTpsX", state.playerInfoTpsX.toString()).toFloatOrNull() ?: state.playerInfoTpsX
                state.playerInfoTpsY = props.getProperty("$prefix.playerInfoTpsY", state.playerInfoTpsY.toString()).toFloatOrNull() ?: state.playerInfoTpsY
                state.playerInfoCordsX = props.getProperty("$prefix.playerInfoCordsX", state.playerInfoCordsX.toString()).toFloatOrNull() ?: state.playerInfoCordsX
                state.playerInfoCordsY = props.getProperty("$prefix.playerInfoCordsY", state.playerInfoCordsY.toString()).toFloatOrNull() ?: state.playerInfoCordsY
            }
        }
    }

    private fun save() {
        runCatching {
            val props = Properties()
            states.forEach { (module, state) ->
                val prefix = module.key
                props["$prefix.enabled"] = state.enabled.toString()
                props["$prefix.version"] = state.version.name
                props["$prefix.axis"] = state.axis.name
                props["$prefix.x"] = state.x.toString()
                props["$prefix.y"] = state.y.toString()
                props["$prefix.slotHighlight"] = state.slotHighlight.toString()
                props["$prefix.playerInfoMode"] = state.playerInfoMode.name
                props["$prefix.playerInfoBps"] = state.playerInfoBps.toString()
                props["$prefix.playerInfoTps"] = state.playerInfoTps.toString()
                props["$prefix.playerInfoCords"] = state.playerInfoCords.toString()
                props["$prefix.playerInfoBpsX"] = state.playerInfoBpsX.toString()
                props["$prefix.playerInfoBpsY"] = state.playerInfoBpsY.toString()
                props["$prefix.playerInfoTpsX"] = state.playerInfoTpsX.toString()
                props["$prefix.playerInfoTpsY"] = state.playerInfoTpsY.toString()
                props["$prefix.playerInfoCordsX"] = state.playerInfoCordsX.toString()
                props["$prefix.playerInfoCordsY"] = state.playerInfoCordsY.toString()
            }
            Files.newOutputStream(HypnosiaPaths.rootFile(FILE_NAME)).use {
                props.store(it, "Hypnosia HUD module settings")
            }
        }
    }
}
