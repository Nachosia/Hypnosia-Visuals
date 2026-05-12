package dev.hypnosia.ui

import dev.hypnosia.license.AccountManager
import dev.hypnosia.ui.layout.FigmaRoot
import dev.hypnosia.ui.layout.HypnosiaMainLayout
import dev.hypnosia.ui.layout.UiInputState
import dev.hypnosia.ui.render.FigmaTextRenderer
import dev.hypnosia.ui.render.HypnosiaRenderUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text

class HypnosiaMenuScreen : Screen(Text.literal("Hypnosia")) {
    private val rootLayout: FigmaRoot = HypnosiaMainLayout.create()

    private enum class ServiceGate {
        Checking,
        Available,
        Unavailable,
    }

    private var figmaMouseX = 0.0f
    private var figmaMouseY = 0.0f
    private var lastFrameNanos = 0L
    private var previousHudHidden = false
    private var hudHiddenCaptured = false
    private var serviceGate = ServiceGate.Checking
    private var serviceCheckInFlight = false

    override fun shouldPause(): Boolean = false

    override fun init() {
        super.init()
        val options = MinecraftClient.getInstance().options
        if (!hudHiddenCaptured) {
            previousHudHidden = options.hudHidden
            hudHiddenCaptured = true
        }
        options.hudHidden = true
        startServiceCheck()
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
        // Keep the world untouched here. Transparent/Liquid Glass must blur only the
        // pixels behind themed surfaces, not darken the entire screen before capture.
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        val client = MinecraftClient.getInstance()
        val now = System.nanoTime()
        val frameSeconds = if (lastFrameNanos == 0L) {
            0.0f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0.0f, 0.05f)
        }
        lastFrameNanos = now

        if (serviceGate != ServiceGate.Available) {
            renderServiceGate(context, client)
            return
        }

        rootLayout.layout(client)

        val (localMouseX, localMouseY) = rootLayout.toFigmaLocal(
            mouseX = mouseX.toDouble(),
            mouseY = mouseY.toDouble(),
        )
        figmaMouseX = localMouseX
        figmaMouseY = localMouseY
        UiInputState.update(localMouseX, localMouseY, frameSeconds)

        dev.hypnosia.ui.render.HypnosiaRenderUtils.captureThemeBackdrop(context)
        rootLayout.render(context)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (serviceGate != ServiceGate.Available) {
            val client = MinecraftClient.getInstance()
            val scaledWidth = client.window.scaledWidth.toFloat()
            val scaledHeight = client.window.scaledHeight.toFloat()
            val buttonX = (scaledWidth - 156.0f) * 0.5f
            val buttonY = scaledHeight * 0.5f + 64.0f
            if (click.button() == 0 && contains(click.x().toFloat(), click.y().toFloat(), buttonX, buttonY, 156.0f, 36.0f)) {
                startServiceCheck()
                return true
            }
            return true
        }
        return rootLayout.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (serviceGate != ServiceGate.Available) return true
        return rootLayout.mouseReleased(click.x(), click.y(), click.button()) || super.mouseReleased(click)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        if (serviceGate != ServiceGate.Available) return true
        return rootLayout.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY) ||
            super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (serviceGate != ServiceGate.Available) return true
        return rootLayout.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) ||
            super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (serviceGate != ServiceGate.Available) return super.keyPressed(input)
        return rootLayout.keyPressed(input.key, input.scancode, input.modifiers) || super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (serviceGate != ServiceGate.Available) return true
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

    private fun startServiceCheck() {
        if (serviceCheckInFlight) return
        serviceGate = ServiceGate.Checking
        serviceCheckInFlight = true
        AccountManager.checkServiceAvailableAsync().thenAccept { available ->
            MinecraftClient.getInstance().execute {
                serviceCheckInFlight = false
                serviceGate = if (available) ServiceGate.Available else ServiceGate.Unavailable
            }
        }
    }

    private fun renderServiceGate(context: DrawContext, client: MinecraftClient) {
        val width = client.window.scaledWidth.toFloat()
        val height = client.window.scaledHeight.toFloat()
        val panelWidth = 430.0f
        val panelHeight = 188.0f
        val panelX = (width - panelWidth) * 0.5f
        val panelY = (height - panelHeight) * 0.5f
        val title = if (serviceGate == ServiceGate.Checking) "Checking Hypnosia Cloud" else "No Connection"
        val message = if (serviceGate == ServiceGate.Checking) {
            "Проверяем подключение к системе Hypnosia..."
        } else {
            "В данный момент нет подключения к сети или ведутся технические работы."
        }
        val details = if (serviceGate == ServiceGate.Checking) {
            "Пожалуйста, подождите."
        } else {
            "Повторите попытку позже или нажмите Retry."
        }

        HypnosiaRenderUtils.drawFigmaBox(context, panelX, panelY, panelWidth, panelHeight, 14.0f, 0xEE0D0D0D.toInt(), 0xFF272727.toInt(), 1.0f)
        FigmaTextRenderer.drawCentered(context, title, width * 0.5f, panelY + 28.0f, 20.0f, 0xFFFFFFFF.toInt())
        FigmaTextRenderer.drawCentered(context, message, width * 0.5f, panelY + 73.0f, 13.0f, 0xFFE7E7EA.toInt())
        FigmaTextRenderer.drawCentered(context, details, width * 0.5f, panelY + 96.0f, 12.0f, 0xFF8E8E98.toInt())

        val buttonX = (width - 156.0f) * 0.5f
        val buttonY = height * 0.5f + 64.0f
        val enabled = serviceGate == ServiceGate.Unavailable
        HypnosiaRenderUtils.drawFigmaBox(
            context = context,
            x = buttonX,
            y = buttonY,
            width = 156.0f,
            height = 36.0f,
            radius = 10.0f,
            bgColor = if (enabled) 0xFF151515.toInt() else 0xFF101010.toInt(),
            strokeColor = if (enabled) 0xFFFFFFFF.toInt() else 0xFF343434.toInt(),
            strokeThickness = 1.0f,
        )
        FigmaTextRenderer.drawCentered(
            context = context,
            text = if (serviceGate == ServiceGate.Checking) "Checking..." else "Retry",
            centerX = width * 0.5f,
            y = buttonY + 10.0f,
            size = 13.0f,
            color = if (enabled) 0xFFFFFFFF.toInt() else 0xFF77777D.toInt(),
        )
    }

    private fun contains(mouseX: Float, mouseY: Float, x: Float, y: Float, width: Float, height: Float): Boolean {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height
    }
}
