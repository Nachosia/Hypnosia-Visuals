package dev.hypnosia

import dev.hypnosia.render.HypnosiaShaders
import dev.hypnosia.hud.CooldownHud
import dev.hypnosia.hud.HotKeyHud
import dev.hypnosia.hud.HudModulesHud
import dev.hypnosia.hud.InventoryHud
import dev.hypnosia.hud.ModuleHotkeys
import dev.hypnosia.hud.PlayerInfoHud
import dev.hypnosia.hud.PotionsHud
import dev.hypnosia.hud.TargetHud
import dev.hypnosia.hud.WatermarkHud
import dev.hypnosia.license.ActKeyCommand
import dev.hypnosia.license.AccountManager
import dev.hypnosia.ui.HypnosiaMenuScreen
import dev.hypnosia.ui.profile.HypnosiaPlaytime
import dev.hypnosia.ui.render.HighQualityTextRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object HypnosiaClient : ClientModInitializer {
    const val MOD_ID: String = "hypnosia"
    private val logger = LoggerFactory.getLogger(MOD_ID)

    private lateinit var openMenuKey: KeyBinding

    override fun onInitializeClient() {
        HypnosiaShaders.initialize()
        WatermarkHud.register()
        HudModulesHud.register()
        TargetHud.register()
        PlayerInfoHud.register()
        InventoryHud.register()
        CooldownHud.register()
        PotionsHud.register()
        HotKeyHud.register()
        ActKeyCommand.register()
        HypnosiaPlaytime.recordLaunch()
        ClientLifecycleEvents.CLIENT_STARTED.register {
            runCatching { HighQualityTextRenderer.prewarmCommonAtlases() }
                .onSuccess { warmed -> logger.info("Prewarmed {} Hypnosia text atlases.", warmed) }
                .onFailure { error -> logger.warn("Failed to prewarm Hypnosia text atlases.", error) }
        }

        AccountManager.startSessionAsync().thenAccept { state ->
            logger.info("Hypnosia account session state: {}", state)
            if (state is dev.hypnosia.license.AccountState.Valid) {
                AccountManager.markOnlineAsync(net.minecraft.client.MinecraftClient.getInstance().session.username)
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            AccountManager.markOfflineAsync()
        }

        openMenuKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.hypnosia.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "hypnosia")),
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            HypnosiaPlaytime.tick(client)
            AccountManager.tickNotifications(client)
            HudModulesHud.tickDrag(client)
            TargetHud.tickDrag(client)
            PlayerInfoHud.tickDrag(client)
            InventoryHud.tickDrag(client)
            CooldownHud.tickDrag(client)
            PotionsHud.tickDrag(client)
            HotKeyHud.tickDrag(client)
            ModuleHotkeys.tick(client)
            while (openMenuKey.wasPressed()) {
                client.setScreen(HypnosiaMenuScreen())
            }
        }
    }
}
