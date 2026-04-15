package dev.hypnosia.ui

import dev.hypnosia.ui.layout.FigmaRoot
import dev.hypnosia.ui.layout.HypnosiaMainLayout
import dev.hypnosia.ui.layout.UiInputState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class HypnosiaMenuScreen : Screen(Text.literal("Hypnosia")) {
    private val rootLayout: FigmaRoot = HypnosiaMainLayout.create()

    private var figmaMouseX = 0.0f
    private var figmaMouseY = 0.0f
    private var lastFrameNanos = 0L

    override fun shouldPause(): Boolean = false

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Keep the world crisp behind the custom SDF shell: no vanilla blur, panorama, or darkening.
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val client = MinecraftClient.getInstance()
        val now = System.nanoTime()
        val frameSeconds = if (lastFrameNanos == 0L) {
            0.0f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0.0f, 0.05f)
        }
        lastFrameNanos = now

        rootLayout.layout(client)

        val (localMouseX, localMouseY) = rootLayout.toFigmaLocal(
            mouseX = mouseX.toDouble(),
            mouseY = mouseY.toDouble(),
        )
        figmaMouseX = localMouseX
        figmaMouseY = localMouseY
        UiInputState.update(localMouseX, localMouseY, frameSeconds)

        rootLayout.render(context)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        return rootLayout.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        return rootLayout.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) ||
            super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    fun figmaMousePosition(): Pair<Float, Float> = figmaMouseX to figmaMouseY
}
