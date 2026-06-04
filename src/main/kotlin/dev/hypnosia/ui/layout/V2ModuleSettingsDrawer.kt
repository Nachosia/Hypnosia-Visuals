package dev.hypnosia.ui.layout

import dev.hypnosia.config.IconSettings
import dev.hypnosia.config.ThemeSettings
import dev.hypnosia.hud.HudModuleSettings
import dev.hypnosia.hud.ModuleHotkeys
import dev.hypnosia.hud.TargetHudSettings
import dev.hypnosia.hud.WatermarkSettings
import dev.hypnosia.other.FriendsManager
import dev.hypnosia.other.StreamerModeSettings
import dev.hypnosia.ui.animation.SpringFloat
import dev.hypnosia.ui.render.FigmaTextRenderer
import dev.hypnosia.ui.render.HypnosiaRenderUtils
import dev.hypnosia.ui.render.HypnosiaScissor
import dev.hypnosia.visual.AspectRatioSettings
import dev.hypnosia.visual.image.ImageRenderConfig
import dev.hypnosia.visual.image.ImageRenderEntry
import dev.hypnosia.visual.image.ImageRenderModule
import dev.hypnosia.world.WorldVisualSettings
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import java.util.Locale

data class V2ModuleEntry(
    val id: String,
    val title: String,
    var enabled: Boolean,
    val hasSettings: Boolean,
)

class V2ModuleSettingsDrawer(
    private val module: () -> V2ModuleEntry?,
    private val close: () -> Unit,
) {
    var bounds: Rect = Rect(0f, 0f, WIDTH, HEIGHT)

    private var activeHudSlider: HudSlider? = null
    private var activeTargetSlider: TargetSliderKind? = null
    private var activeWorldSlider: WorldSliderKind? = null
    private var activeAspectSlider = false
    private var activeImageSlider: ImageSlider? = null
    private var imageNameEditing = false
    private var imageNameInput = ""
    private var pendingImageChromaReload = false
    private var streamerReplacementEditing = false
    private var bindingModuleId: String? = null
    private var bindingModuleTitle: String? = null
    private val contentScroll = SpringFloat(0.0f, stiffness = 320.0f, damping = 38.0f)
    private var maxContentScroll = 0.0f
    private var lastModuleId: String? = null
    private var iconPaletteOpen = false
    private var fogPaletteOpen = false
    private var themePaletteTarget: ThemePaletteTarget? = null
    private var colorPickerTarget: ColorPickerTarget? = null
    private var colorPickerDrag: ColorPickerDrag? = null
    private var imageColorPickerPath: String? = null
    private val recentColors = mutableListOf<Int>()
    private val heightSpring = SpringFloat(0.0f, stiffness = 400.0f, damping = 28.0f)

    fun preRender(): Float {
        val hasModule = module() != null
        heightSpring.target = if (hasModule) HEIGHT else 0.0f
        val currentHeight = heightSpring.update(UiInputState.frameSeconds).coerceIn(0.0f, HEIGHT)
        bounds = Rect(bounds.x, bounds.y, bounds.width, currentHeight)
        return currentHeight
    }

    fun render(context: DrawContext): Float {
        val currentHeight = bounds.height
        if (currentHeight < 1.0f) return 0.0f

        val title = module()?.title ?: "Icons"
        syncScrollState()
        HypnosiaRenderUtils.drawThemedBox(
            context = context,
            x = bounds.x,
            y = bounds.y,
            width = WIDTH,
            height = currentHeight,
            radius = 10.0f,
            bgColor = DRAWER_BG,
            strokeColor = DRAWER_STROKE,
            strokeThickness = 1.0f,
            role = ThemeSettings.ThemeRole.DRAWER,
        )
        drawText(context, "CLIENT SETTINGS", bounds.x + 15.0f, bounds.y + 13.0f, 10.0f, 0xFF8E8E98.toInt())
        drawTextBox(context, title, bounds.x + 15.0f, bounds.y + 26.0f, 100.0f, 24.0f, WHITE, styleFor(18.0f, FigmaTextRenderer.Font.Main), FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)
        module()?.let { current ->
            val bindLabel = if (bindingModuleId == current.id) "..." else ModuleHotkeys.keyName(current.id)
            drawButton(context, bounds.x + 119.0f, bounds.y + 17.0f, 52.0f, 26.0f, bindLabel)
        }
        drawButton(context, bounds.x + 177.0f, bounds.y + 17.0f, 40.0f, 26.0f, "Close")
        rect(context, bounds.x + 15.0f, bounds.y + 59.0f, 204.0f, 1.0f, DRAWER_STROKE)

        val offset = contentScroll.update(UiInputState.frameSeconds).coerceIn(0.0f, maxContentScroll)
        if (offset != contentScroll.value) {
            contentScroll.snap(offset)
        }
        val scissorHeight = (currentHeight - CONTENT_TOP - CONTENT_BOTTOM_PAD).coerceAtLeast(0.0f)
        HypnosiaScissor.withLocalRect(
            context,
            Rect(bounds.x + 1.0f, bounds.y + CONTENT_TOP, WIDTH - 2.0f, scissorHeight),
        ) {
            context.matrices.pushMatrix()
            context.matrices.translate(0.0f, -offset)
            renderScrollableContent(context)
            context.matrices.popMatrix()
        }
        if (maxContentScroll > 0.5f && scissorHeight > 20.0f) {
            renderContentScrollbar(context, offset)
        }
        renderColorPicker(context)
        return currentHeight
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != 0) return false
        if (colorPickerTarget != null && handleColorPickerClick(mouseX, mouseY)) return true
        module()?.let { current ->
            if (contains(mouseX, mouseY, bounds.x + 119.0f, bounds.y + 17.0f, 52.0f, 26.0f)) {
                bindingModuleId = current.id
                bindingModuleTitle = current.title
                return true
            }
        }
        if (contains(mouseX, mouseY, bounds.x + 177.0f, bounds.y + 17.0f, 40.0f, 26.0f)) {
            close()
            return true
        }
        if (!contains(mouseX, mouseY, bounds.x, bounds.y, WIDTH, HEIGHT)) return false
        val contentMouseY = mouseY + contentScroll.value.coerceIn(0.0f, maxContentScroll)
        if (module()?.id == "hud.watermark") {
            val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, versionRect.x, versionRect.y, versionRect.width, versionRect.height)) {
                WatermarkSettings.toggleVersion()
                return true
            }
            if (WatermarkSettings.version() == WatermarkSettings.Version.V1) {
                return contains(mouseX, mouseY, bounds.x, bounds.y, WIDTH, HEIGHT)
            }
            watermarkToggles().forEach { (watermarkModule, rect) ->
                if (contains(mouseX, contentMouseY, rect.x, rect.y, rect.width, rect.height)) {
                    WatermarkSettings.toggle(watermarkModule)
                    return true
                }
            }
        } else if (module()?.id == "hud.hotbar" || module()?.id == "hud.armor") {
            val hudModule = if (module()?.id == "hud.hotbar") HudModuleSettings.Module.HOTBAR else HudModuleSettings.Module.ARMOR
            val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val axisRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 40.0f)
            val xRect = Rect(bounds.x + 11.0f, bounds.y + 175.0f, 258.0f, 48.0f)
            val yRect = Rect(bounds.x + 11.0f, bounds.y + 235.0f, 258.0f, 48.0f)
            val highlightRect = Rect(bounds.x + 11.0f, bounds.y + 295.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, versionRect.x, versionRect.y, versionRect.width, versionRect.height)) {
                HudModuleSettings.toggleVersion(hudModule)
                return true
            }
            if (contains(mouseX, contentMouseY, axisRect.x, axisRect.y, axisRect.width, axisRect.height)) {
                HudModuleSettings.toggleAxis(hudModule)
                return true
            }
            if (contains(mouseX, contentMouseY, xRect.x, xRect.y, xRect.width, xRect.height)) {
                activeHudSlider = HudSlider(hudModule, HudSliderKind.X)
                updateHudSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, yRect.x, yRect.y, yRect.width, yRect.height)) {
                activeHudSlider = HudSlider(hudModule, HudSliderKind.Y)
                updateHudSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, highlightRect.x, highlightRect.y, highlightRect.width, highlightRect.height)) {
                HudModuleSettings.toggleSlotHighlight(hudModule)
                return true
            }
        } else if (module()?.id in hudModuleIds) {
            val hudModule = hudModuleForId(module()?.id) ?: return true
            val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val xRect = Rect(bounds.x + 11.0f, bounds.y + if (hudModule == HudModuleSettings.Module.PLAYER_INFO) 247.0f else 123.0f, 258.0f, 48.0f)
            val yRect = Rect(bounds.x + 11.0f, bounds.y + if (hudModule == HudModuleSettings.Module.PLAYER_INFO) 307.0f else 183.0f, 258.0f, 48.0f)
            if (contains(mouseX, contentMouseY, versionRect.x, versionRect.y, versionRect.width, versionRect.height)) {
                HudModuleSettings.toggleVersion(hudModule, maxVersionFor(hudModule))
                return true
            }
            if (hudModule == HudModuleSettings.Module.PLAYER_INFO) {
                playerInfoToggles().forEach { (part, rect) ->
                    if (contains(mouseX, contentMouseY, rect.x, rect.y, rect.width, rect.height)) {
                        HudModuleSettings.togglePlayerInfoPart(part)
                        return true
                    }
                }
            }
            if (contains(mouseX, contentMouseY, xRect.x, xRect.y, xRect.width, xRect.height)) {
                activeHudSlider = HudSlider(hudModule, HudSliderKind.X)
                updateHudSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, yRect.x, yRect.y, yRect.width, yRect.height)) {
                activeHudSlider = HudSlider(hudModule, HudSliderKind.Y)
                updateHudSlider(mouseX)
                return true
            }
        } else if (module()?.id == "hud.target") {
            val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val xRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 48.0f)
            val yRect = Rect(bounds.x + 11.0f, bounds.y + 183.0f, 258.0f, 48.0f)
            val toggles = targetToggles()
            if (contains(mouseX, contentMouseY, versionRect.x, versionRect.y, versionRect.width, versionRect.height)) {
                TargetHudSettings.nextVersion()
                return true
            }
            if (contains(mouseX, contentMouseY, xRect.x, xRect.y, xRect.width, xRect.height)) {
                activeTargetSlider = TargetSliderKind.X
                updateTargetSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, yRect.x, yRect.y, yRect.width, yRect.height)) {
                activeTargetSlider = TargetSliderKind.Y
                updateTargetSlider(mouseX)
                return true
            }
            if (TargetHudSettings.state().version.ordinal >= TargetHudSettings.Version.V4.ordinal) {
                val modelXRect = Rect(bounds.x + 11.0f, bounds.y + 243.0f, 258.0f, 48.0f)
                val modelYRect = Rect(bounds.x + 11.0f, bounds.y + 303.0f, 258.0f, 48.0f)
                val yawRect = Rect(bounds.x + 11.0f, bounds.y + 363.0f, 258.0f, 48.0f)
                val pitchRect = Rect(bounds.x + 11.0f, bounds.y + 423.0f, 258.0f, 48.0f)
                val scaleRect = Rect(bounds.x + 11.0f, bounds.y + 483.0f, 258.0f, 48.0f)
                if (contains(mouseX, contentMouseY, modelXRect.x, modelXRect.y, modelXRect.width, modelXRect.height)) {
                    activeTargetSlider = TargetSliderKind.MODEL_X
                    updateTargetSlider(mouseX)
                    return true
                }
                if (contains(mouseX, contentMouseY, modelYRect.x, modelYRect.y, modelYRect.width, modelYRect.height)) {
                    activeTargetSlider = TargetSliderKind.MODEL_Y
                    updateTargetSlider(mouseX)
                    return true
                }
                if (contains(mouseX, contentMouseY, yawRect.x, yawRect.y, yawRect.width, yawRect.height)) {
                    activeTargetSlider = TargetSliderKind.MODEL_YAW
                    updateTargetSlider(mouseX)
                    return true
                }
                if (contains(mouseX, contentMouseY, pitchRect.x, pitchRect.y, pitchRect.width, pitchRect.height)) {
                    activeTargetSlider = TargetSliderKind.MODEL_PITCH
                    updateTargetSlider(mouseX)
                    return true
                }
                if (contains(mouseX, contentMouseY, scaleRect.x, scaleRect.y, scaleRect.width, scaleRect.height)) {
                    activeTargetSlider = TargetSliderKind.MODEL_SCALE
                    updateTargetSlider(mouseX)
                    return true
                }
            }
            toggles.forEach { (kind, rect) ->
                if (contains(mouseX, contentMouseY, rect.x, rect.y, rect.width, rect.height)) {
                    when (kind) {
                        TargetToggle.EQUIPMENT -> TargetHudSettings.toggleEquipmentStrip()
                        TargetToggle.MODEL_SPIN -> TargetHudSettings.toggleModelSpin()
                    }
                    return true
                }
            }
        } else if (module()?.id == "client.icons") {
            val blackHoleRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val colorRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, blackHoleRect.x, blackHoleRect.y, blackHoleRect.width, blackHoleRect.height)) {
                IconSettings.toggleBlackHoleVisible()
                return true
            }
            if (contains(mouseX, contentMouseY, colorRect.x, colorRect.y, colorRect.width, colorRect.height)) {
                colorPickerTarget = ColorPickerTarget.ICON
                iconPaletteOpen = false
                return true
            }
            if (iconPaletteOpen) {
                iconPaletteSwatches().forEach { (color, rect) ->
                    if (contains(mouseX, contentMouseY, rect.x, rect.y, rect.width, rect.height)) {
                        IconSettings.setColor(color)
                        recordRecentColor(color)
                        iconPaletteOpen = false
                        return true
                    }
                }
            }
        } else if (module()?.id == "client.images") {
            val files = imageFilesInFolder()
            // Scan folder
            val scanRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, scanRect.x, scanRect.y, scanRect.width, scanRect.height)) {
                ImageRenderConfig.scanFolder(ImageRenderModule.KARTINKI_DIR)
                ImageRenderModule.reload()
                return true
            }
            // Reload
            val reloadRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, reloadRect.x, reloadRect.y, reloadRect.width, reloadRect.height)) {
                ImageRenderModule.reload()
                return true
            }
            // Add by name
            val addNameRect = Rect(bounds.x + 11.0f, bounds.y + 171.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, addNameRect.x, addNameRect.y, addNameRect.width, addNameRect.height)) {
                val relativeX = mouseX - addNameRect.x
                if (relativeX > 180.0f && imageNameInput.isNotEmpty()) {
                    ImageRenderConfig.add(imageNameInput)
                    ImageRenderModule.reload()
                    imageNameInput = ""
                    imageNameEditing = false
                } else {
                    imageNameEditing = true
                }
                return true
            }
            // File rows
            files.forEachIndexed { index, fileName ->
                val rowY = bounds.y + 219.0f + index * 48.0f
                val rowRect = Rect(bounds.x + 11.0f, rowY, 258.0f, 40.0f)
                if (contains(mouseX, contentMouseY, rowRect.x, rowRect.y, rowRect.width, rowRect.height)) {
                    val relativeX = mouseX - rowRect.x
                    if (relativeX > 180.0f) {
                        // Toggle / Add on right side
                        val entry = ImageRenderConfig.entries().find { it.path.equals(fileName, ignoreCase = true) }
                        if (entry != null) {
                            ImageRenderConfig.update(entry.copy(enabled = !entry.enabled))
                        } else {
                            ImageRenderConfig.add(fileName)
                            ImageRenderModule.reload()
                        }
                    } else {
                        // Select / deselect on left side
                        ImageRenderModule.selectedEntryPath = if (ImageRenderModule.selectedEntryPath.equals(fileName, ignoreCase = true)) null else fileName
                    }
                    return true
                }
                // Settings for selected file
                if (fileName.equals(ImageRenderModule.selectedEntryPath, ignoreCase = true)) {
                    val entry = ImageRenderConfig.entries().find { it.path.equals(fileName, ignoreCase = true) }
                    if (entry != null) {
                        val sy = rowY + 48.0f
                        // Scale slider
                        val scaleRect = Rect(bounds.x + 11.0f, sy, 258.0f, 48.0f)
                        if (contains(mouseX, contentMouseY, scaleRect.x, scaleRect.y, scaleRect.width, scaleRect.height)) {
                            activeImageSlider = ImageSlider(fileName, ImageSliderKind.SCALE)
                            updateImageSlider(mouseX)
                            return true
                        }
                        // Chroma key color picker
                        val chromaRect = Rect(bounds.x + 11.0f, sy + 48.0f, 258.0f, 40.0f)
                        if (contains(mouseX, contentMouseY, chromaRect.x, chromaRect.y, chromaRect.width, chromaRect.height)) {
                            imageColorPickerPath = fileName
                            colorPickerTarget = ColorPickerTarget.IMAGE_CHROMA
                            return true
                        }
                        // Remove
                        val removeRect = Rect(bounds.x + 11.0f, sy + 96.0f, 258.0f, 40.0f)
                        if (contains(mouseX, contentMouseY, removeRect.x, removeRect.y, removeRect.width, removeRect.height)) {
                            ImageRenderConfig.remove(fileName)
                            ImageRenderModule.selectedEntryPath = null
                            ImageRenderModule.reload()
                            return true
                        }
                    }
                }
            }
        } else if (module()?.id == "world.custom_fog") {
            val distanceRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 48.0f)
            val strengthRect = Rect(bounds.x + 11.0f, bounds.y + 183.0f, 258.0f, 48.0f)
            val softnessRect = Rect(bounds.x + 11.0f, bounds.y + 243.0f, 258.0f, 48.0f)
            val colorRect = Rect(bounds.x + 11.0f, bounds.y + 303.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, distanceRect.x, distanceRect.y, distanceRect.width, distanceRect.height)) {
                activeWorldSlider = WorldSliderKind.FOG_DISTANCE
                updateWorldSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, strengthRect.x, strengthRect.y, strengthRect.width, strengthRect.height)) {
                activeWorldSlider = WorldSliderKind.FOG_STRENGTH
                updateWorldSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, softnessRect.x, softnessRect.y, softnessRect.width, softnessRect.height)) {
                activeWorldSlider = WorldSliderKind.FOG_SOFTNESS
                updateWorldSlider(mouseX)
                return true
            }
            if (contains(mouseX, contentMouseY, colorRect.x, colorRect.y, colorRect.width, colorRect.height)) {
                colorPickerTarget = ColorPickerTarget.FOG
                fogPaletteOpen = false
                return true
            }
            if (fogPaletteOpen) {
                fogPaletteSwatches().forEach { (color, rect) ->
                    if (contains(mouseX, contentMouseY, rect.x, rect.y, rect.width, rect.height)) {
                        WorldVisualSettings.setFogColor(color)
                        recordRecentColor(color)
                        fogPaletteOpen = false
                        return true
                    }
                }
            }
        } else if (module()?.id == "other.friends") {
            val displayRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, displayRect.x, displayRect.y, displayRect.width, displayRect.height)) {
                val next = !FriendsManager.isEnabled()
                FriendsManager.setEnabled(next)
                module()?.enabled = next
                return true
            }
        } else if (module()?.id == "other.streamer_mode") {
            val levelRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val nameRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 40.0f)
            if (contains(mouseX, contentMouseY, levelRect.x, levelRect.y, levelRect.width, levelRect.height)) {
                StreamerModeSettings.cycleLevel()
                return true
            }
            if (contains(mouseX, contentMouseY, nameRect.x, nameRect.y, nameRect.width, nameRect.height)) {
                streamerReplacementEditing = true
                return true
            }
        } else if (module()?.id == "visuals.aspect_ratio") {
            val modeRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val freeRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 48.0f)
            if (contains(mouseX, contentMouseY, modeRect.x, modeRect.y, modeRect.width, modeRect.height)) {
                AspectRatioSettings.cycleMode()
                return true
            }
            if (contains(mouseX, contentMouseY, freeRect.x, freeRect.y, freeRect.width, freeRect.height)) {
                activeAspectSlider = true
                updateAspectSlider(mouseX)
                return true
            }
        } else if (module()?.id == "client.theme") {
            val modeRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 258.0f, 40.0f)
            val fontRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 40.0f)
            val glassRect = Rect(bounds.x + 11.0f, bounds.y + 171.0f, 258.0f, 40.0f)
            val gradientRect = Rect(bounds.x + 11.0f, bounds.y + 219.0f, 258.0f, 40.0f)
            val baseRect = Rect(bounds.x + 11.0f, bounds.y + 267.0f, 258.0f, 40.0f)
            val startRect = Rect(bounds.x + 11.0f, bounds.y + 315.0f, 258.0f, 40.0f)
            val endRect = Rect(bounds.x + 11.0f, bounds.y + 363.0f, 258.0f, 40.0f)
            when {
                contains(mouseX, contentMouseY, modeRect.x, modeRect.y, modeRect.width, modeRect.height) -> {
                    ThemeSettings.cycleMode()
                    return true
                }
                contains(mouseX, contentMouseY, fontRect.x, fontRect.y, fontRect.width, fontRect.height) -> {
                    ThemeSettings.cycleFont()
                    return true
                }
                contains(mouseX, contentMouseY, glassRect.x, glassRect.y, glassRect.width, glassRect.height) -> {
                    ThemeSettings.toggleLiquidGlass()
                    return true
                }
                contains(mouseX, contentMouseY, gradientRect.x, gradientRect.y, gradientRect.width, gradientRect.height) -> {
                    ThemeSettings.toggleGradient()
                    return true
                }
                contains(mouseX, contentMouseY, baseRect.x, baseRect.y, baseRect.width, baseRect.height) -> {
                    colorPickerTarget = ColorPickerTarget.THEME_BASE
                    themePaletteTarget = null
                    return true
                }
                contains(mouseX, contentMouseY, startRect.x, startRect.y, startRect.width, startRect.height) -> {
                    colorPickerTarget = ColorPickerTarget.THEME_GRADIENT_START
                    themePaletteTarget = null
                    return true
                }
                contains(mouseX, contentMouseY, endRect.x, endRect.y, endRect.width, endRect.height) -> {
                    colorPickerTarget = ColorPickerTarget.THEME_GRADIENT_END
                    themePaletteTarget = null
                    return true
                }
            }
            themePaletteTarget?.let { target ->
                themePaletteSwatches().forEach { (color, rect) ->
                    if (contains(mouseX, contentMouseY, rect.x, rect.y, rect.width, rect.height)) {
                        when (target) {
                            ThemePaletteTarget.BASE -> ThemeSettings.setBaseColor(color)
                            ThemePaletteTarget.GRADIENT_START -> ThemeSettings.setGradientStart(color)
                            ThemePaletteTarget.GRADIENT_END -> ThemeSettings.setGradientEnd(color)
                        }
                        recordRecentColor(color)
                        themePaletteTarget = null
                        return true
                    }
                }
            }
        }
        return contains(mouseX, mouseY, bounds.x, bounds.y, WIDTH, HEIGHT)
    }

    fun mouseScrolled(
        mouseX: Float,
        mouseY: Float,
        horizontalAmount: Float,
        verticalAmount: Float,
    ): Boolean {
        if (!contains(mouseX, mouseY, bounds.x, bounds.y, WIDTH, HEIGHT) || maxContentScroll <= 0.0f) {
            return false
        }
        contentScroll.target = (contentScroll.target - verticalAmount * 44.0f).coerceIn(0.0f, maxContentScroll)
        return true
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, deltaX: Float, deltaY: Float): Boolean {
        if (button != 0) return false
        colorPickerDrag?.let {
            updateColorPickerDrag(mouseX, mouseY, it)
            return true
        }
        if (activeHudSlider != null) {
            updateHudSlider(mouseX)
            return true
        }
        if (activeTargetSlider != null) {
            updateTargetSlider(mouseX)
            return true
        }
        if (activeWorldSlider != null) {
            updateWorldSlider(mouseX)
            return true
        }
        if (activeAspectSlider) {
            updateAspectSlider(mouseX)
            return true
        }
        if (activeImageSlider != null) {
            updateImageSlider(mouseX)
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val wasDragging = activeHudSlider != null || activeTargetSlider != null || activeWorldSlider != null || activeAspectSlider || activeImageSlider != null || colorPickerDrag != null
        activeHudSlider = null
        activeTargetSlider = null
        activeWorldSlider = null
        activeAspectSlider = false
        activeImageSlider = null
        colorPickerDrag = null
        if (pendingImageChromaReload) {
            pendingImageChromaReload = false
            ImageRenderModule.reload()
        }
        return wasDragging
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (imageNameEditing) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> imageNameEditing = false
                GLFW.GLFW_KEY_BACKSPACE -> imageNameInput = imageNameInput.dropLast(1)
                GLFW.GLFW_KEY_DELETE -> imageNameInput = ""
                GLFW.GLFW_KEY_TAB -> {
                    val files = imageFilesInFolder()
                    val match = files.find { it.startsWith(imageNameInput, ignoreCase = true) && !it.equals(imageNameInput, ignoreCase = true) }
                    if (match != null) imageNameInput = match
                }
                else -> return false
            }
            return true
        }
        if (streamerReplacementEditing) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> streamerReplacementEditing = false
                GLFW.GLFW_KEY_BACKSPACE -> StreamerModeSettings.setReplacement(StreamerModeSettings.replacement().dropLast(1))
                GLFW.GLFW_KEY_DELETE -> StreamerModeSettings.setReplacement("")
                else -> return false
            }
            return true
        }
        val moduleId = bindingModuleId ?: return false
        val title = bindingModuleTitle ?: module()?.title ?: moduleId
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE,
            GLFW.GLFW_KEY_BACKSPACE,
            GLFW.GLFW_KEY_DELETE -> ModuleHotkeys.unbind(moduleId)
            else -> ModuleHotkeys.bind(moduleId, title, keyCode)
        }
        bindingModuleId = null
        bindingModuleTitle = null
        return true
    }

    fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (imageNameEditing && !chr.isISOControl()) {
            imageNameInput = (imageNameInput + chr).take(64)
            return true
        }
        if (!streamerReplacementEditing || chr.isISOControl()) return false
        StreamerModeSettings.setReplacement((StreamerModeSettings.replacement() + chr).take(32))
        return true
    }

    fun onScreenClose() {
        bindingModuleId = null
        bindingModuleTitle = null
        imageNameEditing = false
        pendingImageChromaReload = false
        streamerReplacementEditing = false
        colorPickerTarget = null
        colorPickerDrag = null
        activeHudSlider = null
        activeTargetSlider = null
        activeWorldSlider = null
        activeAspectSlider = false
    }

    private fun syncScrollState() {
        val moduleId = module()?.id
        if (moduleId != lastModuleId) {
            contentScroll.snap(0.0f)
            contentScroll.target = 0.0f
            activeWorldSlider = null
            activeAspectSlider = false
            imageNameEditing = false
            pendingImageChromaReload = false
            streamerReplacementEditing = false
            iconPaletteOpen = false
            fogPaletteOpen = false
            themePaletteTarget = null
            colorPickerTarget = null
            colorPickerDrag = null
            lastModuleId = moduleId
        }
        maxContentScroll = (contentBottomY() - (HEIGHT - CONTENT_BOTTOM_PAD)).coerceAtLeast(0.0f)
        contentScroll.target = contentScroll.target.coerceIn(0.0f, maxContentScroll)
    }

    private fun contentBottomY(): Float {
        return when (module()?.id) {
            "hud.watermark" -> if (WatermarkSettings.version() == WatermarkSettings.Version.V1) 225.0f else 327.0f
            "hud.hotbar", "hud.armor" -> 335.0f
            "hud.target" -> if (TargetHudSettings.state().version.ordinal >= TargetHudSettings.Version.V4.ordinal) 605.0f else 305.0f
            "hud.player_info" -> 355.0f
            "hud.inventory" -> 223.0f
            "hud.cooldowns", "hud.potions", "hud.hotkeys" -> 223.0f
            "client.icons" -> if (iconPaletteOpen) 269.0f else 163.0f
            "world.custom_fog" -> if (fogPaletteOpen) 475.0f else 366.0f
            "client.theme" -> if (themePaletteTarget != null) 553.0f else 445.0f
            "other.friends" -> 195.0f
            "other.streamer_mode" -> 235.0f
            "visuals.aspect_ratio" -> 231.0f
            "client.images" -> imageSettingsContentHeight()
            else -> 289.0f
        }
    }

    private fun renderContentScrollbar(context: DrawContext, offset: Float) {
        val trackY = bounds.y + CONTENT_TOP + 8.0f
        val trackHeight = HEIGHT - CONTENT_TOP - CONTENT_BOTTOM_PAD - 16.0f
        val viewportHeight = HEIGHT - CONTENT_TOP - CONTENT_BOTTOM_PAD
        val contentHeight = (contentBottomY() - CONTENT_TOP).coerceAtLeast(viewportHeight)
        val thumbHeight = (trackHeight * (viewportHeight / contentHeight)).coerceIn(28.0f, trackHeight)
        val travel = (trackHeight - thumbHeight).coerceAtLeast(0.0f)
        val thumbY = trackY + travel * (offset / maxContentScroll.coerceAtLeast(1.0f))

        HypnosiaRenderUtils.drawFigmaBox(context, bounds.right - 10.0f, trackY, 3.0f, trackHeight, 2.0f, 0x332A2A33)
        HypnosiaRenderUtils.drawFigmaBox(context, bounds.right - 11.0f, thumbY, 5.0f, thumbHeight, 3.0f, 0x99EDEDF1.toInt())
    }

    private fun renderScrollableContent(context: DrawContext) {
        val currentModule = module()
        if (currentModule?.id == "hud.watermark") {
            renderWatermarkSettings(context)
        } else if (currentModule?.id == "hud.hotbar") {
            renderHudModuleSettings(context, HudModuleSettings.Module.HOTBAR)
        } else if (currentModule?.id == "hud.armor") {
            renderHudModuleSettings(context, HudModuleSettings.Module.ARMOR)
        } else if (currentModule?.id == "hud.target") {
            renderTargetHudSettings(context)
        } else if (currentModule?.id == "client.icons") {
            renderIconSettings(context)
        } else if (currentModule?.id == "world.custom_fog") {
            renderCustomFogSettings(context)
        } else if (currentModule?.id == "other.friends") {
            renderFriendsSettings(context)
        } else if (currentModule?.id == "other.streamer_mode") {
            renderStreamerModeSettings(context)
        } else if (currentModule?.id == "visuals.aspect_ratio") {
            renderAspectRatioSettings(context)
        } else if (currentModule?.id == "client.theme") {
            renderThemeSettings(context)
        } else if (currentModule?.id == "client.images") {
            renderImageSettings(context)
        } else if (currentModule?.id in hudModuleIds) {
            hudModuleForId(currentModule?.id)?.let { renderExtraHudModuleSettings(context, it) }
        } else {
            drawSimpleRow(context, bounds.x + 11.0f, bounds.y + 75.0f, "Apply To", 9.0f, "All GUI", 131.0f, 0xFFBFC0CA.toInt())
            drawSliderRow(context, bounds.x + 11.0f, bounds.y + 127.0f, "Water Icons", "Hide")
            drawSliderRow(context, bounds.x + 11.0f, bounds.y + 187.0f, "Black-Hole", "Keep")
            drawSimpleRow(context, bounds.x + 11.0f, bounds.y + 249.0f, "Icon Color", 9.0f, "#F2F2F2", 115.0f, 0xFFBFC0CA.toInt())
            HypnosiaRenderUtils.drawFigmaBox(context, bounds.x + 11.0f + COLOR_SWATCH_X, bounds.y + 261.0f, 14.0f, 14.0f, 7.0f, 0xFFF2F2F2.toInt())
        }
    }

    private fun renderWatermarkSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Version",
            labelOffX = 9.0f,
            value = WatermarkSettings.version().name,
            valueOffX = 158.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        if (WatermarkSettings.version() == WatermarkSettings.Version.V1) {
            drawGroupCard(context, bounds.x + 11.0f, bounds.y + 127.0f, "V1 watermark")
            drawText(context, "Hover-expand track watermark", bounds.x + 21.0f, bounds.y + 161.0f, 12.0f, 0xFFE7E7EA.toInt())
            drawText(context, "Module toggles are used only", bounds.x + 21.0f, bounds.y + 187.0f, 12.0f, 0xFF8E8E98.toInt())
            drawText(context, "for the V2 compact layout.", bounds.x + 21.0f, bounds.y + 203.0f, 12.0f, 0xFF8E8E98.toInt())
            return
        }

        drawGroupCard(context, bounds.x + 11.0f, bounds.y + 127.0f, "Top line")
        drawToggleChip(context, WatermarkSettings.Module.VISUAL_ICON, bounds.x + 20.0f, bounds.y + 161.0f, 92.0f, 24.0f)
        drawToggleChip(context, WatermarkSettings.Module.ROLE, bounds.x + 124.0f, bounds.y + 161.0f, 92.0f, 24.0f)
        drawToggleChip(context, WatermarkSettings.Module.NICK, bounds.x + 20.0f, bounds.y + 195.0f, 92.0f, 24.0f)
        drawToggleChip(context, WatermarkSettings.Module.FPS, bounds.x + 124.0f, bounds.y + 195.0f, 92.0f, 24.0f)

        drawGroupCard(context, bounds.x + 11.0f, bounds.y + 235.0f, "Bottom line")
        drawToggleChip(context, WatermarkSettings.Module.SERVER, bounds.x + 20.0f, bounds.y + 269.0f, 92.0f, 24.0f)
        drawToggleChip(context, WatermarkSettings.Module.PING, bounds.x + 124.0f, bounds.y + 269.0f, 92.0f, 24.0f)
        drawToggleChip(context, WatermarkSettings.Module.RAM, bounds.x + 20.0f, bounds.y + 303.0f, 92.0f, 24.0f)
        drawToggleChip(context, WatermarkSettings.Module.CPU, bounds.x + 124.0f, bounds.y + 303.0f, 92.0f, 24.0f)
    }

    private fun renderHudModuleSettings(context: DrawContext, hudModule: HudModuleSettings.Module) {
        val state = HudModuleSettings.state(hudModule)
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Version",
            labelOffX = 9.0f,
            value = state.version.name,
            valueOffX = 158.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 123.0f,
            label = "Axis",
            labelOffX = 9.0f,
            value = state.axis.name,
            valueOffX = 172.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 175.0f, "X position", state.x)
        drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 235.0f, "Y position", state.y)
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 295.0f,
            label = "Slot highlight",
            labelOffX = 9.0f,
            value = if (state.slotHighlight) "On" else "Off",
            valueOffX = 164.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
    }

    private fun renderExtraHudModuleSettings(context: DrawContext, hudModule: HudModuleSettings.Module) {
        val state = HudModuleSettings.state(hudModule)
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Version",
            labelOffX = 9.0f,
            value = state.version.name,
            valueOffX = 158.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        if (hudModule == HudModuleSettings.Module.PLAYER_INFO) {
            HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 123.0f, 258.0f, 104.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
            drawText(context, "Parts", bounds.x + 21.0f, bounds.y + 132.0f, 12.0f, 0xFFE7E7EA.toInt())
            drawPlayerInfoToggleChip(context, "BPS", state.playerInfoBps, bounds.x + 20.0f, bounds.y + 160.0f)
            drawPlayerInfoToggleChip(context, "TPS", state.playerInfoTps, bounds.x + 124.0f, bounds.y + 160.0f)
            drawPlayerInfoToggleChip(context, "Cords", state.playerInfoCords, bounds.x + 20.0f, bounds.y + 194.0f, 196.0f)
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 247.0f, "X position", state.x)
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 307.0f, "Y position", state.y)
            return
        }
        val sliderY = 123.0f
        drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + sliderY, "X position", state.x)
        drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + sliderY + 60.0f, "Y position", state.y)
    }

    private fun renderTargetHudSettings(context: DrawContext) {
        val state = TargetHudSettings.state()
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Version",
            labelOffX = 9.0f,
            value = state.version.name,
            valueOffX = 158.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 123.0f, "X position", state.x)
        drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 183.0f, "Y position", state.y)

        if (state.version.ordinal >= TargetHudSettings.Version.V4.ordinal) {
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 243.0f, "Model X", TargetHudSettings.offsetToSlider(state.modelOffsetX))
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 303.0f, "Model Y", TargetHudSettings.offsetToSlider(state.modelOffsetY))
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 363.0f, "Model yaw", TargetHudSettings.yawToSlider(state.modelYaw))
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 423.0f, "Model pitch", TargetHudSettings.pitchToSlider(state.modelPitch))
            drawHudSliderRow(context, bounds.x + 11.0f, bounds.y + 483.0f, "Model scale", TargetHudSettings.scaleToSlider(state.modelScale))
            HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 543.0f, 258.0f, 62.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
            drawText(context, "Options", bounds.x + 21.0f, bounds.y + 552.0f, 12.0f, 0xFFE7E7EA.toInt())
            drawTargetToggleChip(context, "Hand + Armor Strip", state.showEquipmentStrip, bounds.x + 20.0f, bounds.y + 578.0f, 196.0f)
        } else {
            HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 243.0f, 258.0f, 62.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
            drawText(context, "Options", bounds.x + 21.0f, bounds.y + 252.0f, 12.0f, 0xFFE7E7EA.toInt())
            drawTargetToggleChip(context, "Hand + Armor Strip", state.showEquipmentStrip, bounds.x + 20.0f, bounds.y + 278.0f, 196.0f)
        }
    }

    private fun renderIconSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Black-Hole",
            labelOffX = 9.0f,
            value = if (IconSettings.blackHoleVisible) "Show" else "Hide",
            valueOffX = 156.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 123.0f,
            label = "Icon Color",
            labelOffX = 9.0f,
            value = IconSettings.colorHex(),
            valueOffX = 115.0f,
            valueColor = 0xFFBFC0CA.toInt(),
        )
        HypnosiaRenderUtils.drawFigmaBox(context, bounds.x + 11.0f + COLOR_SWATCH_X, bounds.y + 136.0f, 14.0f, 14.0f, 7.0f, IconSettings.color)

        if (!iconPaletteOpen) return
        HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 171.0f, 258.0f, 98.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
        drawText(context, "Palette", bounds.x + 21.0f, bounds.y + 181.0f, 12.0f, 0xFFE7E7EA.toInt())
        iconPaletteSwatches().forEach { (color, rect) ->
            val selected = (IconSettings.color and 0x00FFFFFF) == (color and 0x00FFFFFF)
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                radius = 7.0f,
                bgColor = color,
                strokeColor = if (selected) WHITE else DRAWER_STROKE,
                strokeThickness = if (selected) 2.0f else 1.0f,
            )
        }
    }

    private fun renderThemeSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Theme",
            labelOffX = 9.0f,
            value = ThemeSettings.mode().label,
            valueOffX = 138.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 123.0f,
            label = "Font",
            labelOffX = 9.0f,
            value = ThemeSettings.fontMode().label,
            valueOffX = 132.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 171.0f,
            label = "Liquid Glass",
            labelOffX = 9.0f,
            value = if (ThemeSettings.glassActive()) "On" else "Off",
            valueOffX = 164.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 219.0f,
            label = "Gradient",
            labelOffX = 9.0f,
            value = if (ThemeSettings.gradient()) "On" else "Off",
            valueOffX = 164.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawThemeColorRow(context, bounds.x + 11.0f, bounds.y + 267.0f, "Base Color", ThemeSettings.baseColor())
        drawThemeColorRow(context, bounds.x + 11.0f, bounds.y + 315.0f, "Gradient A", ThemeSettings.gradientStart())
        drawThemeColorRow(context, bounds.x + 11.0f, bounds.y + 363.0f, "Gradient B", ThemeSettings.gradientEnd())

        drawText(context, "Custom font file:", bounds.x + 21.0f, bounds.y + 414.0f, 10.0f, 0xFF8E8E98.toInt())
        drawText(context, ThemeSettings.customFontPathHint(), bounds.x + 21.0f, bounds.y + 430.0f, 10.0f, 0xFFBFC0CA.toInt())

        val target = themePaletteTarget ?: return
        HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 455.0f, 258.0f, 98.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
        drawText(context, "Palette: ${target.label}", bounds.x + 21.0f, bounds.y + 465.0f, 12.0f, 0xFFE7E7EA.toInt())
        val currentColor = when (target) {
            ThemePaletteTarget.BASE -> ThemeSettings.baseColor()
            ThemePaletteTarget.GRADIENT_START -> ThemeSettings.gradientStart()
            ThemePaletteTarget.GRADIENT_END -> ThemeSettings.gradientEnd()
        }
        themePaletteSwatches().forEach { (color, rect) ->
            val selected = (currentColor and 0x00FFFFFF) == (color and 0x00FFFFFF)
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                radius = 7.0f,
                bgColor = color,
                strokeColor = if (selected) WHITE else DRAWER_STROKE,
                strokeThickness = if (selected) 2.0f else 1.0f,
            )
        }
    }

    private fun renderCustomFogSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Mode",
            labelOffX = 9.0f,
            value = if (WorldVisualSettings.customFogEnabled()) "Custom" else "Vanilla",
            valueOffX = 138.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawValueSliderRow(
            context,
            bounds.x + 11.0f,
            bounds.y + 123.0f,
            "Distance",
            "${WorldVisualSettings.fogDistance().toInt()}m",
            WorldVisualSettings.fogDistanceSlider(),
        )
        drawValueSliderRow(
            context,
            bounds.x + 11.0f,
            bounds.y + 183.0f,
            "Strength",
            "${(WorldVisualSettings.fogStrength() * 100.0f).toInt()}%",
            WorldVisualSettings.fogStrengthSlider(),
        )
        drawValueSliderRow(
            context,
            bounds.x + 11.0f,
            bounds.y + 243.0f,
            "Soft Fog",
            "${(WorldVisualSettings.fogSoftness() * 100.0f).toInt()}%",
            WorldVisualSettings.fogSoftnessSlider(),
        )
        drawWorldColorRow(context, bounds.x + 11.0f, bounds.y + 303.0f, "Fog Color", WorldVisualSettings.fogColor())
        drawText(context, "Water and lava fog stay vanilla.", bounds.x + 21.0f, bounds.y + 356.0f, 10.0f, 0xFF8E8E98.toInt())
        if (!fogPaletteOpen) return
        HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 377.0f, 258.0f, 98.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
        drawText(context, "Fog Palette", bounds.x + 21.0f, bounds.y + 387.0f, 12.0f, 0xFFE7E7EA.toInt())
        fogPaletteSwatches().forEach { (color, rect) ->
            val selected = (WorldVisualSettings.fogColor() and 0x00FFFFFF) == (color and 0x00FFFFFF)
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                radius = 7.0f,
                bgColor = color,
                strokeColor = if (selected) WHITE else DRAWER_STROKE,
                strokeThickness = if (selected) 2.0f else 1.0f,
            )
        }
    }

    private fun renderFriendsSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Tab Display",
            labelOffX = 9.0f,
            value = if (FriendsManager.isEnabled()) "On" else "Off",
            valueOffX = 164.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawGroupCard(context, bounds.x + 11.0f, bounds.y + 127.0f, "Friends")
        drawText(context, "Shows friends in the player tab list.", bounds.x + 21.0f, bounds.y + 161.0f, 12.0f, 0xFFE7E7EA.toInt())
        drawText(context, "Use Bind above for quick toggle.", bounds.x + 21.0f, bounds.y + 187.0f, 12.0f, 0xFF8E8E98.toInt())
    }

    private fun renderStreamerModeSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Mode",
            labelOffX = 9.0f,
            value = StreamerModeSettings.level().label,
            valueOffX = 136.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 123.0f,
            label = "Replace With",
            labelOffX = 9.0f,
            value = if (streamerReplacementEditing) StreamerModeSettings.replacement() + "_" else StreamerModeSettings.replacement(),
            valueOffX = 104.0f,
            valueColor = 0xFFBFC0CA.toInt(),
        )
        drawGroupCard(context, bounds.x + 11.0f, bounds.y + 175.0f, "Streamer Mode")
        drawText(context, "Level 1 hides only your nickname.", bounds.x + 21.0f, bounds.y + 209.0f, 12.0f, 0xFFE7E7EA.toInt())
        drawText(context, "Level 2 replaces all player names.", bounds.x + 21.0f, bounds.y + 225.0f, 12.0f, 0xFF8E8E98.toInt())
    }

    private fun renderAspectRatioSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Mode",
            labelOffX = 9.0f,
            value = AspectRatioSettings.mode().label,
            valueOffX = 150.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawValueSliderRow(
            context,
            bounds.x + 11.0f,
            bounds.y + 123.0f,
            "Free Ratio",
            String.format(Locale.US, "%.2f", AspectRatioSettings.freeValue()),
            AspectRatioSettings.freeSlider(),
        )
        drawGroupCard(context, bounds.x + 11.0f, bounds.y + 183.0f, "Aspect Ratio")
        drawText(context, "Changes camera projection aspect.", bounds.x + 21.0f, bounds.y + 217.0f, 12.0f, 0xFFE7E7EA.toInt())
        drawText(context, "Use Free for custom stretch.", bounds.x + 21.0f, bounds.y + 233.0f, 12.0f, 0xFF8E8E98.toInt())
    }

    private fun renderFullbrightSettings(context: DrawContext) {
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Gamma",
            labelOffX = 9.0f,
            value = if (WorldVisualSettings.fullbrightEnabled()) "16.0" else "Vanilla",
            valueOffX = 142.0f,
            valueColor = 0xFFFF2F86.toInt(),
        )
        drawGroupCard(context, bounds.x + 11.0f, bounds.y + 127.0f, "Fullbright")
        drawText(context, "Boosts client gamma while enabled.", bounds.x + 21.0f, bounds.y + 161.0f, 12.0f, 0xFFE7E7EA.toInt())
        drawText(context, "Previous gamma is restored on disable.", bounds.x + 21.0f, bounds.y + 187.0f, 12.0f, 0xFF8E8E98.toInt())
    }

    private fun drawThemeColorRow(context: DrawContext, x: Float, y: Float, label: String, color: Int) {
        drawSimpleRow(
            context = context,
            x = x,
            y = y,
            label = label,
            labelOffX = 9.0f,
            value = ThemeSettings.colorHex(color),
            valueOffX = 112.0f,
            valueColor = 0xFFBFC0CA.toInt(),
        )
        HypnosiaRenderUtils.drawFigmaBox(context, x + COLOR_SWATCH_X - 16.0f, y + 13.0f, 14.0f, 14.0f, 7.0f, color)
    }

    private fun drawWorldColorRow(context: DrawContext, x: Float, y: Float, label: String, color: Int) {
        drawSimpleRow(
            context = context,
            x = x,
            y = y,
            label = label,
            labelOffX = 9.0f,
            value = WorldVisualSettings.colorHex(color),
            valueOffX = 112.0f,
            valueColor = 0xFFBFC0CA.toInt(),
        )
        HypnosiaRenderUtils.drawFigmaBox(context, x + COLOR_SWATCH_X, y + 13.0f, 14.0f, 14.0f, 7.0f, color)
    }

    private fun renderColorPicker(context: DrawContext) {
        val target = colorPickerTarget ?: return
        val x = colorPickerX()
        val y = colorPickerY()
        val color = colorForTarget(target)
        val hsv = hsvFor(color)
        val alpha = ((color ushr 24) and 0xFF) / 255.0f

        HypnosiaRenderUtils.drawThemedBox(context, x, y, COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT, 10.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.DRAWER)
        drawButton(context, x + 8.0f, y + 8.0f, 66.0f, 32.0f, "Close")

        colorPickerSwatches().forEach { (swatch, rect) ->
            val selected = (color and 0x00FFFFFF) == (swatch and 0x00FFFFFF)
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                radius = 4.0f,
                bgColor = swatch,
                strokeColor = if (selected) WHITE else 0x00000000,
                strokeThickness = if (selected) 2.0f else 0.0f,
            )
        }

        val canvas = pickerCanvasRect()
        HypnosiaRenderUtils.drawThemedBox(context, canvas.x - 10.0f, canvas.y - 10.0f, canvas.width + 20.0f, canvas.height + 20.0f, 10.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
        HypnosiaRenderUtils.drawHsvColorCanvas(context, canvas.x, canvas.y, canvas.width, canvas.height, 8.0f, hsv[0] * 360.0f)
        val markerX = canvas.x + hsv[1] * canvas.width
        val markerY = canvas.y + (1.0f - hsv[2]) * canvas.height
        HypnosiaRenderUtils.drawFigmaBox(context, markerX - 7.0f, markerY - 7.0f, 14.0f, 14.0f, 7.0f, 0x33000000, 0x66000000, 1.0f)
        HypnosiaRenderUtils.drawFigmaBox(context, markerX - 6.0f, markerY - 6.0f, 14.0f, 14.0f, 7.0f, 0x00000000, WHITE, 2.0f)

        val hue = pickerHueRect()
        HypnosiaRenderUtils.drawHueStrip(context, hue.x, hue.y, hue.width, hue.height, 5.0f)
        HypnosiaRenderUtils.drawFigmaBox(context, hue.x + hsv[0] * hue.width - 6.0f, hue.y - 1.0f, 12.0f, 12.0f, 6.0f, WHITE)

        val opacity = pickerOpacityRect()
        HypnosiaRenderUtils.drawAlphaStrip(context, opacity.x, opacity.y, opacity.width, opacity.height, 5.0f, color)
        HypnosiaRenderUtils.drawFigmaBox(context, opacity.x + alpha * opacity.width - 6.0f, opacity.y - 1.0f, 12.0f, 12.0f, 6.0f, WHITE)

        val valueY = y + 198.0f
        HypnosiaRenderUtils.drawThemedBox(context, x + 8.0f, valueY, 54.0f, 34.0f, 7.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
        drawText(context, "HEX", x + 22.0f, valueY + 10.0f, 12.0f, 0xFFE7E7EA.toInt())
        HypnosiaRenderUtils.drawThemedBox(context, x + 69.0f, valueY, 137.0f, 34.0f, 7.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.INPUT)
        drawText(context, hexWithAlpha(color), x + 83.0f, valueY + 10.0f, 12.0f, 0xFFBFC0CA.toInt())
        HypnosiaRenderUtils.drawThemedBox(context, x + 214.0f, valueY, 74.0f, 34.0f, 7.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.INPUT)
        drawText(context, "${(alpha * 100.0f).toInt()}%", x + 232.0f, valueY + 10.0f, 12.0f, 0xFFE7E7EA.toInt())
    }

    private fun handleColorPickerClick(mouseX: Float, mouseY: Float): Boolean {
        val target = colorPickerTarget ?: return false
        val picker = Rect(colorPickerX(), colorPickerY(), COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT)
        if (!contains(mouseX, mouseY, picker.x, picker.y, picker.width, picker.height)) return false
        if (contains(mouseX, mouseY, picker.x + 10.0f, picker.y + 10.0f, 64.0f, 30.0f)) {
            colorPickerTarget = null
            colorPickerDrag = null
            return true
        }
        colorPickerSwatches().forEach { (color, rect) ->
            if (contains(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)) {
                val alpha = (colorForTarget(target) ushr 24) and 0xFF
                setColorForTarget(target, (color and 0x00FFFFFF) or (alpha shl 24))
                return true
            }
        }
        val drag = when {
            contains(mouseX, mouseY, pickerCanvasRect().x, pickerCanvasRect().y, pickerCanvasRect().width, pickerCanvasRect().height) -> ColorPickerDrag.CANVAS
            contains(mouseX, mouseY, pickerHueRect().x - 5.0f, pickerHueRect().y, pickerHueRect().width + 10.0f, pickerHueRect().height) -> ColorPickerDrag.HUE
            contains(mouseX, mouseY, pickerOpacityRect().x - 5.0f, pickerOpacityRect().y, pickerOpacityRect().width + 10.0f, pickerOpacityRect().height) -> ColorPickerDrag.OPACITY
            else -> null
        }
        if (drag != null) {
            colorPickerDrag = drag
            updateColorPickerDrag(mouseX, mouseY, drag)
        }
        return true
    }

    private fun updateColorPickerDrag(mouseX: Float, mouseY: Float, drag: ColorPickerDrag) {
        val target = colorPickerTarget ?: return
        val current = colorForTarget(target)
        val hsv = hsvFor(current)
        val alpha = ((current ushr 24) and 0xFF) / 255.0f
        val next = when (drag) {
            ColorPickerDrag.CANVAS -> {
                val rect = pickerCanvasRect()
                val sat = ((mouseX - rect.x) / rect.width).coerceIn(0.0f, 1.0f)
                val value = (1.0f - (mouseY - rect.y) / rect.height).coerceIn(0.0f, 1.0f)
                colorFromHsv(hsv[0], sat, value, alpha)
            }
            ColorPickerDrag.HUE -> {
                val rect = pickerHueRect()
                val hue = ((mouseX - rect.x) / rect.width).coerceIn(0.0f, 1.0f)
                colorFromHsv(hue, hsv[1], hsv[2], alpha)
            }
            ColorPickerDrag.OPACITY -> {
                val rect = pickerOpacityRect()
                val nextAlpha = ((mouseX - rect.x) / rect.width).coerceIn(0.0f, 1.0f)
                colorFromHsv(hsv[0], hsv[1], hsv[2], nextAlpha)
            }
        }
        setColorForTarget(target, next)
    }

    private fun updateHudSlider(mouseX: Float) {
        val slider = activeHudSlider ?: return
        val trackX = bounds.x + 93.0f
        val trackW = 112.0f
        val value = ((mouseX - trackX) / trackW).coerceIn(0.0f, 1.0f)
        when (slider.kind) {
            HudSliderKind.X -> HudModuleSettings.setX(slider.module, value)
            HudSliderKind.Y -> HudModuleSettings.setY(slider.module, value)
        }
    }

    private fun updateTargetSlider(mouseX: Float) {
        val slider = activeTargetSlider ?: return
        val trackX = bounds.x + 93.0f
        val trackW = 112.0f
        val value = ((mouseX - trackX) / trackW).coerceIn(0.0f, 1.0f)
        when (slider) {
            TargetSliderKind.X -> TargetHudSettings.setX(value)
            TargetSliderKind.Y -> TargetHudSettings.setY(value)
            TargetSliderKind.MODEL_X -> TargetHudSettings.setModelOffsetX(TargetHudSettings.sliderToOffset(value))
            TargetSliderKind.MODEL_Y -> TargetHudSettings.setModelOffsetY(TargetHudSettings.sliderToOffset(value))
            TargetSliderKind.MODEL_YAW -> TargetHudSettings.setModelYaw(TargetHudSettings.sliderToYaw(value))
            TargetSliderKind.MODEL_PITCH -> TargetHudSettings.setModelPitch(TargetHudSettings.sliderToPitch(value))
            TargetSliderKind.MODEL_SCALE -> TargetHudSettings.setModelScale(TargetHudSettings.sliderToScale(value))
        }
    }

    private fun updateWorldSlider(mouseX: Float) {
        val slider = activeWorldSlider ?: return
        val trackX = bounds.x + 93.0f
        val trackW = 112.0f
        val value = ((mouseX - trackX) / trackW).coerceIn(0.0f, 1.0f)
        when (slider) {
            WorldSliderKind.FOG_DISTANCE -> WorldVisualSettings.setFogDistanceFromSlider(value)
            WorldSliderKind.FOG_STRENGTH -> WorldVisualSettings.setFogStrengthFromSlider(value)
            WorldSliderKind.FOG_SOFTNESS -> WorldVisualSettings.setFogSoftnessFromSlider(value)
        }
    }

    private fun updateAspectSlider(mouseX: Float) {
        val trackX = bounds.x + 93.0f
        val trackW = 112.0f
        AspectRatioSettings.setFreeFromSlider(((mouseX - trackX) / trackW).coerceIn(0.0f, 1.0f))
    }

    private fun updateImageSlider(mouseX: Float) {
        val slider = activeImageSlider ?: return
        val trackX = bounds.x + 93.0f
        val trackW = 112.0f
        val normalized = ((mouseX - trackX) / trackW).coerceIn(0.0f, 1.0f)
        val entry = ImageRenderConfig.entries().find { it.path == slider.path } ?: return
        when (slider.kind) {
            ImageSliderKind.SCALE -> {
                val scale = 0.5f + normalized * 2.5f
                ImageRenderConfig.update(entry.copy(scale = scale))
            }
        }
    }

    private fun drawGroupCard(context: DrawContext, x: Float, y: Float, title: String) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, 258.0f, 96.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
        drawText(context, title, x + 10.0f, y + 9.0f, 12.0f, 0xFFE7E7EA.toInt())
    }

    private fun drawToggleChip(
        context: DrawContext,
        module: WatermarkSettings.Module,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val enabled = WatermarkSettings.isEnabled(module)
        val bg = if (enabled) 0x22FF2F86 else 0x00111114
        val stroke = if (enabled) 0xFFFF2F86.toInt() else 0xFF34343C.toInt()
        val textColor = if (enabled) WHITE else 0xFFBFC0CA.toInt()
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 10.0f, bg, stroke, 1.0f)
        drawTextBox(
            context = context,
            text = module.label,
            x = x + 10.0f,
            y = y - 1.0f,
            width = width - 20.0f,
            height = height,
            color = textColor,
            style = styleFor(12.0f, FigmaTextRenderer.Font.Main),
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
        )
    }

    private fun drawTargetToggleChip(context: DrawContext, label: String, enabled: Boolean, x: Float, y: Float, width: Float = 92.0f) {
        val bg = if (enabled) 0x22FF2F86 else 0x00111114
        val stroke = if (enabled) 0xFFFF2F86.toInt() else 0xFF34343C.toInt()
        val textColor = if (enabled) WHITE else 0xFFBFC0CA.toInt()
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, 24.0f, 10.0f, bg, stroke, 1.0f)
        drawTextBox(
            context = context,
            text = label,
            x = x + 10.0f,
            y = y - 1.0f,
            width = width - 20.0f,
            height = 24.0f,
            color = textColor,
            style = styleFor(12.0f, FigmaTextRenderer.Font.Main),
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
        )
    }

    private fun drawPlayerInfoToggleChip(context: DrawContext, label: String, enabled: Boolean, x: Float, y: Float, width: Float = 92.0f) {
        val bg = if (enabled) 0x22FF2F86 else 0x00111114
        val stroke = if (enabled) 0xFFFF2F86.toInt() else 0xFF34343C.toInt()
        val textColor = if (enabled) WHITE else 0xFFBFC0CA.toInt()
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, 24.0f, 10.0f, bg, stroke, 1.0f)
        drawTextBox(
            context = context,
            text = label,
            x = x + 10.0f,
            y = y - 1.0f,
            width = width - 20.0f,
            height = 24.0f,
            color = textColor,
            style = styleFor(12.0f, FigmaTextRenderer.Font.Main),
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
        )
    }

    private fun playerInfoToggles(): List<Pair<HudModuleSettings.PlayerInfoPart, Rect>> {
        return listOf(
            HudModuleSettings.PlayerInfoPart.BPS to Rect(bounds.x + 20.0f, bounds.y + 160.0f, 92.0f, 24.0f),
            HudModuleSettings.PlayerInfoPart.TPS to Rect(bounds.x + 124.0f, bounds.y + 160.0f, 92.0f, 24.0f),
            HudModuleSettings.PlayerInfoPart.CORDS to Rect(bounds.x + 20.0f, bounds.y + 194.0f, 196.0f, 24.0f),
        )
    }

    private fun targetToggles(): List<Pair<TargetToggle, Rect>> {
        val toggles = mutableListOf(
            TargetToggle.EQUIPMENT to if (TargetHudSettings.state().version.ordinal >= TargetHudSettings.Version.V4.ordinal) {
                Rect(bounds.x + 20.0f, bounds.y + 578.0f, 196.0f, 24.0f)
            } else {
                Rect(bounds.x + 20.0f, bounds.y + 278.0f, 196.0f, 24.0f)
            },
        )
        return toggles
    }

    private fun hudModuleForId(id: String?): HudModuleSettings.Module? {
        return when (id) {
            "hud.player_info" -> HudModuleSettings.Module.PLAYER_INFO
            "hud.inventory" -> HudModuleSettings.Module.INVENTORY
            "hud.cooldowns" -> HudModuleSettings.Module.COOLDOWNS
            "hud.potions" -> HudModuleSettings.Module.POTIONS
            "hud.hotkeys" -> HudModuleSettings.Module.HOTKEYS
            else -> null
        }
    }

    private fun maxVersionFor(module: HudModuleSettings.Module): HudModuleSettings.Version {
        return when (module) {
            HudModuleSettings.Module.COOLDOWNS,
            HudModuleSettings.Module.POTIONS,
            HudModuleSettings.Module.HOTKEYS -> HudModuleSettings.Version.V4
            else -> HudModuleSettings.Version.V2
        }
    }

    private fun watermarkToggles(): List<Pair<WatermarkSettings.Module, Rect>> {
        return listOf(
            WatermarkSettings.Module.VISUAL_ICON to Rect(bounds.x + 20.0f, bounds.y + 161.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.ROLE to Rect(bounds.x + 124.0f, bounds.y + 161.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.NICK to Rect(bounds.x + 20.0f, bounds.y + 195.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.FPS to Rect(bounds.x + 124.0f, bounds.y + 195.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.SERVER to Rect(bounds.x + 20.0f, bounds.y + 269.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.PING to Rect(bounds.x + 124.0f, bounds.y + 269.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.RAM to Rect(bounds.x + 20.0f, bounds.y + 303.0f, 92.0f, 24.0f),
            WatermarkSettings.Module.CPU to Rect(bounds.x + 124.0f, bounds.y + 303.0f, 92.0f, 24.0f),
        )
    }

    private fun iconPaletteSwatches(): List<Pair<Int, Rect>> {
        val colors = listOf(
            0xFFFFFFFF.toInt(),
            0xFF8E8E8E.toInt(),
            0xFFFF2F86.toInt(),
            0xFF68E673.toInt(),
            0xFF63D7FF.toInt(),
            0xFFE8DEFD.toInt(),
            0xFFFFD84D.toInt(),
            0xFFFF6B4A.toInt(),
            0xFF9D7CFF.toInt(),
            0xFF0D0D0D.toInt(),
        )
        return colors.mapIndexed { index, color ->
            val col = index % 5
            val row = index / 5
            color to Rect(bounds.x + 21.0f + col * 38.0f, bounds.y + 209.0f + row * 28.0f, 24.0f, 24.0f)
        }
    }

    private fun fogPaletteSwatches(): List<Pair<Int, Rect>> {
        val colors = listOf(
            0xFFC9D7E8.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFBFC0CA.toInt(),
            0xFF9BBEFF.toInt(),
            0xFFFFD7A3.toInt(),
            0xFFB8D7C3.toInt(),
            0xFF6F7F8A.toInt(),
            0xFF2F3138.toInt(),
            0xFFFFC1D9.toInt(),
            0xFFD8B4FE.toInt(),
        )
        return colors.mapIndexed { index, color ->
            val col = index % 5
            val row = index / 5
            color to Rect(bounds.x + 21.0f + col * 38.0f, bounds.y + 415.0f + row * 28.0f, 24.0f, 24.0f)
        }
    }

    private fun themePaletteSwatches(): List<Pair<Int, Rect>> {
        val colors = listOf(
            0xFF0D0D0D.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFBFC0CA.toInt(),
            0xFFFF2F86.toInt(),
            0xFF63D7FF.toInt(),
            0xFF68E673.toInt(),
            0xFFE8DEFD.toInt(),
            0xFF9D7CFF.toInt(),
            0xFFFFD84D.toInt(),
            0xFFFF6B4A.toInt(),
        )
        return colors.mapIndexed { index, color ->
            val col = index % 5
            val row = index / 5
            color to Rect(bounds.x + 21.0f + col * 38.0f, bounds.y + 493.0f + row * 28.0f, 24.0f, 24.0f)
        }
    }

    private fun colorPickerSwatches(): List<Pair<Int, Rect>> {
        val combined = (recentColors + defaultColorSwatches()).distinctBy { it and 0x00FFFFFF }
        return combined.mapIndexed { index, color ->
            val col = index % 11
            val row = index / 11
            color to Rect(colorPickerX() + 12.0f + col * 24.0f, colorPickerY() + 242.0f + row * 22.0f, 16.0f, 16.0f)
        }
    }

    private fun colorForTarget(target: ColorPickerTarget): Int =
        when (target) {
            ColorPickerTarget.ICON -> IconSettings.color
            ColorPickerTarget.FOG -> WorldVisualSettings.fogColor()
            ColorPickerTarget.THEME_BASE -> ThemeSettings.baseColor()
            ColorPickerTarget.THEME_GRADIENT_START -> ThemeSettings.gradientStart()
            ColorPickerTarget.THEME_GRADIENT_END -> ThemeSettings.gradientEnd()
            ColorPickerTarget.IMAGE_CHROMA -> {
                val path = imageColorPickerPath ?: return 0xFF00FF00.toInt()
                val entry = ImageRenderConfig.entries().find { it.path == path }
                entry?.chromaKeyColor ?: 0xFF00FF00.toInt()
            }
        }

    private fun setColorForTarget(target: ColorPickerTarget, color: Int) {
        val current = colorForTarget(target)
        if (current != color) {
            recordRecentColor(color)
        }
        when (target) {
            ColorPickerTarget.ICON -> IconSettings.setColor(color)
            ColorPickerTarget.FOG -> WorldVisualSettings.setFogColor(color)
            ColorPickerTarget.THEME_BASE -> ThemeSettings.setBaseColor(color)
            ColorPickerTarget.THEME_GRADIENT_START -> ThemeSettings.setGradientStart(color)
            ColorPickerTarget.THEME_GRADIENT_END -> ThemeSettings.setGradientEnd(color)
            ColorPickerTarget.IMAGE_CHROMA -> {
                val path = imageColorPickerPath ?: return
                val entry = ImageRenderConfig.entries().find { it.path == path } ?: return
                val updated = entry.copy(chromaKeyColor = color)
                ImageRenderConfig.update(updated)
                pendingImageChromaReload = true
            }
        }
    }

    private fun recordRecentColor(color: Int) {
        val rgb = color and 0x00FFFFFF
        recentColors.removeAll { it and 0x00FFFFFF == rgb }
        recentColors.add(0, color)
        if (recentColors.size > 22) {
            recentColors.removeLast()
        }
    }

    private fun defaultColorSwatches(): List<Int> = listOf(
        0xFFBFC0CA.toInt(),
        0xFFFF2F86.toInt(),
        0xFFFF6B4A.toInt(),
        0xFFFFA63D.toInt(),
        0xFFFFD84D.toInt(),
        0xFF68E673.toInt(),
        0xFF3FE08B.toInt(),
        0xFF52E09A.toInt(),
        0xFF49E184.toInt(),
        0xFF4FE492.toInt(),
        0xFF4BE68C.toInt(),
        0xFF38D6C8.toInt(),
        0xFF5797FF.toInt(),
        0xFF6D8CFF.toInt(),
        0xFF9D7CFF.toInt(),
        0xFF8E98A6.toInt(),
        0xFFB3356E.toInt(),
        0xFF57D586.toInt(),
        0xFF41D57C.toInt(),
        0xFF4EDB8B.toInt(),
        0xFF58E596.toInt(),
        0xFF58E596.toInt(),
    )

    private fun pickerCanvasRect(): Rect = Rect(colorPickerX() + 18.0f, colorPickerY() + 48.0f, 260.0f, 100.0f)

    private fun pickerHueRect(): Rect = Rect(colorPickerX() + 18.0f, colorPickerY() + 158.0f, 260.0f, 10.0f)

    private fun pickerOpacityRect(): Rect = Rect(colorPickerX() + 18.0f, colorPickerY() + 178.0f, 260.0f, 10.0f)

    private fun colorPickerX(): Float {
        val client = MinecraftClient.getInstance()
        val guiScale = client.window.scaleFactor.toFloat().coerceAtLeast(1.0f)
        val scale = 1.0f / guiScale
        val screenW = client.window.scaledWidth / scale
        val rightX = bounds.right + 8.0f
        return if (rightX + COLOR_PICKER_WIDTH <= screenW) {
            rightX
        } else {
            (bounds.x - COLOR_PICKER_WIDTH - 8.0f).coerceAtLeast(0.0f)
        }
    }

    private fun colorPickerY(): Float = bounds.y

    private fun hsvFor(color: Int): FloatArray {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return java.awt.Color.RGBtoHSB(r, g, b, null)
    }

    private fun colorFromHsv(hue: Float, saturation: Float, value: Float, alpha: Float): Int {
        val rgb = java.awt.Color.HSBtoRGB(hue.coerceIn(0.0f, 1.0f), saturation.coerceIn(0.0f, 1.0f), value.coerceIn(0.0f, 1.0f))
        val a = (alpha.coerceIn(0.0f, 1.0f) * 255.0f).toInt().coerceIn(0, 255)
        return (rgb and 0x00FFFFFF) or (a shl 24)
    }

    private fun hexNoAlpha(color: Int): String = "#%06X".format(color and 0x00FFFFFF)

    private fun hexWithAlpha(color: Int): String = "#%08X".format(color)

    private fun drawButton(context: DrawContext, x: Float, y: Float, width: Float, height: Float, label: String) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, width, height, 7.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
        drawTextBox(
            context = context,
            text = label,
            x = x + 6.0f,
            y = y - 1.0f,
            width = width - 12.0f,
            height = height,
            color = 0xFFD6D6DE.toInt(),
            style = styleFor(12.0f, FigmaTextRenderer.Font.Main),
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
        )
    }

    private fun drawSimpleRow(context: DrawContext, x: Float, y: Float, label: String, labelOffX: Float, value: String, valueOffX: Float, valueColor: Int) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, 258.0f, 40.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
        drawText(context, label, x + labelOffX, y + 12.0f, 13.0f, 0xFFE7E7EA.toInt())
        drawText(context, value, x + valueOffX, y + 12.0f, 13.0f, valueColor)
    }

    private fun drawSliderRow(context: DrawContext, x: Float, y: Float, label: String, value: String) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, 258.0f, 48.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
        drawText(context, label, x + 10.0f, y + 8.0f, 13.0f, 0xFFE7E7EA.toInt())
        drawText(context, value, x + 154.0f, y + 8.0f, 13.0f, 0xFFFF2F86.toInt())
        rect(context, x + 82.0f, y + 29.0f, 112.0f, 2.0f, 0xFF34343C.toInt())
        rect(context, x + 82.0f, y + 29.0f, 72.0f, 2.0f, 0xFFFF2F86.toInt())
        HypnosiaRenderUtils.drawFigmaBox(context, x + 149.0f, y + 25.0f, 10.0f, 10.0f, 5.0f, 0xFFFF2F86.toInt())
    }

    private fun drawHudSliderRow(context: DrawContext, x: Float, y: Float, label: String, value: Float) {
        val percentage = (value * 100.0f).toInt().coerceIn(0, 100)
        drawValueSliderRow(context, x, y, label, "$percentage%", value)
    }

    private fun drawValueSliderRow(context: DrawContext, x: Float, y: Float, label: String, valueText: String, value: Float) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, 258.0f, 48.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
        drawText(context, label, x + 10.0f, y + 8.0f, 13.0f, 0xFFE7E7EA.toInt())
        drawText(context, valueText, x + 146.0f, y + 8.0f, 13.0f, 0xFFFF2F86.toInt())
        rect(context, x + 82.0f, y + 29.0f, 112.0f, 2.0f, 0xFF34343C.toInt())
        rect(context, x + 82.0f, y + 29.0f, 112.0f * value.coerceIn(0.0f, 1.0f), 2.0f, 0xFFFF2F86.toInt())
        HypnosiaRenderUtils.drawFigmaBox(context, x + 77.0f + 112.0f * value.coerceIn(0.0f, 1.0f), y + 25.0f, 10.0f, 10.0f, 5.0f, 0xFFFF2F86.toInt())
    }

    private fun drawText(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        color: Int,
        font: FigmaTextRenderer.Font = FigmaTextRenderer.Font.Main,
    ) {
        FigmaTextRenderer.draw(context, text, x, y, color, styleFor(fontSize, font))
    }

    private fun drawTextBox(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        style: FigmaTextRenderer.FigmaTextStyle,
        horizontalAlign: FigmaTextRenderer.HorizontalAlign,
        verticalAlign: FigmaTextRenderer.VerticalAlign,
    ) {
        FigmaTextRenderer.drawInBox(context, text, x, y, width, height, color, style, horizontalAlign, verticalAlign)
    }

    private fun styleFor(fontSize: Float, font: FigmaTextRenderer.Font): FigmaTextRenderer.FigmaTextStyle {
        if (font == FigmaTextRenderer.Font.Title) {
            return FigmaTextRenderer.Styles.WelcomeTitle.copy(size = fontSize)
        }
        return when (fontSize) {
            10.0f -> FigmaTextRenderer.Styles.Ui10
            12.0f -> FigmaTextRenderer.Styles.Ui12
            13.0f -> FigmaTextRenderer.Styles.Ui13
            14.0f -> FigmaTextRenderer.Styles.Ui14
            16.0f -> FigmaTextRenderer.Styles.Ui16
            18.0f -> FigmaTextRenderer.Styles.Ui18
            else -> FigmaTextRenderer.FigmaTextStyle(
                font = font,
                size = fontSize,
                lineHeight = fontSize * 1.2f,
            )
        }
    }

    private fun rect(context: DrawContext, x: Float, y: Float, width: Float, height: Float, color: Int) {
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 0.0f, color)
    }

    private fun contains(mouseX: Float, mouseY: Float, x: Float, y: Float, width: Float, height: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    private data class HudSlider(val module: HudModuleSettings.Module, val kind: HudSliderKind)

    private enum class HudSliderKind {
        X,
        Y,
    }

    private data class ImageSlider(val path: String, val kind: ImageSliderKind)

    private enum class ImageSliderKind {
        SCALE,
    }

    private enum class TargetSliderKind {
        X,
        Y,
        MODEL_X,
        MODEL_Y,
        MODEL_YAW,
        MODEL_PITCH,
        MODEL_SCALE,
    }

    private enum class TargetToggle {
        EQUIPMENT,
        MODEL_SPIN,
    }

    private enum class WorldSliderKind {
        FOG_DISTANCE,
        FOG_STRENGTH,
        FOG_SOFTNESS,
    }

    private enum class ThemePaletteTarget(val label: String) {
        BASE("Base"),
        GRADIENT_START("Gradient A"),
        GRADIENT_END("Gradient B"),
    }

    private enum class ColorPickerTarget(val label: String) {
        ICON("Icon"),
        FOG("Fog"),
        THEME_BASE("Base"),
        THEME_GRADIENT_START("Gradient A"),
        THEME_GRADIENT_END("Gradient B"),
        IMAGE_CHROMA("Chroma Key"),
    }

    private enum class ColorPickerDrag {
        CANVAS,
        HUE,
        OPACITY,
    }

    private fun imageFilesInFolder(): List<String> {
        return runCatching {
            java.nio.file.Files.list(ImageRenderModule.KARTINKI_DIR).use { stream ->
                stream.filter { java.nio.file.Files.isRegularFile(it) }
                    .map { it.fileName.toString() }
                    .filter { it.lowercase().endsWith(".png") || it.lowercase().endsWith(".gif") }
                    .sorted()
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun imageSettingsContentHeight(): Float {
        val files = imageFilesInFolder()
        val selected = ImageRenderModule.selectedEntryPath
        val selectedIndex = files.indexOfFirst { it.equals(selected, ignoreCase = true) }
        val listBottom = 219.0f + files.size * 48.0f + 16.0f
        return if (selectedIndex >= 0) {
            val settingsTop = 219.0f + selectedIndex * 48.0f + 48.0f
            (settingsTop + 184.0f).coerceAtLeast(listBottom)
        } else {
            listBottom
        }
    }

    private fun renderImageSettings(context: DrawContext) {
        val files = imageFilesInFolder()
        val selected = ImageRenderModule.selectedEntryPath

        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 75.0f,
            label = "Scan Folder",
            labelOffX = 9.0f,
            value = "Tap",
            valueOffX = 206.0f,
            valueColor = 0xFFBFC0CA.toInt(),
        )
        drawSimpleRow(
            context = context,
            x = bounds.x + 11.0f,
            y = bounds.y + 123.0f,
            label = "Reload",
            labelOffX = 9.0f,
            value = "Tap",
            valueOffX = 206.0f,
            valueColor = 0xFFBFC0CA.toInt(),
        )
        // Add by name row
        val addNameLabel = if (imageNameEditing) imageNameInput + "_" else if (imageNameInput.isNotEmpty()) imageNameInput else "Add by name..."
        val addNameValue = if (imageNameInput.isNotEmpty()) "Add" else ""
        drawSimpleRow(context, bounds.x + 11.0f, bounds.y + 171.0f, addNameLabel, 9.0f, addNameValue, 206.0f, 0xFFBFC0CA.toInt())

        if (files.isEmpty()) {
            drawText(context, "No images in folder", bounds.x + 11.0f, bounds.y + 228.0f, 13.0f, 0xFF8E8E98.toInt())
            drawText(context, "Drop files to .minecraft/Hypnosia/kartinki/", bounds.x + 11.0f, bounds.y + 248.0f, 11.0f, 0xFF8E8E98.toInt())
            return
        }

        files.forEachIndexed { index, fileName ->
            val rowY = bounds.y + 219.0f + index * 48.0f
            val inConfig = ImageRenderConfig.contains(fileName)
            val entry = if (inConfig) ImageRenderConfig.entries().find { it.path.equals(fileName, ignoreCase = true) } else null
            val status = when {
                entry != null && entry.enabled -> "ON"
                entry != null && !entry.enabled -> "OFF"
                else -> "Add"
            }
            val statusColor = when {
                entry != null && entry.enabled -> 0xFF80FF97.toInt()
                entry != null && !entry.enabled -> 0xFFFF6B6B.toInt()
                else -> 0xFF66B2FF.toInt()
            }
            drawSimpleRow(
                context = context,
                x = bounds.x + 11.0f,
                y = rowY,
                label = fileName,
                labelOffX = 9.0f,
                value = status,
                valueOffX = 206.0f,
                valueColor = statusColor,
            )

            if (fileName.equals(selected, ignoreCase = true) && entry != null) {
                val sy = rowY + 48.0f
                val scaleNorm = (entry.scale - 0.5f) / 2.5f
                drawValueSliderRow(
                    context = context,
                    x = bounds.x + 11.0f,
                    y = sy,
                    label = "Scale",
                    valueText = String.format("%.1fx", entry.scale),
                    value = scaleNorm.coerceIn(0.0f, 1.0f),
                )
                val chromaLabel = if (entry.chromaKeyColor != null) "Chroma ON" else "Chroma OFF"
                val chromaColor = entry.chromaKeyColor ?: 0xFF272727.toInt()
                drawSimpleRow(
                    context = context,
                    x = bounds.x + 11.0f,
                    y = sy + 48.0f,
                    label = chromaLabel,
                    labelOffX = 9.0f,
                    value = "",
                    valueOffX = 206.0f,
                    valueColor = 0xFFBFC0CA.toInt(),
                )
                HypnosiaRenderUtils.drawFigmaBox(
                    context,
                    bounds.x + 220.0f,
                    sy + 56.0f,
                    14.0f,
                    14.0f,
                    7.0f,
                    chromaColor,
                )
                drawSimpleRow(context, bounds.x + 11.0f, sy + 96.0f, "Remove", 9.0f, "Tap", 206.0f, 0xFFFF6B6B.toInt())
            }
        }
    }

    companion object {
        private val DRAWER_BG = 0xFE0D0D0D.toInt()
        private val DRAWER_STROKE = 0xFE272727.toInt()
        private val WHITE = 0xFFFFFFFF.toInt()
        private val hudModuleIds = setOf("hud.player_info", "hud.inventory", "hud.cooldowns", "hud.potions", "hud.hotkeys")
        const val WIDTH = 280.0f
        const val HEIGHT = 464.0f
        private const val COLOR_PICKER_WIDTH = 296.0f
        private const val COLOR_PICKER_HEIGHT = 360.0f
        private const val COLOR_SWATCH_X = 206.0f
        private const val CONTENT_TOP = 67.0f
        private const val CONTENT_BOTTOM_PAD = 12.0f
    }
}
