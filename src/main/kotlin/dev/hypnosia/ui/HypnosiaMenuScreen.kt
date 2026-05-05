package dev.hypnosia.ui

import dev.hypnosia.ui.layout.FigmaRoot
import dev.hypnosia.ui.layout.HypnosiaMainLayout
import dev.hypnosia.ui.layout.UiInputState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text

class HypnosiaMenuScreen : Screen(Text.literal("Hypnosia")) {
    private val rootLayout: FigmaRoot = HypnosiaMainLayout.create()

    private var figmaMouseX = 0.0f
    private var figmaMouseY = 0.0f
    private var lastFrameNanos = 0L
    private var previousHudHidden = false
    private var hudHiddenCaptured = false

    override fun shouldPause(): Boolean = false

    override fun init() {
        super.init()
        val options = MinecraftClient.getInstance().options
        if (!hudHiddenCaptured) {
            previousHudHidden = options.hudHidden
            hudHiddenCaptured = true
        }
        options.hudHidden = true
    }

    override fun removed() {
        restoreHudHidden()
        super.removed()
    }

    override fun close() {
        restoreHudHidden()
        super.close()
    }

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

    override fun mouseReleased(click: Click): Boolean {
        return rootLayout.mouseReleased(click.x(), click.y(), click.button()) || super.mouseReleased(click)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        return rootLayout.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY) ||
            super.mouseDragged(click, offsetX, offsetY)
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

    override fun keyPressed(input: KeyInput): Boolean {
        return rootLayout.keyPressed(input.key, input.scancode, input.modifiers) || super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        val text = input.asString()
        return text.any { rootLayout.charTyped(it, input.modifiers) } || super.charTyped(input)
    }

    fun figmaMousePosition(): Pair<Float, Float> = figmaMouseX to figmaMouseY

    private fun restoreHudHidden() {
        if (hudHiddenCaptured) {
            MinecraftClient.getInstance().options.hudHidden = previousHudHidden
            hudHiddenCaptured = false
        }
    }
}
