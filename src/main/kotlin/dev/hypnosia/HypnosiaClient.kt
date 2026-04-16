package dev.hypnosia

import dev.hypnosia.render.HypnosiaShaders
import dev.hypnosia.license.LicenseManager
import dev.hypnosia.ui.HypnosiaMenuScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

object HypnosiaClient : ClientModInitializer {
    const val MOD_ID: String = "hypnosia"

    private lateinit var openMenuKey: KeyBinding

    override fun onInitializeClient() {
        HypnosiaShaders.initialize()
        LicenseManager.startSessionAsync()

        openMenuKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.hypnosia.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "hypnosia")),
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openMenuKey.wasPressed()) {
                client.setScreen(HypnosiaMenuScreen())
            }
        }
    }
}
