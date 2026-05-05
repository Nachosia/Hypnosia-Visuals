package dev.hypnosia.hud

import dev.hypnosia.license.HypnosiaPaths
import java.nio.file.Files
import java.util.Properties

object TargetHudSettings {
    enum class Version {
        V1,
        V2,
        V3,
        V4,
        V5,
        V6,
    }

    data class State(
        var enabled: Boolean = true,
        var version: Version = Version.V1,
        var x: Float = 0.5f,
        var y: Float = 0.18f,
        var showEquipmentStrip: Boolean = true,
        var modelSpin: Boolean = false,
        var modelYaw: Float = -35.0f,
        var modelPitch: Float = 12.0f,
        var modelScale: Float = 42.0f,
        var modelOffsetX: Float = 0.0f,
        var modelOffsetY: Float = 0.0f,
    )

    private const val FILE_NAME = "target-hud.properties"
    private val state = State()
    private var loaded = false

    fun state(): State {
        ensureLoaded()
        return state
    }

    fun isEnabled(): Boolean = state().enabled

    fun setEnabled(enabled: Boolean) {
        state().enabled = enabled
        save()
    }

    fun nextVersion() {
        val current = state()
        val entries = Version.entries
        current.version = entries[(current.version.ordinal + 1) % entries.size]
        save()
    }

    fun setX(value: Float) {
        state().x = value
        save()
    }

    fun setY(value: Float) {
        state().y = value
        save()
    }

    fun setPosition(x: Float, y: Float) {
        val current = state()
        current.x = x
        current.y = y
        save()
    }

    fun toggleEquipmentStrip() {
        state().showEquipmentStrip = !state().showEquipmentStrip
        save()
    }

    fun toggleModelSpin() {
        state().modelSpin = !state().modelSpin
        save()
    }

    fun setModelYaw(value: Float) {
        state().modelYaw = value.coerceIn(-180.0f, 180.0f)
        save()
    }

    fun setModelPitch(value: Float) {
        state().modelPitch = value.coerceIn(-90.0f, 90.0f)
        save()
    }

    fun setModelScale(value: Float) {
        state().modelScale = value.coerceIn(20.0f, 80.0f)
        save()
    }

    fun setModelOffsetX(value: Float) {
        state().modelOffsetX = value.coerceIn(-50.0f, 50.0f)
        save()
    }

    fun setModelOffsetY(value: Float) {
        state().modelOffsetY = value.coerceIn(-50.0f, 50.0f)
        save()
    }

    fun yawToSlider(value: Float): Float = ((value.coerceIn(-180.0f, 180.0f) + 180.0f) / 360.0f)

    fun sliderToYaw(value: Float): Float = value.coerceIn(0.0f, 1.0f) * 360.0f - 180.0f

    fun pitchToSlider(value: Float): Float = ((value.coerceIn(-90.0f, 90.0f) + 90.0f) / 180.0f)

    fun sliderToPitch(value: Float): Float = value.coerceIn(0.0f, 1.0f) * 180.0f - 90.0f

    fun scaleToSlider(value: Float): Float = ((value.coerceIn(20.0f, 80.0f) - 20.0f) / 60.0f)

    fun sliderToScale(value: Float): Float = 20.0f + value.coerceIn(0.0f, 1.0f) * 60.0f

    fun offsetToSlider(value: Float): Float = ((value.coerceIn(-50.0f, 50.0f) + 50.0f) / 100.0f)

    fun sliderToOffset(value: Float): Float = value.coerceIn(0.0f, 1.0f) * 100.0f - 50.0f

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
            state.enabled = props.getProperty("enabled", state.enabled.toString())
                .toBooleanStrictOrNull() ?: state.enabled
            state.version = runCatching {
                Version.valueOf(props.getProperty("version", state.version.name))
            }.getOrDefault(state.version)
            state.x = props.getProperty("x", state.x.toString()).toFloatOrNull() ?: state.x
            state.y = props.getProperty("y", state.y.toString()).toFloatOrNull() ?: state.y
            state.showEquipmentStrip = props.getProperty("showEquipmentStrip", state.showEquipmentStrip.toString())
                .toBooleanStrictOrNull() ?: state.showEquipmentStrip
            state.modelSpin = false
            state.modelYaw = props.getProperty("modelYaw", state.modelYaw.toString()).toFloatOrNull() ?: state.modelYaw
            state.modelPitch = props.getProperty("modelPitch", state.modelPitch.toString()).toFloatOrNull() ?: state.modelPitch
            state.modelScale = props.getProperty("modelScale", state.modelScale.toString()).toFloatOrNull() ?: state.modelScale
            state.modelOffsetX = props.getProperty("modelOffsetX", state.modelOffsetX.toString()).toFloatOrNull() ?: state.modelOffsetX
            state.modelOffsetY = props.getProperty("modelOffsetY", state.modelOffsetY.toString()).toFloatOrNull() ?: state.modelOffsetY
        }
    }

    private fun save() {
        runCatching {
            val props = Properties()
            props["enabled"] = state.enabled.toString()
            props["version"] = state.version.name
            props["x"] = state.x.toString()
            props["y"] = state.y.toString()
            props["showEquipmentStrip"] = state.showEquipmentStrip.toString()
            props["modelSpin"] = state.modelSpin.toString()
            props["modelYaw"] = state.modelYaw.toString()
            props["modelPitch"] = state.modelPitch.toString()
            props["modelScale"] = state.modelScale.toString()
            props["modelOffsetX"] = state.modelOffsetX.toString()
            props["modelOffsetY"] = state.modelOffsetY.toString()
            Files.newOutputStream(HypnosiaPaths.rootFile(FILE_NAME)).use {
                props.store(it, "Hypnosia Target HUD settings")
            }
        }
    }
}
