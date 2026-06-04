package dev.hypnosia.media

import dev.hypnosia.hud.WatermarkHud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

object GlobalMediaTracker {

    private var lastTitle: String = ""
    private var lastUpdateTime: Long = 0
    private var consecutiveFailures = 0
    private const val MAX_FAILURES = 5
    private var timer: Timer? = null
    private var wasLeftDown = false

    fun start() {
        if (timer != null) return

        if (!MediaBridge.isAvailable) {
            println("[Hypnosia] MediaBridge not available: native library missing")
            return
        }

        timer = Timer("GlobalMedia", true).apply {
            scheduleAtFixedRate(0, 5000) {
                pollMedia()
            }
        }
        println("[Hypnosia] GlobalMediaTracker started")

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            handleClicks(client)
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }

    private fun pollMedia() {
        try {
            val media = MediaBridge.readCurrentMedia()

            if (media != null && media.title.isNotBlank()) {
                consecutiveFailures = 0

                // Обновляем UI в render thread
                MinecraftClient.getInstance().execute {
                    WatermarkHud.trackTitle = media.title
                    WatermarkHud.trackArtist = media.artist
                    WatermarkHud.trackDurationMs = media.durationMs
                    WatermarkHud.trackPositionMs = media.positionMs
                    WatermarkHud.lastProgressUpdate = System.currentTimeMillis()
                    WatermarkHud.isMediaPlaying = media.isPlaying
                    lastUpdateTime = System.currentTimeMillis()

                    // Обложка
                    media.thumbnailPath?.let { path ->
                        loadThumbnail(path)
                    }
                }

                // Лог только при смене трека (не каждые 5 сек)
                if (media.title != lastTitle) {
                    println("[Hypnosia] Now playing: ${media.title} — ${media.artist}")
                    lastTitle = media.title
                }

            } else {
                // Ничего не играет
                MinecraftClient.getInstance().execute {
                    if (WatermarkHud.trackTitle.isNotEmpty()) {
                        WatermarkHud.trackTitle = ""
                        WatermarkHud.trackArtist = ""
                        WatermarkHud.trackPositionMs = 0
                        WatermarkHud.trackDurationMs = 0
                        WatermarkHud.isMediaPlaying = false
                        WatermarkHud.coverTextureId = null
                    }
                }
                lastTitle = ""
            }

        } catch (e: Exception) {
            consecutiveFailures++
            if (consecutiveFailures <= 3) {
                println("[Hypnosia] Media poll failed ($consecutiveFailures/$MAX_FAILURES)")
            }
            if (consecutiveFailures >= MAX_FAILURES) {
                println("[Hypnosia] Media tracker stopped after $MAX_FAILURES failures")
                stop()
            }
        }
    }

    private fun loadThumbnail(path: String) {
        try {
            val file = File(path)
            println("[Hypnosia] Loading thumbnail from: ${file.absolutePath}, exists=${file.exists()}, size=${file.length()}")
            if (!file.exists() || file.length() == 0L) {
                println("[Hypnosia] Thumbnail file not found or empty")
                return
            }

            val image = net.minecraft.client.texture.NativeImage.read(file.inputStream())
            println("[Hypnosia] Thumbnail loaded: ${image.width}x${image.height}")
            val texture = NativeImageBackedTexture({ "Hypnosia media thumbnail" }, image)
            texture.upload()
            val id = Identifier.of("hypnosia", "media_cover")
            MinecraftClient.getInstance().textureManager.registerTexture(id, texture)
            WatermarkHud.coverTextureId = id
            println("[Hypnosia] Thumbnail texture registered: $id")
        } catch (e: Exception) {
            println("[Hypnosia] Thumbnail load failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleClicks(client: MinecraftClient) {
        if (client.currentScreen == null) return

        val window = client.window
        val fixedScale = 1.0f / window.scaleFactor.toFloat().coerceAtLeast(1.0f)
        val mouseGuiX = (client.mouse.x * window.scaledWidth / window.width).toFloat()
        val mouseGuiY = (client.mouse.y * window.scaledHeight / window.height).toFloat()
        val mouseX = mouseGuiX / fixedScale
        val mouseY = mouseGuiY / fixedScale

        val isLeftDown = GLFW.glfwGetMouseButton(window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val clicked = !wasLeftDown && isLeftDown
        wasLeftDown = isLeftDown

        if (clicked && WatermarkHud.handleV1PlayerClick(mouseX, mouseY)) {
            // Click handled
        }
    }
}
