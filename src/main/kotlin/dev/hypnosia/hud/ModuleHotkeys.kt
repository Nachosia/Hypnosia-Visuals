package dev.hypnosia.hud

import dev.hypnosia.license.HypnosiaPaths
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.util.Properties

object ModuleHotkeys {
    private const val FILE_NAME = "module-hotkeys.properties"
    private val bindings = linkedMapOf<String, Binding>()
    private val pressed = mutableMapOf<Int, Boolean>()
    private val genericEnabled = mutableMapOf<String, Boolean>()
    private var loaded = false

    data class Binding(
        val moduleId: String,
        var title: String,
        var keyCode: Int,
    )

    fun bind(moduleId: String, title: String, keyCode: Int) {
        ensureLoaded()
        bindings[moduleId] = Binding(moduleId, title, keyCode)
        save()
    }

    fun unbind(moduleId: String) {
        ensureLoaded()
        bindings.remove(moduleId)
        save()
    }

    fun keyName(moduleId: String): String {
        ensureLoaded()
        val key = bindings[moduleId]?.keyCode ?: return "Bind"
        return displayKey(key)
    }

    fun activeBindings(): List<Binding> {
        ensureLoaded()
        return bindings.values
            .filter { it.keyCode > 0 }
            .sortedWith(compareBy<Binding> { it.title.lowercase() }.thenBy { it.moduleId })
    }

    fun tick(client: MinecraftClient) {
        if (client.currentScreen != null || client.player == null) {
            pressed.clear()
            return
        }

        ensureLoaded()
        val window = client.window.handle
        activeBindings().forEach { binding ->
            val down = GLFW.glfwGetKey(window, binding.keyCode) == GLFW.GLFW_PRESS
            val wasDown = pressed[binding.keyCode] == true
            if (down && !wasDown) {
                toggle(binding.moduleId)
            }
            pressed[binding.keyCode] = down
        }
    }

    fun toggle(moduleId: String) {
        ensureLoaded()
        when (moduleId) {
            "hud.hotbar" -> toggleHud(HudModuleSettings.Module.HOTBAR)
            "hud.armor" -> toggleHud(HudModuleSettings.Module.ARMOR)
            "hud.player_info" -> toggleHud(HudModuleSettings.Module.PLAYER_INFO)
            "hud.inventory" -> toggleHud(HudModuleSettings.Module.INVENTORY)
            "hud.cooldowns" -> toggleHud(HudModuleSettings.Module.COOLDOWNS)
            "hud.potions" -> toggleHud(HudModuleSettings.Module.POTIONS)
            "hud.hotkeys" -> toggleHud(HudModuleSettings.Module.HOTKEYS)
            "hud.target" -> TargetHudSettings.setEnabled(!TargetHudSettings.isEnabled())
            else -> genericEnabled[moduleId] = !(genericEnabled[moduleId] ?: false)
        }
    }

    fun displayKey(keyCode: Int): String {
        val glfwName = GLFW.glfwGetKeyName(keyCode, 0)
        if (!glfwName.isNullOrBlank()) return glfwName.uppercase()
        return when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> "ESC"
            GLFW.GLFW_KEY_SPACE -> "SPACE"
            GLFW.GLFW_KEY_TAB -> "TAB"
            GLFW.GLFW_KEY_ENTER -> "ENTER"
            GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT"
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT"
            GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL"
            GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL"
            GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT"
            GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT"
            GLFW.GLFW_KEY_UP -> "UP"
            GLFW.GLFW_KEY_DOWN -> "DOWN"
            GLFW.GLFW_KEY_LEFT -> "LEFT"
            GLFW.GLFW_KEY_RIGHT -> "RIGHT"
            GLFW.GLFW_KEY_DELETE -> "DEL"
            GLFW.GLFW_KEY_INSERT -> "INS"
            GLFW.GLFW_KEY_HOME -> "HOME"
            GLFW.GLFW_KEY_END -> "END"
            GLFW.GLFW_KEY_PAGE_UP -> "PGUP"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN"
            in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F25 -> "F${keyCode - GLFW.GLFW_KEY_F1 + 1}"
            else -> "KEY$keyCode"
        }
    }

    private fun toggleHud(module: HudModuleSettings.Module) {
        HudModuleSettings.setEnabled(module, !HudModuleSettings.isEnabled(module))
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val file = HypnosiaPaths.rootFile(FILE_NAME)
            if (!Files.exists(file)) return@runCatching
            val props = Properties()
            Files.newInputStream(file).use(props::load)
            props.stringPropertyNames()
                .filter { it.endsWith(".key") }
                .forEach { keyName ->
                    val moduleId = keyName.removeSuffix(".key")
                    val keyCode = props.getProperty(keyName).toIntOrNull() ?: return@forEach
                    val title = props.getProperty("$moduleId.title", moduleId)
                    bindings[moduleId] = Binding(moduleId, title, keyCode)
                }
        }
    }

    private fun save() {
        runCatching {
            val props = Properties()
            bindings.values.forEach { binding ->
                props["${binding.moduleId}.title"] = binding.title
                props["${binding.moduleId}.key"] = binding.keyCode.toString()
            }
            Files.newOutputStream(HypnosiaPaths.rootFile(FILE_NAME)).use {
                props.store(it, "Hypnosia module hotkeys")
            }
        }
    }
}
