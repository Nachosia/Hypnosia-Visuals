package dev.hypnosia.hud

import dev.hypnosia.HypnosiaClient
import dev.hypnosia.license.AccountManager
import dev.hypnosia.license.AccountState
import dev.hypnosia.license.LicenseRole
import dev.hypnosia.other.StreamerModeSettings
import dev.hypnosia.ui.animation.FigmaAnimation
import dev.hypnosia.ui.animation.SpringFloat
import dev.hypnosia.ui.layout.Rect
import dev.hypnosia.ui.render.FigmaTextRenderer
import dev.hypnosia.ui.render.HypnosiaRenderUtils
import dev.hypnosia.ui.render.HypnosiaScissor
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.Identifier
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import kotlin.math.max

object WatermarkHud {
    private data class WatermarkSegment(
        val icon: String,
        val text: String,
        val textWidth: Float,
        val textStyle: FigmaTextRenderer.FigmaTextStyle,
        val textY: Float,
    )

    private data class WatermarkMetrics(
        val fps: Int = 0,
        val ping: Int = 0,
        val ram: Int = 0,
        val cpu: Int = 0,
    )

    var trackTitle: String = "name track"
    var trackArtist: String = "avtor track"
    var trackProgress: Float = 0.0f
    var trackElapsed: String = "3:27"
    var trackDuration: String = "2:23"

    private val musicExpand = SpringFloat(0.0f, stiffness = 310.0f, damping = 28.0f)
    private val osBean = ManagementFactory.getOperatingSystemMXBean()
    private val cpuLoadMethod = sequenceOf("getCpuLoad", "getSystemCpuLoad", "getProcessCpuLoad")
        .firstNotNullOfOrNull { methodName -> osBean.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 } }
    private var cachedMetrics = WatermarkMetrics()
    private var lastMetricsAtMs = 0L

    private val BG = 0xFF0D0D0D.toInt()
    private val STROKE = 0xFF272727.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val MUTED = 0xFF606060.toInt()
    private val SEPARATOR = 0xFF6D6D6D.toInt()
    private val ART_PLACEHOLDER = 0xFFD9D9D9.toInt()
    private val PROGRESS_TRACK = 0xFFE8DEFD.toInt()

    private val Text12 = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 12.0f, 14.0f, baselineOffset = 1.0f)
    private val Text18 = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 18.0f, 28.0f, baselineOffset = 2.0f)
    private val Text16 = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 16.0f, 32.0f, baselineOffset = 2.0f)
    private val Text20 = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 20.0f, 32.0f, baselineOffset = 2.0f)
    private val Text24 = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 24.0f, 32.0f, baselineOffset = 3.0f)

    private fun iconId(name: String): Identifier =
        Identifier.of(HypnosiaClient.MOD_ID, "textures/gui/icons/$name")

    fun register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.SUBTITLES,
            Identifier.of(HypnosiaClient.MOD_ID, "watermark_hud"),
            ::render,
        )
    }

    private fun render(context: DrawContext, tickCounter: RenderTickCounter) {
        val client = MinecraftClient.getInstance()

        val window = client.window
        val fixedScale = 1.0f / window.scaleFactor.toFloat().coerceAtLeast(1.0f)
        val fixedWidth = window.framebufferWidth.toFloat()
        val dt = FigmaAnimation.frameSeconds(tickCounter)

        val mouseGuiX = (client.mouse.x * window.scaledWidth / window.width).toFloat()
        val mouseGuiY = (client.mouse.y * window.scaledHeight / window.height).toFloat()
        val mouseX = mouseGuiX / fixedScale
        val mouseY = mouseGuiY / fixedScale

        val collapsedX = fixedWidth * 0.5f - V1_MAIN_WIDTH * 0.5f
        val centerX = collapsedX + V1_MAIN_WIDTH * 0.5f
        val currentWidth = lerp(V1_MAIN_WIDTH, V1_HOVER_WIDTH, musicExpand.value)
        val currentHeight = lerp(V1_MAIN_HEIGHT, V1_HOVER_HEIGHT, musicExpand.value)
        val currentX = centerX - currentWidth * 0.5f
        val hover = client.currentScreen != null &&
            mouseX in currentX..(currentX + currentWidth) &&
            mouseY in V1_Y..(V1_Y + currentHeight)
        musicExpand.target = if (hover && isMusicActive()) 1.0f else 0.0f
        val expand = musicExpand.update(dt)

        context.matrices.pushMatrix()
        context.matrices.scale(fixedScale, fixedScale)
        when (WatermarkSettings.version()) {
            WatermarkSettings.Version.V1 -> drawVersion1Watermark(context, client, collapsedX, centerX, expand)
            WatermarkSettings.Version.V2 -> drawVersion2Watermark(context, client, V2_X, V2_Y)
        }
        context.matrices.popMatrix()
    }

    private fun drawVersion1Watermark(
        context: DrawContext,
        client: MinecraftClient,
        collapsedX: Float,
        centerX: Float,
        expand: Float,
    ) {
        if (expand < 0.01f) {
            drawVersion1Main(context, client, collapsedX, V1_Y)
            return
        }

        val width = lerp(V1_MAIN_WIDTH, V1_HOVER_WIDTH, expand)
        val height = lerp(V1_MAIN_HEIGHT, V1_HOVER_HEIGHT, expand)
        val x = centerX - width * 0.5f
        val radius = lerp(200.0f, 30.0f, expand)
        panel(context, x, V1_Y, width, height, radius, 3.0f)

        val clipInsetX = lerp(6.0f, 18.0f, expand)
        val clipInsetY = lerp(3.0f, 8.0f, expand)
        HypnosiaScissor.withLocalRect(
            context,
            Rect(
                x + clipInsetX,
                V1_Y + clipInsetY,
                width - clipInsetX * 2.0f,
                height - clipInsetY * 2.0f,
            ),
        ) {
            val mainAlpha = (1.0f - expand * 1.8f).coerceIn(0.0f, 1.0f)
            val hoverAlpha = ((expand - 0.72f) / 0.28f).coerceIn(0.0f, 1.0f)
            if (mainAlpha > 0.01f) {
                drawVersion1MainContent(context, client, x, V1_Y, mainAlpha)
            }
            if (hoverAlpha > 0.01f) {
                drawVersion1HoverContent(context, x, V1_Y, hoverAlpha)
            }
        }
    }

    private fun drawVersion1Main(context: DrawContext, client: MinecraftClient, x: Float, y: Float) {
        panel(context, x, y, V1_MAIN_WIDTH, V1_MAIN_HEIGHT, 200.0f, 3.0f)
        drawVersion1MainContent(context, client, x, y, 1.0f)
    }

    private fun drawVersion1MainContent(context: DrawContext, client: MinecraftClient, x: Float, y: Float, alpha: Float) {
        val metrics = metrics(client)
        drawText(context, musicTitle(client), x + 12.0f, y + 9.0f, 188.0f, 46.0f, Text24, WHITE, alpha)
        drawText(
            context = context,
            text = metrics.fps.toString(),
            x = x + 199.0f,
            y = y + 9.0f,
            width = 66.0f,
            height = 46.0f,
            style = Text24,
            color = WHITE,
            alpha = alpha,
            align = FigmaTextRenderer.HorizontalAlign.Right,
        )
        drawText(context, "/", x + 265.0f, y + 9.0f, 17.0f, 46.0f, Text24, MUTED, alpha, FigmaTextRenderer.HorizontalAlign.Center)
        drawText(context, metrics.ping.toString(), x + 279.0f, y + 9.0f, 60.0f, 46.0f, Text24, WHITE, alpha)
        drawColoredIcon(context, "black_hole.png", x + 343.0f, y + 14.0f, 32.0f, 32.0f, alpha)
    }

    private fun drawVersion1HoverContent(context: DrawContext, x: Float, y: Float, alpha: Float) {
        HypnosiaRenderUtils.drawFigmaBox(context, x + 11.0f, y + 11.0f, 96.0f, 96.0f, 10.0f, a(ART_PLACEHOLDER, alpha))
        drawText(context, trackTitle, x + 114.0f, y + 14.0f, 241.0f, 29.0f, Text20, WHITE, alpha)
        drawText(context, trackArtist, x + 114.0f, y + 43.0f, 241.0f, 29.0f, Text20, WHITE, alpha)
        drawIcon(context, "previous.png", x + 359.0f, y + 8.0f, 24.0f, 24.0f, a(WHITE, alpha))
        drawIcon(context, "pause.png", x + 383.0f, y + 8.0f, 24.0f, 24.0f, a(WHITE, alpha))
        drawIcon(context, "play.png", x + 407.0f, y + 8.0f, 24.0f, 24.0f, a(WHITE, alpha))
        drawIcon(context, "next.png", x + 431.0f, y + 8.0f, 24.0f, 24.0f, a(WHITE, alpha))
        drawColoredIcon(context, "black_hole.png", x + 454.0f, y + 8.0f, 24.0f, 24.0f, alpha)

        HypnosiaRenderUtils.drawFigmaBox(context, x + 8.0f, y + 132.0f, 404.0f, 8.0f, 4.0f, a(PROGRESS_TRACK, alpha))
        drawText(context, "$trackElapsed/$trackDuration", x + 417.0f, y + 128.0f, 69.0f, 14.0f, Text12, WHITE, alpha)
    }

    private fun drawVersion2Watermark(context: DrawContext, client: MinecraftClient, x: Float, y: Float) {
        var topX = x
        if (WatermarkSettings.isEnabled(WatermarkSettings.Module.VISUAL_ICON)) {
            panel(context, topX, y, 34.0f, 34.0f, 10.0f, 1.0f)
            drawColoredIcon(context, "black_hole.png", topX + 1.0f, y + 1.0f, 32.0f, 32.0f, 1.0f)
            topX += 38.0f
        }

        drawVersion2ClientInfo(context, client, topX, y)
        drawVersion2ServerInfo(context, client, x, y + 38.0f)
    }

    private fun drawVersion2ClientInfo(context: DrawContext, client: MinecraftClient, x: Float, y: Float) {
        val session = (AccountManager.state as? AccountState.Valid)?.session
        val primaryRole = primaryRole(session)
        val role = primaryRole.name
        val nick = StreamerModeSettings.displayName(session?.displayName?.takeIf { it.isNotBlank() } ?: client.session.username)
        val fps = "${metrics(client).fps} fps"

        val segments = buildList {
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.ROLE)) {
                add(
                    WatermarkSegment(
                        icon = roleIconName(primaryRole, session),
                        text = role,
                        textWidth = max(40.0f, textWidth(role, Text18)),
                        textStyle = Text18,
                        textY = 2.0f,
                    ),
                )
            }
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.NICK)) {
                add(
                    WatermarkSegment(
                        icon = "location_user.png",
                        text = nick,
                        textWidth = max(44.0f, textWidth(nick, Text18)),
                        textStyle = Text18,
                        textY = 2.0f,
                    ),
                )
            }
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.FPS)) {
                add(
                    WatermarkSegment(
                        icon = "flash.png",
                        text = fps,
                        textWidth = max(77.0f, textWidth(fps, Text20) + 4.0f),
                        textStyle = Text20,
                        textY = 1.0f,
                    ),
                )
            }
        }

        drawVersion2SegmentBar(context, x, y, segments)
    }

    private fun drawVersion2ServerInfo(context: DrawContext, client: MinecraftClient, x: Float, y: Float) {
        val metrics = metrics(client)
        val segments = buildList {
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.SERVER)) {
                add(
                    WatermarkSegment(
                        icon = "server.png",
                        text = serverLabel(client),
                        textWidth = max(66.0f, textWidth(serverLabel(client), Text16)),
                        textStyle = Text16,
                        textY = 1.0f,
                    ),
                )
            }
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.PING)) {
                val ping = "${metrics.ping} ms"
                add(
                    WatermarkSegment(
                        icon = "satellite.png",
                        text = ping,
                        textWidth = max(70.0f, textWidth(ping, Text20)),
                        textStyle = Text20,
                        textY = 1.0f,
                    ),
                )
            }
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.RAM)) {
                val ram = "${metrics.ram}%"
                add(
                    WatermarkSegment(
                        icon = "ram.png",
                        text = ram,
                        textWidth = max(54.0f, textWidth(ram, Text20)),
                        textStyle = Text20,
                        textY = 1.0f,
                    ),
                )
            }
            if (WatermarkSettings.isEnabled(WatermarkSettings.Module.CPU)) {
                val cpu = "${metrics.cpu}%"
                add(
                    WatermarkSegment(
                        icon = "cpu.png",
                        text = cpu,
                        textWidth = max(64.0f, textWidth(cpu, Text20)),
                        textStyle = Text20,
                        textY = 1.0f,
                    ),
                )
            }
        }

        drawVersion2SegmentBar(context, x, y, segments)
    }

    private fun drawVersion2SegmentBar(
        context: DrawContext,
        x: Float,
        y: Float,
        segments: List<WatermarkSegment>,
    ) {
        if (segments.isEmpty()) return

        val width = calculateVersion2BarWidth(segments)
        panel(context, x, y, width, 34.0f, 10.0f, 1.0f)

        var cursorX = x + 4.0f
        segments.forEachIndexed { index, segment ->
            drawIcon(context, segment.icon, cursorX, y + 4.0f, 24.0f, 24.0f, WHITE)
            drawText(
                context = context,
                text = segment.text,
                x = cursorX + 28.0f,
                y = y + segment.textY,
                width = segment.textWidth,
                height = 32.0f,
                style = segment.textStyle,
                color = WHITE,
                alpha = 1.0f,
                align = FigmaTextRenderer.HorizontalAlign.Center,
            )
            cursorX += 28.0f + segment.textWidth
            if (index != segments.lastIndex) {
                drawDivider(context, cursorX + 3.0f, y)
                cursorX += 13.0f
            }
        }
    }

    private fun calculateVersion2BarWidth(segments: List<WatermarkSegment>): Float {
        var width = 8.0f
        segments.forEachIndexed { index, segment ->
            width += 28.0f + segment.textWidth
            if (index != segments.lastIndex) {
                width += 13.0f
            }
        }
        return width + 8.0f
    }

    private fun primaryRole(session: dev.hypnosia.license.AccountSession?): LicenseRole {
        return session?.roles?.firstOrNull { it.name != "USER" }
            ?: session?.roles?.firstOrNull()
            ?: LicenseRole.USER
    }

    private fun roleIconName(role: LicenseRole, session: dev.hypnosia.license.AccountSession?): String {
        val serverIcon = session?.roleIcons?.get(role.name)?.substringAfterLast('/')?.takeIf { it.endsWith(".png", ignoreCase = true) }
        return serverIcon ?: LicenseRole.iconFor(role)
    }

    private fun musicTitle(client: MinecraftClient): String =
        if (trackTitle.isNotBlank() && trackTitle != "Minecraft") trackTitle else StreamerModeSettings.displayName(client.session.username)

    private fun panel(context: DrawContext, x: Float, y: Float, width: Float, height: Float, radius: Float, stroke: Float) {
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, radius, BG, STROKE, stroke)
    }

    private fun drawIcon(context: DrawContext, name: String, x: Float, y: Float, width: Float, height: Float, color: Int) {
        HypnosiaRenderUtils.drawIconTexture(context, iconId(name), x, y, width, height, color)
    }

    private fun drawColoredIcon(context: DrawContext, name: String, x: Float, y: Float, width: Float, height: Float, alpha: Float) {
        HypnosiaRenderUtils.drawRoundedTexture(context, iconId(name), x, y, width, height, 0.0f, a(WHITE, alpha))
    }

    private fun drawDivider(context: DrawContext, x: Float, y: Float) {
        HypnosiaRenderUtils.drawFigmaBox(
            context = context,
            x = x,
            y = y + 6.0f,
            width = 2.0f,
            height = 22.0f,
            radius = 1.0f,
            bgColor = SEPARATOR,
        )
    }

    private fun drawText(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: FigmaTextRenderer.FigmaTextStyle,
        color: Int = WHITE,
        alpha: Float = 1.0f,
        align: FigmaTextRenderer.HorizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
    ) {
        FigmaTextRenderer.drawInBox(context, text, x, y, width, height, a(color, alpha), style, align, FigmaTextRenderer.VerticalAlign.Center)
    }

    private fun serverLabel(client: MinecraftClient): String {
        return client.currentServerEntry?.address?.take(12) ?: "Single..."
    }

    private fun isMusicActive(): Boolean = trackTitle.isNotBlank() && trackTitle != "Minecraft"

    private fun textWidth(text: String, style: FigmaTextRenderer.FigmaTextStyle): Float =
        FigmaTextRenderer.width(text, style)

    private fun pingMs(client: MinecraftClient): Int =
        client.player?.networkHandler?.getPlayerListEntry(client.player!!.uuid)?.latency ?: 0

    private fun metrics(client: MinecraftClient): WatermarkMetrics {
        val now = System.currentTimeMillis()
        if (now - lastMetricsAtMs >= METRICS_CACHE_MS) {
            cachedMetrics = WatermarkMetrics(
                fps = client.currentFps,
                ping = pingMs(client),
                ram = ramPercent(),
                cpu = cpuPercent(),
            )
            lastMetricsAtMs = now
        }
        return cachedMetrics
    }

    private fun ramPercent(): Int {
        val runtime = Runtime.getRuntime()
        return (((runtime.totalMemory() - runtime.freeMemory()) * 100L) / runtime.maxMemory()).toInt()
    }

    private fun cpuPercent(): Int {
        val load = (osBean as? OperatingSystemMXBean)?.let { bean ->
            runCatching { bean.cpuLoad.takeIf { it >= 0.0 } ?: bean.processCpuLoad.takeIf { it >= 0.0 } }.getOrNull()
        } ?: runCatching { (cpuLoadMethod?.invoke(osBean) as? Number)?.toDouble() }.getOrNull()
            ?.takeIf { it >= 0.0 }
            ?: return 0
        return (load.coerceIn(0.0, 1.0) * 100.0).toInt()
    }

    private fun lerp(from: Float, to: Float, amount: Float): Float =
        from + (to - from) * amount.coerceIn(0.0f, 1.0f)

    private fun a(color: Int, alpha: Float): Int {
        val channel = (((color ushr 24) and 0xFF) * alpha.coerceIn(0.0f, 1.0f)).toInt()
        return (color and 0x00FFFFFF) or (channel shl 24)
    }

    private const val V1_Y = 11.0f
    private const val V1_MAIN_WIDTH = 400.0f
    private const val V1_MAIN_HEIGHT = 66.0f
    private const val V1_HOVER_WIDTH = 500.0f
    private const val V1_HOVER_HEIGHT = 164.0f

    private const val V2_X = 20.0f
    private const val V2_Y = 19.0f
    private const val METRICS_CACHE_MS = 200L
}
