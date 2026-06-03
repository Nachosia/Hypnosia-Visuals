package dev.hypnosia.ui.layout

import dev.hypnosia.HypnosiaClient
import dev.hypnosia.config.HypnosiaConfigProfiles
import dev.hypnosia.config.HypnosiaClientSettings
import dev.hypnosia.config.IconSettings
import dev.hypnosia.config.ThemeSettings
import dev.hypnosia.hud.HudModuleSettings
import dev.hypnosia.hud.ModuleHotkeys
import dev.hypnosia.hud.TargetHudSettings
import dev.hypnosia.hud.WatermarkSettings
import dev.hypnosia.license.AccountManager
import dev.hypnosia.license.AccountState
import dev.hypnosia.license.CloudConfigSummary
import dev.hypnosia.license.LicenseRole
import dev.hypnosia.license.CloudDeleteResult
import dev.hypnosia.license.CloudListResult
import dev.hypnosia.license.CloudLoadResult
import dev.hypnosia.license.CloudSaveResult
import dev.hypnosia.license.HypnosiaPaths
import dev.hypnosia.other.FriendsManager
import dev.hypnosia.other.StreamerModeSettings
import dev.hypnosia.ui.animation.FigmaAnimation
import dev.hypnosia.ui.animation.SpringFloat
import dev.hypnosia.ui.component.CategorySidebar
import dev.hypnosia.ui.component.HypnosiaCategory
import dev.hypnosia.ui.component.ModuleRow
import dev.hypnosia.ui.render.FigmaTextRenderer
import dev.hypnosia.ui.render.HypnosiaRenderUtils
import dev.hypnosia.ui.render.HypnosiaScissor
import dev.hypnosia.world.WorldVisualSettings
import dev.hypnosia.ui.profile.HypnosiaPlaytime
import dev.hypnosia.visual.AspectRatioSettings
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.util.Identifier
import org.joml.Matrix3x2f
import org.lwjgl.glfw.GLFW
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object HypnosiaMainLayout {
    private const val WINDOW_WIDTH = 698.0f
    private const val WINDOW_HEIGHT = 464.0f

    private const val CONTENT_X = 72.5f
    private const val CONTENT_Y = 66.5f
    private const val CONTENT_WIDTH = 604.0f
    private const val CONTENT_HEIGHT = 370.0f

    private const val GRID_PAD_X = 27.0f
    private const val GRID_PAD_Y = 21.0f
    private const val MODULE_GAP_X = 24.0f
    private const val MODULE_GAP_Y = 21.0f

    private const val SURFACE = 0xFF0D0D0D.toInt()
    private const val STROKE = 0xFF272727.toInt()
    private const val MUTED_ICON = 0xFF8E8E8E.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val MUTED_TEXT = 0xFFB8B8C2.toInt()

    private val modulesByCategory = mapOf(
        HypnosiaCategory.Visuals to listOf(
            ModuleEntry("visuals.aspect_ratio", HypnosiaCategory.Visuals, "Aspect Ratio", AspectRatioSettings.isEnabled(), hasSettings = true),
        ),
        HypnosiaCategory.World to listOf(
            ModuleEntry("world.fullbright", HypnosiaCategory.World, "Fullbright", moduleEnabled("world.fullbright", moduleEnabled("visuals.fullbright", true)), hasSettings = false),
            ModuleEntry("world.custom_fog", HypnosiaCategory.World, "Custom Fog", WorldVisualSettings.customFogEnabled(), hasSettings = true),
        ),
        HypnosiaCategory.Client to listOf(
            ModuleEntry("client.icons", HypnosiaCategory.Client, "Icons", moduleEnabled("client.icons", true)),
        ),
        HypnosiaCategory.Hud to listOf(
            ModuleEntry("hud.watermark", HypnosiaCategory.Hud, "Watermark", true),
            ModuleEntry("hud.hotbar", HypnosiaCategory.Hud, "HotBaR", HudModuleSettings.isEnabled(HudModuleSettings.Module.HOTBAR)),
            ModuleEntry("hud.armor", HypnosiaCategory.Hud, "Armor HUD", HudModuleSettings.isEnabled(HudModuleSettings.Module.ARMOR)),
            ModuleEntry("hud.target", HypnosiaCategory.Hud, "Target HUD", TargetHudSettings.isEnabled()),
            ModuleEntry("hud.player_info", HypnosiaCategory.Hud, "Player Info", HudModuleSettings.isEnabled(HudModuleSettings.Module.PLAYER_INFO)),
            ModuleEntry("hud.inventory", HypnosiaCategory.Hud, "Inventory HUD", HudModuleSettings.isEnabled(HudModuleSettings.Module.INVENTORY)),
            ModuleEntry("hud.cooldowns", HypnosiaCategory.Hud, "Cooldown HUD", HudModuleSettings.isEnabled(HudModuleSettings.Module.COOLDOWNS)),
            ModuleEntry("hud.potions", HypnosiaCategory.Hud, "Potions HUD", HudModuleSettings.isEnabled(HudModuleSettings.Module.POTIONS)),
            ModuleEntry("hud.hotkeys", HypnosiaCategory.Hud, "HotKey", HudModuleSettings.isEnabled(HudModuleSettings.Module.HOTKEYS)),
        ),
        HypnosiaCategory.Other to listOf(
            ModuleEntry("other.friends", HypnosiaCategory.Other, "Friends", moduleEnabled("other.friends", true)),
            ModuleEntry("other.streamer_mode", HypnosiaCategory.Other, "Streamer Mode", StreamerModeSettings.isEnabled(), hasSettings = true),
            ModuleEntry("other.discord_rpc", HypnosiaCategory.Other, "Discord RPC", moduleEnabled("other.discord_rpc", false)),
        ),
    )

    private object MenuStateStorage {
        private const val KEY_PREFIX = "menu."

        data class Snapshot(
            val page: String,
            val category: String,
            val selectedModuleId: String?,
            val offsetX: Float,
            val offsetY: Float,
        )

        fun load(): Snapshot {
            return runCatching {
                Snapshot(
                    page = HypnosiaClientSettings.string(KEY_PREFIX + "page", Page.Welcome.name),
                    category = HypnosiaClientSettings.string(KEY_PREFIX + "category", HypnosiaCategory.Home.name),
                    selectedModuleId = HypnosiaClientSettings.nullableString(KEY_PREFIX + "selectedModuleId"),
                    offsetX = HypnosiaClientSettings.string(KEY_PREFIX + "offsetX", "0").toFloatOrNull() ?: 0.0f,
                    offsetY = HypnosiaClientSettings.string(KEY_PREFIX + "offsetY", "0").toFloatOrNull() ?: 0.0f,
                )
            }.getOrDefault(Snapshot(Page.Welcome.name, HypnosiaCategory.Home.name, null, 0.0f, 0.0f))
        }

        fun save(page: String, category: String, selectedModuleId: String?, offsetX: Float, offsetY: Float) {
            runCatching {
                HypnosiaClientSettings.setAll(
                    mapOf(
                        KEY_PREFIX + "page" to page,
                        KEY_PREFIX + "category" to category,
                        KEY_PREFIX + "selectedModuleId" to selectedModuleId,
                        KEY_PREFIX + "offsetX" to offsetX.toInt().toString(),
                        KEY_PREFIX + "offsetY" to offsetY.toInt().toString(),
                    ),
                )
            }
        }
    }

    fun create(): FigmaRoot {
        HypnosiaConfigProfiles.bootstrap()
        ThemeSettings.writeLockedDefaults()
        return FigmaRoot(
            designWidth = WINDOW_WIDTH,
            designHeight = WINDOW_HEIGHT,
            child = ShellNode(),
            anchor = RootAnchor.Center,
            renderScale = 1.1f,
        )
    }

    private fun moduleEnabled(id: String, default: Boolean): Boolean =
        HypnosiaClientSettings.boolean("module.$id.enabled", default)

    private fun profileName(): String {
        val client = MinecraftClient.getInstance()
        val minecraftName = client.player?.gameProfile?.name ?: client.session.username
        val session = (AccountManager.state as? AccountState.Valid)?.session
        return StreamerModeSettings.displayName(session?.displayName?.takeIf { it.isNotBlank() } ?: minecraftName)
    }

    private fun profileRoleLine(): String {
        val session = (AccountManager.state as? AccountState.Valid)?.session ?: return "no acc"
        return session.roles.firstOrNull { it.name != "USER" }?.name ?: "USER"
    }

    private fun primaryRole(): LicenseRole? {
        val session = (AccountManager.state as? AccountState.Valid)?.session ?: return null
        return session.roles.firstOrNull { it.name != "USER" } ?: session.roles.firstOrNull()
    }

    private fun profileRoleGradient(): List<Int> {
        val session = (AccountManager.state as? AccountState.Valid)?.session ?: return emptyList()
        val role = primaryRole() ?: return emptyList()
        return parseGradientColors(session.roleGradients[role.name])
    }

    private fun parseGradientColors(gradientStr: String?): List<Int> {
        if (gradientStr.isNullOrBlank()) return emptyList()
        val hexRegex = Regex("#([A-Fa-f0-9]{6})")
        return hexRegex.findAll(gradientStr).map { match ->
            val hex = match.groupValues[1]
            0xFF000000.toInt() or hex.toInt(16)
        }.toList()
    }

    private class ShellNode : BaseUiNode(
        LayoutSpec(
            width = SizeMode.Fixed(WINDOW_WIDTH),
            height = SizeMode.Fixed(WINDOW_HEIGHT),
        ),
    ) {
        private val restoredMenuState = MenuStateStorage.load()
        private var page = parsePage(restoredMenuState.page)
        private var activeCategory = parseCategory(restoredMenuState.category)
        private var selectedModule: ModuleEntry? = null
        private var settingsOpen = false
        private var searchFocused = false
        private var searchQuery = ""
        private var menuOffsetX = restoredMenuState.offsetX.coerceIn(-900.0f, 900.0f)
        private var menuOffsetY = restoredMenuState.offsetY.coerceIn(-600.0f, 600.0f)
        private var draggingMenu = false
        private var slideDirection = 1.0f
        private val transition = SpringFloat(1.0f, stiffness = 420.0f, damping = 38.0f)
        private val accountButtonBackground = dev.hypnosia.ui.animation.SpringColor(SURFACE, stiffness = 380.0f, damping = 42.0f)
        private val accountButtonStroke = dev.hypnosia.ui.animation.SpringColor(STROKE, stiffness = 420.0f, damping = 42.0f)
        private val accountButtonIconTint = dev.hypnosia.ui.animation.SpringColor(MUTED_ICON, stiffness = 420.0f, damping = 42.0f)
        private val accountButtonStrokeWidth = SpringFloat(1.0f, stiffness = 520.0f, damping = 46.0f)
        private val accountButtonHoverProgress = SpringFloat(0.0f, stiffness = 520.0f, damping = 46.0f)

        private val sidebar = CategorySidebar(
            selectedCategory = {
                if (isAccountPage()) {
                    null
                } else {
                    activeCategory
                }
            },
            onCategorySelected = ::selectCategory,
        )

        private val categoryGrids = modulesByCategory.mapValues { (category, modules) ->
            ModuleGridNode(
                modules = modules,
                onSettings = ::setModuleSettingsOpen,
            ).also { it.category = category }
        }

        private val searchGrid = ModuleGridNode(
            modules = modulesByCategory
                .filterKeys { it != HypnosiaCategory.Home }
                .values
                .flatten(),
            queryProvider = { searchQuery },
            onSettings = { module, open ->
                if (open) {
                    activeCategory = module.category
                    page = Page.Category
                    restartTransition(1.0f)
                    saveMenuState()
                }
                setModuleSettingsOpen(module, open)
            },
        )

        private val homeContent = HomeContentNode(onConfigApplied = ::syncModuleEntriesFromSettings)
        private val welcomeContent = WelcomeContentNode()
        private val profileContent = ProfileCalendarContentNode()
        private val accountWelcomeContent = AccountWelcomeContentNode(
            onFinished = {
                if (page == Page.AccountWelcome) {
                    page = if (AccountManager.state is AccountState.Valid) {
                        Page.AccountManager
                    } else {
                        Page.AccountCreate
                    }
                    closeSettings()
                    restartTransition(1.0f)
                    saveMenuState()
                }
            },
        )
        private val accountManagerContent = AccountManagerContentNode()
        private val accountCreateContent = AccountCreateContentNode(
            onCreated = {
                accountWelcomeContent.restart()
                page = Page.AccountWelcome
                closeSettings()
                restartTransition(1.0f)
                saveMenuState()
            },
        )
        private val settingsDrawer = ModuleSettingsDrawerNode(
            module = { selectedModule },
            close = ::closeSettings,
        )

        private fun syncModuleEntriesFromSettings() {
            modulesByCategory.values.flatten().forEach { module ->
                module.enabled = when (module.id) {
                    "hud.hotbar" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.HOTBAR)
                    "hud.armor" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.ARMOR)
                    "hud.target" -> TargetHudSettings.isEnabled()
                    "hud.player_info" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.PLAYER_INFO)
                    "hud.inventory" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.INVENTORY)
                    "hud.cooldowns" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.COOLDOWNS)
                    "hud.potions" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.POTIONS)
                    "hud.hotkeys" -> HudModuleSettings.isEnabled(HudModuleSettings.Module.HOTKEYS)
                    else -> HypnosiaClientSettings.boolean("module.${module.id}.enabled", module.enabled)
                }
            }
            categoryGrids.values.forEach { it.rebuild() }
            searchGrid.rebuild()
        }

        init {
            restoreSelectedModule(restoredMenuState.selectedModuleId)
        }

        override fun measure(constraints: Constraints): Size {
            sidebar.measure(Constraints(WINDOW_WIDTH, WINDOW_HEIGHT))
            contentNodes().forEach { it.measure(Constraints(CONTENT_WIDTH, CONTENT_HEIGHT)) }
            settingsDrawer.measure(Constraints(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
            return constraints.constrain(Size(WINDOW_WIDTH, WINDOW_HEIGHT))
        }

        override fun layout(x: Float, y: Float, width: Float, height: Float) {
            val menuX = x + menuOffsetX
            val menuY = y + menuOffsetY
            super.layout(menuX, menuY, WINDOW_WIDTH, WINDOW_HEIGHT)
            sidebar.layout(menuX, menuY, CategorySidebar.WIDTH, CategorySidebar.HEIGHT)
            contentNodes().forEach { it.layout(menuX + CONTENT_X, menuY + CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT) }
            settingsDrawer.layout(menuX + WINDOW_WIDTH + 8.0f, menuY + CONTENT_Y, ModuleSettingsDrawerNode.WIDTH, ModuleSettingsDrawerNode.HEIGHT)
        }

        override fun render(context: DrawContext) {
            ThemeSettings.themedUi {
                renderBase(context)
                sidebar.render(context)
                renderAccountNavButton(context)
                renderTopBar(context)
                renderContent(context)
                if (settingsOpen) {
                    settingsDrawer.render(context)
                }
            }
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button != 0) {
                return false
            }

            if (settingsOpen && settingsDrawer.mouseClicked(mouseX, mouseY, button)) {
                return true
            }

            if (contains(mouseX, mouseY, bounds.x + 50.5f, bounds.y + 7.5f, 166.0f, 35.0f)) {
                page = Page.Search
                searchFocused = true
                closeSettings()
                restartTransition(1.0f)
                saveMenuState()
                return true
            }

            if (page != Page.Search && contains(mouseX, mouseY, bounds.x + 485.5f, bounds.y + 10.5f, 32.0f, 32.0f)) {
                page = Page.Profile
                searchFocused = false
                closeSettings()
                restartTransition(1.0f)
                saveMenuState()
                return true
            }

            if (contains(mouseX, mouseY, bounds.x + 552.5f, bounds.y + 7.5f, 137.0f, 35.0f)) {
                page = Page.Profile
                searchFocused = false
                closeSettings()
                restartTransition(1.0f)
                saveMenuState()
                return true
            }

            if (sidebar.mouseClicked(mouseX, mouseY, button)) {
                searchFocused = false
                return true
            }

            if (contains(mouseX, mouseY, bounds.x + ACCOUNT_BUTTON_X, bounds.y + ACCOUNT_BUTTON_Y, ACCOUNT_BUTTON_SIZE, ACCOUNT_BUTTON_SIZE)) {
                openAccountArea()
                return true
            }

            if (contains(mouseX, mouseY, bounds.x, bounds.y, WINDOW_WIDTH, 51.0f)) {
                draggingMenu = true
                return true
            }

            searchFocused = false
            return activeContent().mouseClicked(mouseX, mouseY, button)
        }

        override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
            val wasDraggingMenu = draggingMenu
            draggingMenu = false
            return if (wasDraggingMenu) {
                true
            } else if (settingsOpen && settingsDrawer.mouseReleased(mouseX, mouseY, button)) {
                true
            } else {
                activeContent().mouseReleased(mouseX, mouseY, button)
            }
        }

        override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, deltaX: Float, deltaY: Float): Boolean {
            if (button == 0 && draggingMenu) {
                menuOffsetX = (menuOffsetX + deltaX).coerceIn(-900.0f, 900.0f)
                menuOffsetY = (menuOffsetY + deltaY).coerceIn(-600.0f, 600.0f)
                saveMenuState()
                return true
            }
            return if (settingsOpen && settingsDrawer.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                true
            } else {
                activeContent().mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            }
        }

        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
            if (settingsOpen && settingsDrawer.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
            if (page == Page.Search && searchFocused) {
                return when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                    }
                    true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = ""
                    } else {
                        searchFocused = false
                    }
                    true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    searchFocused = false
                    true
                }
                else -> false
                }
            }

            return activeContent().keyPressed(keyCode, scanCode, modifiers)
        }

        override fun charTyped(chr: Char, modifiers: Int): Boolean {
            if (settingsOpen && settingsDrawer.charTyped(chr, modifiers)) {
                return true
            }
            if (page == Page.Search && searchFocused) {
                if (chr.isISOControl()) {
                    return false
                }
                if (searchQuery.length >= 32) {
                    return true
                }

                searchQuery += chr
                return true
            }

            return activeContent().charTyped(chr, modifiers)
        }

        override fun mouseScrolled(
            mouseX: Float,
            mouseY: Float,
            horizontalAmount: Float,
            verticalAmount: Float,
        ): Boolean {
            if (settingsOpen && settingsDrawer.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true
            }
            return activeContent().mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        private fun selectCategory(category: HypnosiaCategory) {
            if (category == activeCategory && page == Page.Category) {
                return
            }
            slideDirection = if (category.order >= activeCategory.order) 1.0f else -1.0f
            activeCategory = category
            page = if (category == HypnosiaCategory.Home) Page.Home else Page.Category
            closeSettings()
            restartTransition(slideDirection)
            saveMenuState()
        }

        private fun setModuleSettingsOpen(module: ModuleEntry, open: Boolean) {
            if (module.id == "client.theme") {
                closeSettings()
                ThemeSettings.writeLockedDefaults()
                return
            }
            modulesByCategory.values.flatten().forEach { entry ->
                if (entry != module) {
                    entry.settingsOpen = false
                }
            }
            module.settingsOpen = open
            selectedModule = if (open) module else null
            settingsOpen = open
            saveMenuState()
        }

        private fun closeSettings() {
            selectedModule?.settingsOpen = false
            selectedModule = null
            settingsOpen = false
            saveMenuState()
        }

        private fun openAccountArea() {
            searchFocused = false
            closeSettings()
            if (AccountManager.hasAccountKey()) {
                accountWelcomeContent.restart()
                page = Page.AccountWelcome
                AccountManager.refreshSessionAsync().thenAccept { result ->
                    if (result !is AccountState.Valid) {
                        MinecraftClient.getInstance().execute {
                            if (isAccountPage()) {
                                page = Page.AccountCreate
                                restartTransition(1.0f)
                                saveMenuState()
                            }
                        }
                    }
                }
            } else {
                page = Page.AccountCreate
            }
            restartTransition(1.0f)
            saveMenuState()
        }

        private fun saveMenuState() {
            MenuStateStorage.save(
                page = page.name,
                category = activeCategory.name,
                selectedModuleId = selectedModule?.takeIf { settingsOpen }?.id,
                offsetX = menuOffsetX,
                offsetY = menuOffsetY,
            )
        }

        private fun restoreSelectedModule(moduleId: String?) {
            val module = moduleId?.let(::moduleById) ?: return
            module.settingsOpen = true
            selectedModule = module
            settingsOpen = true
            activeCategory = module.category
            if (page == Page.Home || page == Page.Welcome || page == Page.Search) {
                page = Page.Category
            }
        }

        private fun moduleById(moduleId: String): ModuleEntry? {
            return modulesByCategory.values.asSequence().flatten().firstOrNull { it.id == moduleId }
        }

        private fun parsePage(value: String): Page {
            return runCatching { Page.valueOf(value) }.getOrDefault(Page.Welcome)
        }

        private fun parseCategory(value: String): HypnosiaCategory {
            return runCatching { HypnosiaCategory.valueOf(value) }.getOrDefault(HypnosiaCategory.Home)
        }

        private fun isAccountPage(): Boolean {
            return page == Page.AccountCreate || page == Page.AccountWelcome || page == Page.AccountManager
        }

        private fun restartTransition(direction: Float) {
            slideDirection = direction
            transition.snap(0.0f)
            transition.target = 1.0f
        }

        private fun renderBase(context: DrawContext) {
            HypnosiaRenderUtils.drawSdfShadowBox(
                context = context,
                x = bounds.x,
                y = bounds.y + 3.0f,
                width = WINDOW_WIDTH,
                height = WINDOW_HEIGHT,
                radius = 10.0f,
                shadowSpread = 0.0f,
                shadowBlur = 4.0f,
                color = 0x40000000,
            )
            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = bounds.x,
                y = bounds.y,
                width = WINDOW_WIDTH,
                height = WINDOW_HEIGHT,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
                role = ThemeSettings.ThemeRole.MAIN_PANEL,
            )
            rect(context, bounds.x - 0.5f, bounds.y + 50.5f, WINDOW_WIDTH, 1.0f, STROKE)
            rect(context, bounds.x + 50.5f, bounds.y + 51.5f, 1.0f, 412.0f, STROKE)
        }

        private fun renderAccountNavButton(context: DrawContext) {
            val x = bounds.x + ACCOUNT_BUTTON_X
            val y = bounds.y + ACCOUNT_BUTTON_Y
            val selected = isAccountPage()
            val hovered = UiInputState.contains(Rect(x, y, ACCOUNT_BUTTON_SIZE, ACCOUNT_BUTTON_SIZE))
            val seconds = UiInputState.frameSeconds

            accountButtonBackground.target = SURFACE
            accountButtonStroke.target = if (selected) WHITE else STROKE
            accountButtonIconTint.target = if (selected || hovered) WHITE else MUTED_ICON
            accountButtonStrokeWidth.target = if (selected) 2.0f else if (hovered) HOVER_STROKE_WIDTH else 1.0f
            accountButtonHoverProgress.target = if (hovered && !selected) 1.0f else 0.0f

            val hover = accountButtonHoverProgress.update(seconds)
            val visualX = x + HOVER_OFFSET_X * hover
            val visualY = y + HOVER_OFFSET_Y * hover
            val visualSize = ACCOUNT_BUTTON_SIZE + (HOVER_BUTTON_SIZE - ACCOUNT_BUTTON_SIZE) * hover
            val iconScale = 1.0f + (HOVER_BUTTON_SIZE / ACCOUNT_BUTTON_SIZE - 1.0f) * hover
            val iconSize = ACCOUNT_ICON_SIZE * iconScale

            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = visualX,
                y = visualY,
                width = visualSize,
                height = visualSize,
                radius = BUTTON_RADIUS + (HOVER_BUTTON_RADIUS - BUTTON_RADIUS) * hover,
                bgColor = accountButtonBackground.update(seconds),
                strokeColor = accountButtonStroke.update(seconds),
                strokeThickness = accountButtonStrokeWidth.update(seconds),
                role = ThemeSettings.ThemeRole.ICON_BUTTON,
            )
            icon(
                context = context,
                fileName = "account_cloud.png",
                x = visualX + (visualSize - iconSize) * 0.5f,
                y = visualY + (visualSize - iconSize) * 0.5f,
                width = iconSize,
                height = iconSize,
                tint = accountButtonIconTint.update(seconds),
            )
        }

        private fun renderTopBar(context: DrawContext) {
            val searchActive = page == Page.Search
            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = bounds.x + 50.5f,
                y = bounds.y + 7.5f,
                width = 166.0f,
                height = 35.0f,
                radius = if (searchActive) 7.0f else 10.0f,
                bgColor = if (searchActive) WHITE else SURFACE,
                strokeColor = if (searchActive) WHITE else STROKE,
                strokeThickness = if (searchActive) 2.0f else 1.0f,
                role = if (searchActive) ThemeSettings.ThemeRole.INPUT else ThemeSettings.ThemeRole.BUTTON,
            )
            icon(context, "search.png", bounds.x + 182.5f, bounds.y + 8.5f, 31.0f, 31.0f, if (searchActive) 0xFF0D0D0D.toInt() else WHITE)
            if (searchActive && searchQuery.isNotEmpty()) {
                drawTextBox(
                    context = context,
                    text = searchDisplayText(),
                    x = bounds.x + 58.5f,
                    y = bounds.y + 17.0f,
                    width = 117.0f,
                    height = 12.0f,
                    color = 0xFF0D0D0D.toInt(),
                    style = FigmaTextRenderer.Styles.Stats,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                )
            }

            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = bounds.x + 310.5f,
                y = bounds.y + 7.5f,
                width = 75.0f,
                height = 35.0f,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
                role = ThemeSettings.ThemeRole.BUTTON,
            )
            drawTextBox(
                context = context,
                text = chapterTitle(),
                x = bounds.x + 314.5f,
                y = bounds.y + 11.5f,
                width = 67.0f,
                height = 27.0f,
                color = WHITE,
                style = FigmaTextRenderer.Styles.Chapter,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )

            if (page != Page.Search) {
                icon(
                    context = context,
                    fileName = "black_hole.png",
                    x = bounds.x + 485.5f,
                    y = bounds.y + 10.5f,
                    width = 32.0f,
                    height = 32.0f,
                    tint = WHITE,
                    preserveTextureColor = true,
                )
            }

            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = bounds.x + 552.5f,
                y = bounds.y + 7.5f,
                width = 137.0f,
                height = 35.0f,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
                role = ThemeSettings.ThemeRole.BUTTON,
            )
            drawTextBox(
                context = context,
                text = profileName(),
                x = bounds.x + 556.5f,
                y = bounds.y + 11.5f,
                width = 129.0f,
                height = 12.0f,
                color = WHITE,
                style = FigmaTextRenderer.Styles.Stats,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            val roleGrad = profileRoleGradient()
            val time = (System.currentTimeMillis() % 1000000L) / 1000f
            if (roleGrad.size >= 2) {
                FigmaTextRenderer.drawGradientInBox(
                    context = context, text = profileRoleLine(),
                    x = bounds.x + 556.5f, y = bounds.y + 26.5f,
                    width = 129.0f, height = 12.0f,
                    color = WHITE, style = FigmaTextRenderer.Styles.Stats,
                    gradientColor1 = roleGrad[0], gradientColor2 = roleGrad[1], time = time,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                    fallbackColor = WHITE,
                )
            } else {
                drawTextBox(
                    context = context,
                    text = profileRoleLine(),
                    x = bounds.x + 556.5f,
                    y = bounds.y + 26.5f,
                    width = 129.0f,
                    height = 12.0f,
                    color = WHITE,
                    style = FigmaTextRenderer.Styles.Stats,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                )
            }
        }

        private fun renderContent(context: DrawContext) {
            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = bounds.x + CONTENT_X,
                y = bounds.y + CONTENT_Y,
                width = CONTENT_WIDTH,
                height = CONTENT_HEIGHT,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
                role = ThemeSettings.ThemeRole.MAIN_PANEL,
            )

            val progress = transition.update(UiInputState.frameSeconds).coerceIn(0.0f, 1.0f)
            val offsetX = (1.0f - progress) * slideDirection * 24.0f
            activeContent().alpha = progress

            context.matrices.pushMatrix()
            context.matrices.translate(offsetX, 0.0f)
            activeContent().render(context)
            context.matrices.popMatrix()
        }

        private fun activeContent(): FadeNode {
            return when (page) {
                Page.Welcome -> welcomeContent
                Page.Home -> homeContent
                Page.Search -> searchGrid
                Page.Profile -> profileContent
                Page.AccountCreate -> accountCreateContent
                Page.AccountWelcome -> accountWelcomeContent
                Page.AccountManager -> accountManagerContent
                Page.Category -> categoryGrids.getValue(activeCategory)
            }
        }

        private fun contentNodes(): List<UiNode> {
            return listOf(
                welcomeContent,
                homeContent,
                searchGrid,
                profileContent,
                accountCreateContent,
                accountWelcomeContent,
                accountManagerContent,
            ) + categoryGrids.values
        }

        private fun chapterTitle(): String {
            return when (page) {
                Page.Search -> "SEARCH"
                Page.Profile -> "PROFILE"
                Page.AccountCreate -> "ACCOUNT"
                Page.AccountWelcome -> "CLOUD"
                Page.AccountManager -> "ACCOUNT"
                Page.Category -> activeCategory.displayName.uppercase()
                Page.Home -> "HOME"
                Page.Welcome -> "-"
            }
        }

        private fun searchDisplayText(): String {
            val caret = if (searchFocused && ((System.nanoTime() / 500_000_000L) % 2L == 0L)) "_" else ""
            val maxWidth = 117.0f
            var text = searchQuery
            while (text.isNotEmpty() && FigmaTextRenderer.width(text + caret, FigmaTextRenderer.Styles.Stats) > maxWidth) {
                text = text.drop(1)
            }
            return text + caret
        }

        companion object {
            private const val ACCOUNT_BUTTON_X = 7.5f
            private const val ACCOUNT_BUTTON_Y = 274.5f
            private const val ACCOUNT_BUTTON_SIZE = 35.0f
            private const val ACCOUNT_ICON_SIZE = 30.0f
            private const val BUTTON_RADIUS = 10.0f
            private const val HOVER_BUTTON_SIZE = 38.255144f
            private const val HOVER_BUTTON_RADIUS = 10.4f
            private const val HOVER_OFFSET_X = 0.7774563f
            private const val HOVER_OFFSET_Y = -1.1275725f
            private const val HOVER_STROKE_WIDTH = 1.04f
        }
    }

    private enum class Page {
        Welcome,
        Home,
        Search,
        Profile,
        AccountCreate,
        AccountWelcome,
        AccountManager,
        Category,
    }

    private interface FadeNode : UiNode {
        var alpha: Float
    }

    private class ModuleGridNode(
        private val modules: List<ModuleEntry>,
        private val queryProvider: () -> String = { "" },
        private val onSettings: (ModuleEntry, Boolean) -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        var category: HypnosiaCategory? = null
        override var alpha: Float = 1.0f

        private val moduleRows = mutableListOf<ModuleRow>()
        private var appliedQuery: String? = null

        private val scroll = scrollColumn(
            width = SizeMode.Fill,
            height = SizeMode.Fill,
            padding = Insets(left = GRID_PAD_X, top = GRID_PAD_Y, right = GRID_PAD_X, bottom = GRID_PAD_Y),
            gap = MODULE_GAP_Y,
            alignment = Alignment.Start,
            scrollStep = ModuleRow.HEIGHT + MODULE_GAP_Y,
            scrollbar = true,
        )

        override fun measure(constraints: Constraints): Size {
            updateVisibleRows()
            scroll.measure(Constraints(CONTENT_WIDTH, CONTENT_HEIGHT))
            return constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))
        }

        override fun layout(x: Float, y: Float, width: Float, height: Float) {
            super<BaseUiNode>.layout(x, y, CONTENT_WIDTH, CONTENT_HEIGHT)
            scroll.layout(x, y, CONTENT_WIDTH, CONTENT_HEIGHT)
        }

        override fun render(context: DrawContext) {
            updateVisibleRows()
            moduleRows.forEach { it.alpha = alpha }
            scroll.render(context)
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            return scroll.mouseClicked(mouseX, mouseY, button)
        }

        override fun mouseScrolled(mouseX: Float, mouseY: Float, horizontalAmount: Float, verticalAmount: Float): Boolean {
            return scroll.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }

        fun rebuild() {
            appliedQuery = null
        }

        private fun updateVisibleRows() {
            val query = queryProvider().trim().lowercase()
            if (query == appliedQuery) {
                return
            }

            appliedQuery = query
            moduleRows.clear()

            val visibleModules = if (query.isBlank()) {
                modules
            } else {
                modules.filter { module ->
                    module.title.lowercase().contains(query) ||
                        module.category.displayName.lowercase().contains(query) ||
                        module.id.lowercase().contains(query)
                }
            }

            val rows = visibleModules.chunked(2).map { rowModules ->
                row(width = SizeMode.Hug, height = SizeMode.Fixed(ModuleRow.HEIGHT), gap = MODULE_GAP_X) {
                    rowModules.forEach { module ->
                        val moduleRow = ModuleRow(
                            title = module.title,
                            description = "",
                            iconPath = "plug_socket.png",
                            isActive = module.enabled,
                            isSettingsOpen = { module.settingsOpen },
                            settingsEnabled = module.hasSettings,
                            onActiveChanged = { enabled ->
                                module.enabled = enabled
                                when (module.id) {
                                    "hud.hotbar" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.HOTBAR, enabled)
                                    "hud.armor" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.ARMOR, enabled)
                                    "hud.target" -> TargetHudSettings.setEnabled(enabled)
                                    "hud.player_info" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.PLAYER_INFO, enabled)
                                    "hud.inventory" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.INVENTORY, enabled)
                                    "hud.cooldowns" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.COOLDOWNS, enabled)
                                    "hud.potions" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.POTIONS, enabled)
                                    "hud.hotkeys" -> HudModuleSettings.setEnabled(HudModuleSettings.Module.HOTKEYS, enabled)
                                    "world.fullbright" -> WorldVisualSettings.setFullbrightEnabled(enabled)
                                    "world.custom_fog" -> WorldVisualSettings.setCustomFogEnabled(enabled)
                                    "visuals.aspect_ratio" -> AspectRatioSettings.setEnabled(enabled)
                                    "other.friends" -> FriendsManager.setEnabled(enabled)
                                    "other.streamer_mode" -> StreamerModeSettings.setEnabled(enabled)
                                    else -> HypnosiaClientSettings.set("module.${module.id}.enabled", enabled.toString())
                                }
                            },
                            onSettingsChanged = { open -> onSettings(module, open) },
                        )
                        moduleRows += moduleRow
                        child(moduleRow)
                    }
                }
            }
            scroll.replaceChildren(rows)
        }
    }

    private class WelcomeContentNode : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))

        override fun render(context: DrawContext) {
            drawTextBox(
                context = context,
                text = "Welcome",
                x = bounds.x + 95.0f,
                y = bounds.y + 69.0f,
                width = 413.0f,
                height = 76.0f,
                color = withAlpha(WHITE, alpha),
                style = FigmaTextRenderer.Styles.WelcomeTitle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )
            icon(
                context = context,
                fileName = "black_hole.png",
                x = bounds.x + 269.0f,
                y = bounds.y + 152.0f,
                width = 64.0f,
                height = 64.0f,
                tint = withAlpha(WHITE, alpha),
                preserveTextureColor = true,
            )
            drawTextBox(
                context = context,
                text = "HYPNOSIA VISUALS",
                x = bounds.x + 95.0f,
                y = bounds.y + 225.0f,
                width = 413.0f,
                height = 76.0f,
                color = withAlpha(WHITE, alpha),
                style = FigmaTextRenderer.Styles.WelcomeTitle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )
        }
    }

    private class AccountCreateContentNode(
        private val onCreated: () -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f

        private var acceptedTerms = false
        private var creating = false
        private var statusText = ""
        private var statusColor = 0xFF8C8C93.toInt()

        private val titleStyle = FigmaTextRenderer.FigmaTextStyle(
            font = FigmaTextRenderer.Font.Title,
            size = 30.0f,
            lineHeight = 38.0f,
            baselineOffset = -8.0f,
        )
        private val bodyStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 16.0f, 19.0f, baselineOffset = 1.5f)
        private val buttonStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 18.0f, 22.0f, baselineOffset = 2.0f)
        private val smallStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 12.0f, 15.0f, baselineOffset = 1.0f)

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))

        override fun render(context: DrawContext) {
            val a = alpha
            drawAccountHeroTitle(context, bounds.x, bounds.y, "WELCOME CLOUD CFG SYSTEM", a, titleStyle)

            val buttonEnabled = acceptedTerms && !creating
            val buttonAlpha = if (buttonEnabled) 1.0f else 0.34f
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = bounds.x + 174.0f,
                y = bounds.y + 126.0f,
                width = 256.0f,
                height = 90.0f,
                radius = 32.0f,
                bgColor = withAlpha(0xFF080808.toInt(), a),
                strokeColor = withAlpha(if (buttonEnabled) 0xFF2B2B2F.toInt() else 0xFF1D1D20.toInt(), a),
                strokeThickness = 1.0f,
            )
            drawTextBox(
                context = context,
                text = if (creating) "Creating..." else "Create Account",
                x = bounds.x + 174.0f,
                y = bounds.y + 126.0f,
                width = 256.0f,
                height = 90.0f,
                color = withAlpha(if (buttonEnabled) WHITE else 0xFF8A8A90.toInt(), a * buttonAlpha),
                style = buttonStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )

            val checkX = bounds.x + 44.0f
            val checkY = bounds.y + 272.0f
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = checkX,
                y = checkY,
                width = 18.0f,
                height = 18.0f,
                radius = 2.0f,
                bgColor = withAlpha(if (acceptedTerms) WHITE else 0x00000000, a),
                strokeColor = withAlpha(if (acceptedTerms) WHITE else STROKE, a),
                strokeThickness = 1.0f,
            )
            if (acceptedTerms) {
                drawTextBox(
                    context = context,
                    text = "V",
                    x = checkX,
                    y = checkY - 1.0f,
                    width = 18.0f,
                    height = 18.0f,
                    color = withAlpha(SURFACE, a),
                    style = FigmaTextRenderer.Styles.Ui14,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
                )
            }
            drawTextBox(
                context = context,
                text = "I have read and agree to the Terms of Service",
                x = bounds.x + 68.0f,
                y = bounds.y + 272.0f,
                width = 330.0f,
                height = 18.0f,
                color = withAlpha(WHITE, a),
                style = bodyStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            drawTextBox(
                context = context,
                text = "*",
                x = bounds.x + 421.0f,
                y = bounds.y + 272.0f,
                width = 12.0f,
                height = 18.0f,
                color = withAlpha(0xFFFF2F93.toInt(), a),
                style = bodyStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )

            drawMultilineText(
                context = context,
                lines = listOf(
                    "Creating an account requires linking a unique device identifier (HWID) to your",
                    "profile for account protection, abuse prevention, and config system",
                    "functionality. Please review the Privacy Policy for more details.",
                ),
                x = bounds.x + 34.0f,
                y = bounds.y + 306.0f,
                width = 535.0f,
                lineHeight = 20.0f,
                color = withAlpha(0xFFE9E9EC.toInt(), a),
                style = bodyStyle,
                align = FigmaTextRenderer.HorizontalAlign.Center,
            )

            if (statusText.isNotBlank()) {
                drawTextBox(
                    context = context,
                    text = statusText,
                    x = bounds.x + 110.0f,
                    y = bounds.y + 236.0f,
                    width = 384.0f,
                    height = 16.0f,
                    color = withAlpha(statusColor, a),
                    style = smallStyle,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                )
            }
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button != 0) return false

            if (contains(mouseX, mouseY, bounds.x + 44.0f, bounds.y + 272.0f, 390.0f, 20.0f)) {
                acceptedTerms = !acceptedTerms
                statusText = ""
                return true
            }

            if (contains(mouseX, mouseY, bounds.x + 174.0f, bounds.y + 126.0f, 256.0f, 90.0f)) {
                if (!acceptedTerms) {
                    statusText = "Accept the Terms of Service first."
                    statusColor = 0xFFFFD166.toInt()
                    return true
                }
                if (!creating) {
                    createAccount()
                }
                return true
            }

            return contains(mouseX, mouseY, bounds.x, bounds.y, CONTENT_WIDTH, CONTENT_HEIGHT)
        }

        private fun createAccount() {
            creating = true
            statusText = "Connecting to Hypnosia Cloud..."
            statusColor = 0xFF8C8C93.toInt()
            AccountManager.createAsync().thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    creating = false
                    when (result) {
                        is AccountState.Valid -> {
                            statusText = "Account #${result.session.accountId} created."
                            statusColor = 0xFF68E673.toInt()
                            onCreated()
                        }
                        AccountState.InvalidResponse -> {
                            statusText = "Invalid server response."
                            statusColor = 0xFFFF6B6B.toInt()
                        }
                        AccountState.NoAccount, AccountState.NotChecked -> {
                            statusText = "Account was not created."
                            statusColor = 0xFFFF6B6B.toInt()
                        }
                        is AccountState.ServerRejected -> {
                            statusText = "Server rejected: ${result.reason}"
                            statusColor = 0xFFFF6B6B.toInt()
                        }
                        is AccountState.NetworkError -> {
                            statusText = "Network error: ${result.message.take(44)}"
                            statusColor = 0xFFFF6B6B.toInt()
                        }
                    }
                }
            }
        }
    }

    private class AccountWelcomeContentNode(
        private val onFinished: () -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f
        private var startedAtNanos = System.nanoTime()
        private var finished = false
        private val welcomeStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 20.0f, 23.0f, baselineOffset = 1.0f)

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))

        fun restart() {
            startedAtNanos = System.nanoTime()
            finished = false
        }

        override fun render(context: DrawContext) {
            val elapsed = ((System.nanoTime() - startedAtNanos) / 1_000_000_000.0f).coerceAtLeast(0.0f)
            if (!finished && elapsed >= 2.35f) {
                finished = true
                MinecraftClient.getInstance().execute(onFinished)
            }
            val move = smoothstep(0.35f, 1.35f, elapsed)
            val textIn = smoothstep(1.10f, 1.70f, elapsed)
            val lineOut = 1.0f - smoothstep(1.25f, 1.95f, elapsed)
            val a = alpha

            val cloudX = lerp(bounds.x + 165.0f, bounds.x + 214.0f, move)
            val cloudY = lerp(bounds.y + 92.0f, bounds.y + 145.0f, move)
            val cloudWidth = lerp(274.0f, 64.0f, move)
            val cloudHeight = cloudWidth * 0.59375f
            icon(context, "cloud_outline.png", cloudX, cloudY, cloudWidth, cloudHeight, withAlpha(WHITE, a))

            val lineAlpha = a * smoothstep(0.50f, 1.05f, elapsed) * lineOut
            if (lineAlpha > 0.01f) {
                val lineX = cloudX + cloudWidth + 11.0f
                val lineY = cloudY + cloudHeight * 0.56f
                val lineWidth = (bounds.x + 294.0f - lineX).coerceAtLeast(0.0f)
                HypnosiaRenderUtils.drawFigmaBox(
                    context = context,
                    x = lineX,
                    y = lineY,
                    width = lineWidth,
                    height = 3.0f,
                    radius = 1.5f,
                    bgColor = withAlpha(WHITE, lineAlpha),
                )
            }

            drawMultilineText(
                context = context,
                lines = listOf("Welcome", "Cloud"),
                x = bounds.x + 305.0f,
                y = bounds.y + 139.0f,
                width = 180.0f,
                lineHeight = 22.0f,
                color = withAlpha(WHITE, a * textIn),
                style = welcomeStyle,
                align = FigmaTextRenderer.HorizontalAlign.Left,
            )
        }
    }

    private class AccountManagerContentNode : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f
        private val titleStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 15.0f, 18.0f, baselineOffset = 1.0f)
        private val textStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 16.0f, 16.0f, baselineOffset = 1.0f)
        private val bodyStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 15.0f, 15.0f, baselineOffset = 1.0f)

        private enum class AccountField { Name, Contact, CloudKey }
        private var focusedAccountField: AccountField? = null
        private var nameInput = ""
        private var contactInput = ""
        private var cloudKeyInput = ""
        private var nameSaving = false
        private var contactSaving = false
        private var cloudKeyLoading = false
        private var localConfigs: List<String> = emptyList()
        private var cloudConfigs: List<CloudConfigSummary> = emptyList()
        private var cloudUsed = 0
        private var cloudLimit = 3
        private var cloudLoading = false
        private var cloudLastRefreshMs = 0L
        private var localLastRefreshMs = 0L
        private var statusMessage = ""

        private companion object {
            private const val MAX_ACCOUNT_CONFIG_ROWS = 4
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button != 0) return false
            val x = bounds.x + 14.0f
            val y = bounds.y + 14.0f
            val rightX = bounds.x + 309.0f
            if (contains(mouseX, mouseY, x + 52.0f, y + 159.0f, 218.0f, 18.0f)) {
                focusedAccountField = AccountField.Name
                return true
            }
            if (contains(mouseX, mouseY, x + 69.0f, y + 180.0f, 201.0f, 18.0f)) {
                focusedAccountField = AccountField.Contact
                return true
            }
            if (contains(mouseX, mouseY, rightX + 244.0f, y + 24.0f, 22.0f, 22.0f)) {
                pasteCloudKeyFromClipboard()
                focusedAccountField = AccountField.CloudKey
                return true
            }
            if (contains(mouseX, mouseY, rightX + 5.0f, y + 23.0f, 267.0f, 24.0f)) {
                focusedAccountField = AccountField.CloudKey
                return true
            }
            localConfigs.take(MAX_ACCOUNT_CONFIG_ROWS).forEachIndexed { index, name ->
                val rowY = y + 56.0f + index * 28.0f
                if (contains(mouseX, mouseY, rightX + 238.0f, rowY, 22.0f, 22.0f)) {
                    focusedAccountField = null
                    uploadLocalConfig(name)
                    return true
                }
            }
            cloudConfigs.take(MAX_ACCOUNT_CONFIG_ROWS).forEachIndexed { index, config ->
                val rowY = y + 206.0f + index * 28.0f
                if (contains(mouseX, mouseY, rightX + 215.0f, rowY, 22.0f, 22.0f)) {
                    focusedAccountField = null
                    copyCloudConfigKey(config)
                    return true
                }
                if (contains(mouseX, mouseY, rightX + 237.0f, rowY - 1.0f, 24.0f, 24.0f)) {
                    focusedAccountField = null
                    deleteCloudConfig(config)
                    return true
                }
            }
            focusedAccountField = null
            return false
        }

        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
            val field = focusedAccountField ?: return false
            return when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    when (field) {
                        AccountField.Name -> if (nameInput.isNotEmpty()) nameInput = nameInput.dropLast(1)
                        AccountField.Contact -> if (contactInput.isNotEmpty()) contactInput = contactInput.dropLast(1)
                        AccountField.CloudKey -> if (cloudKeyInput.isNotEmpty()) cloudKeyInput = cloudKeyInput.dropLast(1)
                    }
                    true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    when (field) {
                        AccountField.Name -> saveNameToServer()
                        AccountField.Contact -> saveContactToServer()
                        AccountField.CloudKey -> loadCloudConfigByInput()
                    }
                    true
                }
                GLFW.GLFW_KEY_V -> {
                    if (field == AccountField.CloudKey && (modifiers and GLFW.GLFW_MOD_CONTROL) != 0) {
                        pasteCloudKeyFromClipboard()
                        true
                    } else {
                        false
                    }
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    when (field) {
                        AccountField.Name -> if (nameInput.isNotEmpty()) nameInput = "" else focusedAccountField = null
                        AccountField.Contact -> if (contactInput.isNotEmpty()) contactInput = "" else focusedAccountField = null
                        AccountField.CloudKey -> if (cloudKeyInput.isNotEmpty()) cloudKeyInput = "" else focusedAccountField = null
                    }
                    true
                }
                else -> false
            }
        }

        override fun charTyped(chr: Char, modifiers: Int): Boolean {
            val field = focusedAccountField ?: return false
            if (chr.isISOControl()) return false
            when (field) {
                AccountField.Name -> if (nameInput.length < 32) nameInput += chr
                AccountField.Contact -> if (contactInput.length < 96) contactInput += chr
                AccountField.CloudKey -> if (chr.isLetterOrDigit() && cloudKeyInput.length < 8) cloudKeyInput += chr.uppercaseChar()
            }
            return true
        }

        private fun saveNameToServer() {
            val name = nameInput.trim().takeIf { it.isNotBlank() } ?: return
            nameSaving = true
            focusedAccountField = null
            AccountManager.setNameAsync(name).thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    nameSaving = false
                    if (result is AccountState.Valid) nameInput = ""
                }
            }
        }

        private fun saveContactToServer() {
            val contact = contactInput.trim().takeIf { it.isNotBlank() } ?: return
            contactSaving = true
            focusedAccountField = null
            AccountManager.setContactAsync(contact).thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    contactSaving = false
                    if (result is AccountState.Valid) contactInput = ""
                }
            }
        }

        private fun pasteCloudKeyFromClipboard() {
            val clipboard = runCatching { MinecraftClient.getInstance().keyboard.clipboard }.getOrDefault("")
            val key = clipboard.filter { it.isLetterOrDigit() }.take(8).uppercase()
            if (key.isNotBlank()) {
                cloudKeyInput = key
            }
        }

        private fun loadCloudConfigByInput() {
            val key = cloudKeyInput.trim().uppercase()
            if (key.length != 8) {
                statusMessage = "Cloud key must be 8 chars"
                return
            }
            cloudKeyLoading = true
            focusedAccountField = null
            statusMessage = "Loading cloud key $key..."
            AccountManager.loadCloudConfigAsync(key, null).thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    cloudKeyLoading = false
                    when (result) {
                        is CloudLoadResult.Loaded -> {
                            cloudKeyInput = ""
                            localLastRefreshMs = 0L
                            statusMessage = "Loaded ${result.fileName}"
                        }
                        is CloudLoadResult.Error -> statusMessage = "Load failed: ${result.reason}"
                    }
                }
            }
        }

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))

        override fun render(context: DrawContext) {
            val a = alpha
            val session = (AccountManager.state as? AccountState.Valid)?.session
            refreshLocalConfigs()
            refreshCloudConfigs(session)
            val x = bounds.x + 14.0f
            val y = bounds.y + 14.0f
            val panelWidth = 279.0f
            val rightX = bounds.x + 309.0f

            drawSmallAccountPanel(context, x, y, panelWidth, 121.0f, "Account info", a) {
                val date = session?.createdAt?.substringBefore('T')?.ifBlank { "unknown" } ?: "No account"
                val roles = session?.roles?.joinToString(" ") { it.name } ?: "no acc"
                drawAccountLine(context, x + 5.0f, y + 26.0f, "ID: ${session?.accountId ?: "-"}", a)
                drawAccountLine(context, x + 5.0f, y + 49.0f, "Account Created: $date", a, bodyStyle)
                drawWrappedAccountText(context, x + 5.0f, y + 72.0f, panelWidth - 12.0f, "Roles: $roles", a)
            }

            drawSmallAccountPanel(context, x, y + 136.0f, panelWidth, 204.0f, "Account", a) {
                drawAccountField(context, x + 5.0f, y + 160.0f, "Name", x + 52.0f, y + 159.0f, 218.0f, "Enter to save name", a,
                    value = nameInput, savedValue = session?.displayName, focused = focusedAccountField == AccountField.Name, saving = nameSaving)
                drawAccountField(context, x + 5.0f, y + 181.0f, "Contact", x + 69.0f, y + 180.0f, 201.0f, "Enter to save Contact", a,
                    value = contactInput, savedValue = session?.contact, focused = focusedAccountField == AccountField.Contact, saving = contactSaving)
                drawMultilineText(
                    context = context,
                    lines = listOf(
                        "Without a linked TG or DC account,",
                        "account recovery will be impossible.",
                        "",
                        "For account transfer or recovery,",
                        "contact:",
                        "Discord: nachosia",
                        "Telegram: @Hypnosia_NSXS",
                    ),
                    x = x + 5.0f,
                    y = y + 204.0f,
                    width = 265.0f,
                    lineHeight = 14.0f,
                    color = withAlpha(WHITE, a),
                    style = bodyStyle,
                    align = FigmaTextRenderer.HorizontalAlign.Left,
                )
            }

            drawSmallAccountPanel(context, rightX, y, panelWidth, 163.0f, "Local configs", a) {
                drawCloudKeyInput(context, rightX + 5.0f, y + 23.0f, 267.0f, a)
                val rows = localConfigs.take(MAX_ACCOUNT_CONFIG_ROWS)
                if (rows.isEmpty()) {
                    drawPillField(context, rightX + 5.0f, y + 56.0f, 267.0f, "No local configs", a, muted = true)
                } else {
                    rows.forEachIndexed { index, name ->
                        val rowY = y + 56.0f + index * 28.0f
                        drawPillField(context, rightX + 5.0f, rowY, 267.0f, compact(name, 22), a)
                        icon(context, "cloud_config_key.png", rightX + 238.0f, rowY, 22.0f, 22.0f, withAlpha(WHITE, a))
                    }
                }
            }

            drawSmallAccountPanel(context, rightX, y + 177.0f, panelWidth, 163.0f, "Cloud configs", a) {
                val slots = if (session == null) "0/3" else "$cloudUsed/$cloudLimit"
                drawTextBox(context, slots, rightX + 154.0f, y + 178.0f, 118.0f, 16.0f, withAlpha(0xFF74747B.toInt(), a), titleStyle, FigmaTextRenderer.HorizontalAlign.Right, FigmaTextRenderer.VerticalAlign.Top)
                val rows = cloudConfigs.take(MAX_ACCOUNT_CONFIG_ROWS)
                if (session == null) {
                    drawPillField(context, rightX + 5.0f, y + 206.0f, 267.0f, "No account", a, muted = true)
                } else if (cloudLoading && rows.isEmpty()) {
                    drawPillField(context, rightX + 5.0f, y + 206.0f, 267.0f, "Loading...", a, muted = true)
                } else if (rows.isEmpty()) {
                    drawPillField(context, rightX + 5.0f, y + 206.0f, 267.0f, "No cloud configs", a, muted = true)
                } else {
                    rows.forEachIndexed { index, config ->
                        val rowY = y + 206.0f + index * 28.0f
                        drawPillField(context, rightX + 5.0f, rowY, 267.0f, cloudLabel(config), a)
                        icon(context, "folder_select_cloud.png", rightX + 215.0f, rowY, 22.0f, 22.0f, withAlpha(WHITE, a))
                        icon(context, "interface_trash_empty.png", rightX + 237.0f, rowY - 1.0f, 24.0f, 24.0f, withAlpha(WHITE, a))
                    }
                }
            }
            if (statusMessage.isNotBlank()) {
                drawTextBox(
                    context = context,
                    text = compact(statusMessage, 70),
                    x = rightX,
                    y = y + 346.0f,
                    width = panelWidth,
                    height = 16.0f,
                    color = withAlpha(0xFF929292.toInt(), a),
                    style = bodyStyle,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                )
            }
        }

        private fun refreshLocalConfigs() {
            val now = System.currentTimeMillis()
            if (now - localLastRefreshMs < 1000L) return
            localLastRefreshMs = now
            val dir = HypnosiaPaths.configsDir
            localConfigs = runCatching {
                Files.list(dir).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .map { it.fileName.toString() }
                        .filter { it.endsWith(".json", ignoreCase = true) }
                        .map { it.removeSuffix(".json") }
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList()
                }
            }.getOrDefault(emptyList())
        }

        private fun refreshCloudConfigs(session: dev.hypnosia.license.AccountSession?) {
            if (session == null) {
                cloudConfigs = emptyList()
                cloudUsed = 0
                cloudLimit = 3
                cloudLoading = false
                cloudLastRefreshMs = 0L
                return
            }
            val now = System.currentTimeMillis()
            if (cloudLoading || now - cloudLastRefreshMs < 5000L) return
            cloudLoading = true
            cloudLastRefreshMs = now
            AccountManager.listCloudConfigsAsync().thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    cloudLoading = false
                    when (result) {
                        is CloudListResult.Listed -> {
                            cloudConfigs = result.configs
                            cloudUsed = result.used
                            cloudLimit = result.limit
                            if (statusMessage.startsWith("Cloud list:")) statusMessage = ""
                        }
                        is CloudListResult.Error -> statusMessage = "Cloud list: ${result.reason}"
                    }
                }
            }
        }

        private fun uploadLocalConfig(name: String) {
            statusMessage = "Uploading $name..."
            AccountManager.saveCloudConfigAsync(name).thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    when (result) {
                        is CloudSaveResult.Saved -> {
                            cloudUsed = result.used
                            cloudLimit = result.limit
                            cloudLastRefreshMs = 0L
                            statusMessage = "Uploaded $name: ${result.configKey}"
                        }
                        is CloudSaveResult.Error -> statusMessage = "Upload failed: ${result.reason}"
                    }
                }
            }
        }

        private fun loadCloudConfig(config: CloudConfigSummary) {
            statusMessage = "Loading ${config.name}..."
            AccountManager.loadCloudConfigAsync(config.configKey, config.name).thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    when (result) {
                        is CloudLoadResult.Loaded -> {
                            localLastRefreshMs = 0L
                            statusMessage = "Loaded ${result.fileName}"
                        }
                        is CloudLoadResult.Error -> statusMessage = "Load failed: ${result.reason}"
                    }
                }
            }
        }

        private fun copyCloudConfigKey(config: CloudConfigSummary) {
            MinecraftClient.getInstance().keyboard.clipboard = config.configKey
            statusMessage = "Copied ${config.configKey}"
        }

        private fun deleteCloudConfig(config: CloudConfigSummary) {
            statusMessage = "Deleting ${config.name}..."
            AccountManager.deleteCloudConfigAsync(config.configKey).thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    when (result) {
                        is CloudDeleteResult.Deleted -> {
                            cloudConfigs = cloudConfigs.filterNot { it.configKey == result.configKey }
                            cloudUsed = (cloudUsed - 1).coerceAtLeast(0)
                            cloudLastRefreshMs = 0L
                            statusMessage = "Deleted ${config.name}"
                        }
                        is CloudDeleteResult.Error -> statusMessage = "Delete failed: ${result.reason}"
                    }
                }
            }
        }

        private fun cloudLabel(config: CloudConfigSummary): String {
            val cleanName = config.name.replace(Regex("""\s*\[(true|false)]$"""), "")
            val name = compact(cleanName, 12)
            val typeLabel = config.configType?.takeIf { it.isNotBlank() }
            val suffix = if (typeLabel != null) " [$typeLabel]" else ""
            return compact("$name$suffix  ${config.configKey}", 28)
        }

        private fun compact(text: String, maxChars: Int): String {
            if (text.length <= maxChars) return text
            return text.take((maxChars - 3).coerceAtLeast(1)) + "..."
        }

        private inline fun drawSmallAccountPanel(
            context: DrawContext,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            title: String,
            alpha: Float,
            body: () -> Unit,
        ) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, width, height, 10.0f, withAlpha(0xFF0D0D0D.toInt(), alpha), withAlpha(0xFF272727.toInt(), alpha), 1.0f, ThemeSettings.ThemeRole.CARD)
            HypnosiaRenderUtils.drawThemedBox(context, x, y, width, 20.0f, 10.0f, withAlpha(0xFF191919.toInt(), alpha), role = ThemeSettings.ThemeRole.HEADER)
            HypnosiaRenderUtils.drawThemedBox(context, x, y + 10.0f, width, 10.0f, 0.0f, withAlpha(0xFF191919.toInt(), alpha), role = ThemeSettings.ThemeRole.HEADER)
            drawTextBox(context, title, x + 5.0f, y + 1.0f, width - 10.0f, 16.0f, withAlpha(WHITE, alpha), titleStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            body()
        }

        private fun drawAccountLine(
            context: DrawContext,
            x: Float,
            y: Float,
            text: String,
            alpha: Float,
            style: FigmaTextRenderer.FigmaTextStyle = textStyle,
        ) {
            drawTextBox(context, text, x, y, 267.0f, 16.0f, withAlpha(WHITE, alpha), style, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
        }

        private fun drawAccountField(
            context: DrawContext,
            labelX: Float,
            labelY: Float,
            label: String,
            fieldX: Float,
            fieldY: Float,
            fieldWidth: Float,
            placeholder: String,
            alpha: Float,
            value: String = "",
            savedValue: String? = null,
            focused: Boolean = false,
            saving: Boolean = false,
        ) {
            drawTextBox(context, label, labelX, labelY, 62.0f, 16.0f, withAlpha(WHITE, alpha), textStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            val strokeColor = when {
                saving -> withAlpha(0xFF4E4E4E.toInt(), alpha)
                focused -> withAlpha(WHITE, alpha)
                else -> withAlpha(0xFF272727.toInt(), alpha)
            }
            HypnosiaRenderUtils.drawThemedBox(context, fieldX, fieldY, fieldWidth, 18.0f, 5.0f, withAlpha(0xFF0D0D0D.toInt(), alpha), strokeColor, if (focused) 1.2f else 1.0f, ThemeSettings.ThemeRole.INPUT)
            val saved = savedValue?.takeIf { it.isNotBlank() }
            val displayText = when {
                saving -> "Saving..."
                value.isNotEmpty() -> value + if (focused && (System.nanoTime() / 500_000_000L) % 2L == 0L) "_" else ""
                !focused && saved != null -> compact(saved, 24)
                else -> placeholder
            }
            val textColor = if ((value.isNotEmpty() || (!focused && saved != null)) && !saving) withAlpha(WHITE, alpha) else withAlpha(0xFF4E4E4E.toInt(), alpha)
            drawTextBox(context, displayText, fieldX + 3.0f, fieldY - 1.0f, fieldWidth - 6.0f, 16.0f, textColor, textStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
        }

        private fun drawPillField(context: DrawContext, x: Float, y: Float, width: Float, text: String, alpha: Float, muted: Boolean = false) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, width, 24.0f, 13.0f, withAlpha(0xFF0D0D0D.toInt(), alpha), withAlpha(0xFF272727.toInt(), alpha), 1.0f, ThemeSettings.ThemeRole.INPUT)
            drawTextBox(
                context = context,
                text = text,
                x = x + 10.0f,
                y = y + 1.0f,
                width = width - 20.0f,
                height = 20.0f,
                color = withAlpha(if (muted) 0xFF4E4E4E.toInt() else WHITE, alpha),
                style = textStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
        }

        private fun drawCloudKeyInput(context: DrawContext, x: Float, y: Float, width: Float, alpha: Float) {
            val focused = focusedAccountField == AccountField.CloudKey
            val stroke = if (focused) WHITE else 0xFF272727.toInt()
            HypnosiaRenderUtils.drawThemedBox(context, x, y, width, 24.0f, 13.0f, withAlpha(0xFF0D0D0D.toInt(), alpha), withAlpha(stroke, alpha), if (focused) 1.2f else 1.0f, ThemeSettings.ThemeRole.INPUT)
            val caret = if (focused && !cloudKeyLoading && (System.nanoTime() / 500_000_000L) % 2L == 0L) "_" else ""
            val text = when {
                cloudKeyLoading -> "Loading..."
                cloudKeyInput.isNotBlank() -> cloudKeyInput + caret
                else -> "Upload config : Cloud Key"
            }
            val color = if (cloudKeyInput.isNotBlank() || cloudKeyLoading) WHITE else 0xFF4E4E4E.toInt()
            drawTextBox(
                context = context,
                text = text,
                x = x + 10.0f,
                y = y + 1.0f,
                width = width - 42.0f,
                height = 20.0f,
                color = withAlpha(color, alpha),
                style = textStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            icon(context, "clipboard.png", x + width - 27.0f, y + 2.0f, 20.0f, 20.0f, withAlpha(WHITE, alpha))
        }

        private fun drawWrappedAccountText(context: DrawContext, x: Float, y: Float, width: Float, text: String, alpha: Float) {
            val lines = mutableListOf<String>()
            var current = ""
            text.split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (FigmaTextRenderer.width(candidate, bodyStyle) <= width || current.isEmpty()) {
                    current = candidate
                } else {
                    lines += current
                    current = word
                }
            }
            if (current.isNotEmpty()) lines += current
            drawMultilineText(
                context = context,
                lines = lines.take(2),
                x = x,
                y = y,
                width = width,
                lineHeight = 14.0f,
                color = withAlpha(WHITE, alpha),
                style = bodyStyle,
                align = FigmaTextRenderer.HorizontalAlign.Left,
            )
        }
    }

    private class ProfileCalendarContentNode : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f

        private val labelStyle = FigmaTextRenderer.FigmaTextStyle(
            font = FigmaTextRenderer.Font.Main,
            size = 10.0f,
            lineHeight = 18.0f,
            letterSpacing = 1.4f,
            baselineOffset = 1.0f,
        )
        private val subtitleStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 11.0f, 18.0f, baselineOffset = 1.0f)
        private val calendarTitleStyle = FigmaTextRenderer.FigmaTextStyle(
            font = FigmaTextRenderer.Font.Main,
            size = 11.0f,
            lineHeight = 16.0f,
            letterSpacing = 0.88f,
            baselineOffset = 1.0f,
        )
        private val tinyStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 9.0f, 13.0f, baselineOffset = 1.0f)
        private val statValueStyle = FigmaTextRenderer.FigmaTextStyle(
            font = FigmaTextRenderer.Font.Main,
            size = 16.0f,
            lineHeight = 20.0f,
            letterSpacing = -0.16f,
            baselineOffset = 0.5f,
        )
        private val statLabelStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 10.5f, 10.0f, baselineOffset = 0.5f)
        private val graphTitleStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 11.0f, 13.0f, baselineOffset = 0.5f)
        private val graphSmallStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 9.5f, 12.0f, baselineOffset = 0.5f)
        private val graphTinyStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 8.5f, 10.0f, baselineOffset = 0.5f)
        private val profileNameStyle = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 12.0f, 14.0f, baselineOffset = 1.0f)
        private val profileRoleStyle = FigmaTextRenderer.FigmaTextStyle(
            font = FigmaTextRenderer.Font.Main,
            size = 10.0f,
            lineHeight = 12.0f,
            letterSpacing = 0.8f,
            baselineOffset = 1.0f,
        )

        private data class ModelTuning(
            val yaw: Float = -32.56f,
            val phi: Float = -17.95f,
            val x: Float = 38.0f,
            val y: Float = 42.0f,
            val scale: Float = 82.0f,
        )

        private val modelTuning = ModelTuning()
        private var cachedPlayerModel: PlayerEntityModel? = null

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))

        override fun render(context: DrawContext) {
            val a = alpha
            val playtime = HypnosiaPlaytime.snapshot()
            drawTextBox(
                context = context,
                text = "PROFILE ACTIVITY",
                x = bounds.x + 17.0f,
                y = bounds.y + 13.0f,
                width = 210.0f,
                height = 18.0f,
                color = withAlpha(0xFF7C7C82.toInt(), a),
                style = labelStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            drawTextBox(
                context = context,
                text = "Each day square has a circle: bigger circle = more activity that day.",
                x = bounds.x + 17.0f,
                y = bounds.y + 33.0f,
                width = 370.0f,
                height = 18.0f,
                color = withAlpha(0xFF8C8C92.toInt(), a),
                style = subtitleStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )

            drawSummaryCard(context, bounds.x + 391.0f, bounds.y + 17.0f, 0xFFFF2F93.toInt(), formatTotalPlaytime(playtime.totalSeconds), "time total", a)
            drawSummaryCard(context, bounds.x + 455.0f, bounds.y + 17.0f, 0xFF68E673.toInt(), playtime.opensToday.toString(), "opens today", a)
            drawSummaryCard(context, bounds.x + 519.0f, bounds.y + 17.0f, 0xFF75CFFF.toInt(), "${playtime.streakDays}d", "login streak", a)

            drawCalendarPanel(context, bounds.x + 17.0f, bounds.y + 58.0f, a, playtime)
            drawProfileSkinPanel(context, bounds.x + 389.0f, bounds.y + 93.0f, a)
            drawSevenDayGraph(context, bounds.x + 17.0f, bounds.y + 310.0f, a, playtime)
        }

        private fun drawSummaryCard(context: DrawContext, x: Float, y: Float, dotColor: Int, value: String, label: String, alpha: Float) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 56.0f, 64.0f, 8.0f, withAlpha(0xFF0D0D0E.toInt(), alpha), withAlpha(0xFF26262A.toInt(), alpha), 1.0f, ThemeSettings.ThemeRole.CARD)
            HypnosiaRenderUtils.drawFigmaBox(context, x + 7.0f, y + 9.0f, 8.0f, 8.0f, 4.0f, withAlpha(dotColor, alpha))
            drawTextBox(
                context = context,
                text = value,
                x = x + 7.0f,
                y = y + 18.0f,
                width = 42.0f,
                height = 20.0f,
                color = withAlpha(0xFFF1F1F2.toInt(), alpha),
                style = statValueStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            drawSummaryLabel(context, x + 5.0f, y + 40.0f, label, alpha)
        }

        private fun drawSummaryLabel(context: DrawContext, x: Float, y: Float, label: String, alpha: Float) {
            label.split(' ')
                .filter { it.isNotBlank() }
                .forEachIndexed { index, word ->
                    drawTextBox(
                        context = context,
                        text = word,
                        x = x,
                        y = y + index * 10.0f,
                        width = 48.0f,
                        height = 10.0f,
                        color = withAlpha(0xFF8A8A90.toInt(), alpha),
                        style = statLabelStyle,
                        horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                        verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                    )
                }
        }

        private fun drawCalendarPanel(
            context: DrawContext,
            x: Float,
            y: Float,
            alpha: Float,
            playtime: HypnosiaPlaytime.Snapshot,
        ) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 354.0f, 248.0f, 9.0f, withAlpha(SURFACE, alpha), withAlpha(0xFF242428.toInt(), alpha), 1.0f, ThemeSettings.ThemeRole.CARD)
            drawTextBox(context, monthTitle(playtime), x + 15.0f, y + 13.0f, 110.0f, 16.0f, withAlpha(0xFFD6D6D8.toInt(), alpha), calendarTitleStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            drawTextBox(context, "less", x + 238.0f, y + 13.0f, 28.0f, 12.0f, withAlpha(0xFF77777D.toInt(), alpha), tinyStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            drawLegendDot(context, x + 270.5f, y + 16.5f, 5.0f, 0.18f, alpha)
            drawLegendDot(context, x + 281.0f, y + 14.0f, 10.0f, 0.52f, alpha)
            drawLegendDot(context, x + 295.5f, y + 10.5f, 17.0f, 1.0f, alpha)
            drawTextBox(context, "more", x + 318.0f, y + 13.0f, 28.0f, 12.0f, withAlpha(0xFF77777D.toInt(), alpha), tinyStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)

            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                drawTextBox(context, label, x + 17.0f + index * 45.0f, y + 42.0f, 28.0f, 14.0f, withAlpha(0xFF75757B.toInt(), alpha), tinyStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            }

            monthCircleSizes(playtime).forEachIndexed { index, size ->
                val col = index % 7
                val row = index / 7
                val cellX = x + 15.0f + col * 45.0f
                val cellY = y + 61.0f + row * 34.0f
                HypnosiaRenderUtils.drawFigmaBox(context, cellX, cellY, 30.0f, 30.0f, 6.0f, withAlpha(0xFF080809.toInt(), alpha), withAlpha(0xFF25252A.toInt(), alpha), 1.0f)
                if (size > 0.0f) {
                    val activity = (size / 26.0f).coerceIn(0.0f, 1.0f)
                    val color = FigmaAnimation.lerpArgb(0xFF2E2E35.toInt(), 0xFFFF2F93.toInt(), activity)
                    HypnosiaRenderUtils.drawFigmaBox(
                        context = context,
                        x = cellX + (30.0f - size) * 0.5f,
                        y = cellY + (30.0f - size) * 0.5f,
                        width = size,
                        height = size,
                        radius = size * 0.5f,
                        bgColor = withAlpha(color, alpha),
                    )
                }
            }

            drawTextBox(
                context = context,
                text = "Local data: playtime is stored only on this Minecraft instance.",
                x = x + 8.0f,
                y = y + 231.0f,
                width = 300.0f,
                height = 14.0f,
                color = withAlpha(0xFF6E6E74.toInt(), alpha),
                style = tinyStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
        }

        private fun drawLegendDot(context: DrawContext, x: Float, y: Float, size: Float, activity: Float, alpha: Float) {
            val color = FigmaAnimation.lerpArgb(0xFF2E2E35.toInt(), 0xFFFF2F93.toInt(), activity)
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, size, size, size * 0.5f, withAlpha(color, alpha))
        }

        private fun drawProfileSkinPanel(context: DrawContext, x: Float, y: Float, alpha: Float) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 196.0f, 260.0f, 9.0f, withAlpha(0xFF0D0D0E.toInt(), alpha), withAlpha(0xFF242428.toInt(), alpha), 1.0f, ThemeSettings.ThemeRole.CARD)
            drawTextBox(
                context = context,
                text = profileName(),
                x = x + 12.0f,
                y = y + 9.0f,
                width = 172.0f,
                height = 15.0f,
                color = withAlpha(0xFFF1F1F2.toInt(), alpha),
                style = profileNameStyle,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            val roleGrad = profileRoleGradient()
            val time = (System.currentTimeMillis() % 1000000L) / 1000f
            if (roleGrad.size >= 2) {
                FigmaTextRenderer.drawGradientInBox(
                    context = context, text = profileRoleLine().uppercase(),
                    x = x + 12.0f, y = y + 25.0f,
                    width = 172.0f, height = 13.0f,
                    color = WHITE, style = profileRoleStyle,
                    gradientColor1 = roleGrad[0], gradientColor2 = roleGrad[1], time = time,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                    fallbackColor = withAlpha(profileRoleColor(), alpha),
                )
            } else {
                drawTextBox(
                    context = context,
                    text = profileRoleLine().uppercase(),
                    x = x + 12.0f,
                    y = y + 25.0f,
                    width = 172.0f,
                    height = 13.0f,
                    color = withAlpha(profileRoleColor(), alpha),
                    style = profileRoleStyle,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
                )
            }

            renderPlayerModel(context, x, y)
        }

        private fun drawSevenDayGraph(
            context: DrawContext,
            x: Float,
            y: Float,
            alpha: Float,
            playtime: HypnosiaPlaytime.Snapshot,
        ) {
            val graphPoints = weekGraphPoints(playtime)
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 354.0f, 56.0f, 8.0f, withAlpha(0xFF0D0D0E.toInt(), alpha), withAlpha(0xFF242428.toInt(), alpha), 1.0f, ThemeSettings.ThemeRole.CARD)
            drawTextBox(context, "Last 7 days active", x + 11.0f, y + 6.0f, 126.0f, 13.0f, withAlpha(0xFFD6D6D8.toInt(), alpha), graphTitleStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            drawTextBox(context, weekPeakHours(playtime), x + 308.0f, y + 5.0f, 38.0f, 12.0f, withAlpha(0xFF8C8C93.toInt(), alpha), graphSmallStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            drawTextBox(context, "0h", x + 310.0f, y + 25.0f, 28.0f, 10.0f, withAlpha(0xFF6F6F76.toInt(), alpha), graphTinyStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            HypnosiaRenderUtils.drawFigmaBox(context, x + 127.0f, y + 22.5f, 172.0f, 1.0f, 0.5f, withAlpha(0x662A2A2E.toInt(), alpha))
            HypnosiaRenderUtils.drawFigmaBox(context, x + 127.0f, y + 33.5f, 172.0f, 2.0f, 1.0f, withAlpha(0xBF2A2A2E.toInt(), alpha))

            graphPoints.windowed(2).forEach { pair ->
                val (x1, y1) = pair[0]
                val (x2, y2) = pair[1]
                drawSegmentApproximation(context, x + x1, y + y1, x + x2, y + y2, alpha)
            }
            val labels = lastSevenDayLabels()
            graphPoints.forEachIndexed { index, point ->
                val pointSize = if (index == 4) 5.0f else 4.0f
                HypnosiaRenderUtils.drawFigmaBox(context, x + point.first - pointSize * 0.5f, y + point.second - pointSize * 0.5f, pointSize, pointSize, pointSize * 0.5f, withAlpha(WHITE, alpha))
                drawTextBox(context, labels[index], x + 122.0f + index * 24.0f, y + 42.0f, 22.0f, 9.0f, withAlpha(0xFF6E6E75.toInt(), alpha), graphTinyStyle, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Top)
            }
        }

        private fun drawSegmentApproximation(context: DrawContext, startX: Float, startY: Float, endX: Float, endY: Float, alpha: Float) {
            val steps = 12
            for (step in 0 until steps) {
                val t = step / steps.toFloat()
                val nextT = (step + 1) / steps.toFloat()
                val x1 = startX + (endX - startX) * t
                val y1 = startY + (endY - startY) * t
                val x2 = startX + (endX - startX) * nextT
                val y2 = startY + (endY - startY) * nextT
                HypnosiaRenderUtils.drawFigmaBox(
                    context = context,
                    x = (x1 + x2) * 0.5f - 1.5f,
                    y = (y1 + y2) * 0.5f - 1.5f,
                    width = 3.0f,
                    height = 3.0f,
                    radius = 1.5f,
                    bgColor = withAlpha(0x99EDEDF1.toInt(), alpha),
                )
            }
        }

        private fun renderPlayerModel(context: DrawContext, x: Float, y: Float) {
            val client = MinecraftClient.getInstance()
            val player = client.player ?: return

            val skin = player.skin
            val model = cachedPlayerModel ?: PlayerEntityModel(client.loadedEntityModels.getModelPart(EntityModelLayers.PLAYER), false).also {
                cachedPlayerModel = it
            }
            model.setVisible(true)

            val matrix = Matrix3x2f(context.matrices)
            val localLeft = x + modelTuning.x
            val localTop = y + modelTuning.y
            val guiLeft = transformX(matrix, localLeft, localTop)
            val guiTop = transformY(matrix, localLeft, localTop)
            val scaleX = matrix.m00().takeIf { it != 0.0f } ?: 1.0f
            val scaleY = matrix.m11().takeIf { it != 0.0f } ?: scaleX
            val modelScale = modelTuning.scale * scaleX.coerceAtLeast(0.01f)
            val modelWidth = 120.0f * scaleX
            val modelHeight = 210.0f * scaleY

            HypnosiaScissor.withLocalRect(context, Rect(x + 1.0f, y + 40.0f, 194.0f, 219.0f)) {
                context.matrices.pushMatrix()
                context.matrices.scale(1.0f / scaleX, 1.0f / scaleY)
                context.matrices.translate(-matrix.m20(), -matrix.m21())
                try {
                    context.addPlayerSkin(
                        model,
                        skin.body().texturePath(),
                        modelScale,
                        modelTuning.phi,
                        modelTuning.yaw,
                        PLAYER_SKIN_Y_PIVOT,
                        guiLeft.toInt(),
                        guiTop.toInt(),
                        (guiLeft + modelWidth).toInt(),
                        (guiTop + modelHeight).toInt(),
                    )
                } finally {
                    context.matrices.popMatrix()
                }
            }
        }

        private fun transformX(matrix: Matrix3x2f, x: Float, y: Float): Float =
            matrix.m00() * x + matrix.m10() * y + matrix.m20()

        private fun transformY(matrix: Matrix3x2f, x: Float, y: Float): Float =
            matrix.m01() * x + matrix.m11() * y + matrix.m21()

        private fun formatTotalPlaytime(totalSeconds: Long): String {
            val hours = totalSeconds / 3600.0
            return when {
                totalSeconds < 60L -> "${totalSeconds}s"
                totalSeconds < 3600L -> "${totalSeconds / 60L}m"
                hours < 10.0 -> String.format(Locale.US, "%.1fh", hours)
                else -> "${hours.toInt()}h"
            }
        }

        private fun monthTitle(playtime: HypnosiaPlaytime.Snapshot): String {
            val month = playtime.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)
            return "$month ${playtime.month.year}"
        }

        private fun monthCircleSizes(playtime: HypnosiaPlaytime.Snapshot): FloatArray {
            val result = FloatArray(35)
            val first = playtime.month.atDay(1)
            val firstIndex = first.dayOfWeek.value - 1
            val maxSeconds = (1..playtime.month.lengthOfMonth())
                .maxOfOrNull { day -> playtime.dailySeconds[playtime.month.atDay(day)] ?: 0L }
                ?: 0L
            if (maxSeconds <= 0L) {
                return result
            }

            for (day in 1..playtime.month.lengthOfMonth()) {
                val index = firstIndex + day - 1
                if (index !in result.indices) {
                    continue
                }
                val seconds = playtime.dailySeconds[playtime.month.atDay(day)] ?: 0L
                if (seconds <= 0L) {
                    continue
                }
                val activity = (seconds / maxSeconds.toFloat()).coerceIn(0.0f, 1.0f)
                result[index] = 5.0f + activity * 17.0f
            }

            return result
        }

        private fun weekGraphPoints(playtime: HypnosiaPlaytime.Snapshot): List<Pair<Float, Float>> {
            val today = LocalDate.now()
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val hours = days.map { (playtime.dailySeconds[it] ?: 0L) / 3600.0f }
            val maxHours = hours.maxOrNull()?.coerceAtLeast(1.0f) ?: 1.0f
            return hours.mapIndexed { index, value ->
                val x = 131.0f + index * 24.0f
                val y = 34.0f - (value / maxHours).coerceIn(0.0f, 1.0f) * 22.0f
                x to y
            }
        }

        private fun lastSevenDayLabels(): List<String> {
            val today = LocalDate.now()
            return (6 downTo 0).map { offset ->
                today.minusDays(offset.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            }
        }

        private fun weekPeakHours(playtime: HypnosiaPlaytime.Snapshot): String {
            val today = LocalDate.now()
            val peak = (6 downTo 0)
                .maxOf { offset -> (playtime.dailySeconds[today.minusDays(offset.toLong())] ?: 0L) / 3600.0 }
            return if (peak < 10.0) {
                String.format(Locale.US, "%.1fh", peak)
            } else {
                "${peak.toInt()}h"
            }
        }

        private fun profileRoleColor(): Int {
            return if (profileRoleLine() == "no acc") 0xFF8C8C93.toInt() else 0xFF68E673.toInt()
        }

        companion object {
            private const val PLAYER_SKIN_Y_PIVOT = 1.0f
        }
    }

    private class HomeContentNode(
        private val onConfigApplied: () -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f

        private enum class FocusedField {
            Friends,
            Configs,
            ConfigRename,
        }

        private var focusedField: FocusedField? = null
        private var friendInput = ""
        private var configInput = ""
        private val friends = loadFriends().toMutableList()
        private val configs = HypnosiaConfigProfiles.listNames().toMutableList()
        private var selectedConfig: String = HypnosiaConfigProfiles.selectedName()
        private var renamingConfig: String? = null
        private var renameInput: String = ""
        private val friendScroll = SpringFloat(0.0f, stiffness = 320.0f, damping = 38.0f)
        private val configScroll = SpringFloat(0.0f, stiffness = 320.0f, damping = 38.0f)

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))

        override fun render(context: DrawContext) {
            val a = alpha
            val friendPanelX = bounds.x + 9.0f
            val configPanelX = bounds.x + 252.0f
            val panelY = bounds.y + 11.0f

            drawPanel(context, friendPanelX, panelY, 232.0f, 346.0f, 10.0f, a)
            drawPanelHeader(context, friendPanelX, panelY, 232.0f, 0xFF191919.toInt(), a)
            drawTextBox(
                context = context,
                text = "Friends",
                x = friendPanelX + 5.0f,
                y = panelY + 4.0f,
                width = 221.0f,
                height = 18.0f,
                color = withAlpha(WHITE, a),
                style = FigmaTextRenderer.Styles.HomeHeader,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            drawInputField(
                context = context,
                x = friendPanelX + 14.0f,
                y = panelY + 34.0f,
                width = 202.0f,
                height = 24.0f,
                placeholder = "Search or add...",
                value = friendInput,
                focused = focusedField == FocusedField.Friends,
                alpha = a,
            )
            drawHelperText(context, "Search or Enter to add", friendPanelX + 14.0f, panelY + 62.0f, 202.0f, a)
            drawFriendRows(context, friendPanelX, panelY, visibleFriends(), a)

            drawPanel(context, configPanelX, panelY, 341.0f, 346.0f, 10.0f, a)
            drawPanelHeader(context, configPanelX, panelY, 341.0f, 0xFF181818.toInt(), a)
            drawTextBox(
                context = context,
                text = "Config Manager",
                x = configPanelX + 5.0f,
                y = panelY + 4.0f,
                width = 331.0f,
                height = 18.0f,
                color = withAlpha(WHITE, a),
                style = FigmaTextRenderer.Styles.HomeHeader,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            drawInputField(
                context = context,
                x = configPanelX + 14.0f,
                y = panelY + 34.0f,
                width = 311.0f,
                height = 24.0f,
                placeholder = "Search or add Cfg",
                value = configInput,
                focused = focusedField == FocusedField.Configs,
                alpha = a,
            )
            drawHelperText(context, "Search or Enter to add", configPanelX + 14.0f, panelY + 62.0f, 311.0f, a)
            drawConfigRows(context, configPanelX, panelY, visibleConfigs(), a)
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button != 0) return false

            val friendPanelX = bounds.x + 9.0f
            val configPanelX = bounds.x + 252.0f
            val panelY = bounds.y + 11.0f

            if (contains(mouseX, mouseY, friendPanelX + 14.0f, panelY + 34.0f, 202.0f, 24.0f)) {
                focusedField = FocusedField.Friends
                return true
            }
            if (contains(mouseX, mouseY, configPanelX + 14.0f, panelY + 34.0f, 311.0f, 24.0f)) {
                focusedField = FocusedField.Configs
                return true
            }

            if (contains(mouseX, mouseY, friendPanelX + 14.0f, listClipY(panelY), 202.0f, LIST_VIEWPORT_HEIGHT)) {
                val friendRows = visibleFriends().toList()
                val offset = currentScroll(friendScroll, friendRows)
                friendRows.forEachIndexed { index, friend ->
                    val y = rowY(panelY, index, offset)
                    if (contains(mouseX, mouseY, friendPanelX + 190.0f, y - 1.0f, 24.0f, 24.0f)) {
                        friends.remove(friend)
                        saveFriends()
                        focusedField = null
                        return true
                    }
                }
            }

            if (contains(mouseX, mouseY, configPanelX + 14.0f, listClipY(panelY), 311.0f, LIST_VIEWPORT_HEIGHT)) {
                val configRows = visibleConfigs().toList()
                val offset = currentScroll(configScroll, configRows)
                configRows.forEachIndexed { index, config ->
                    val y = rowY(panelY, index, offset)
                    if (contains(mouseX, mouseY, configPanelX + 14.0f, y, 230.0f, 24.0f)) {
                        if (renamingConfig != null && renamingConfig != config) cancelRename()
                        selectConfig(config)
                        focusedField = null
                        return true
                    }
                    if (contains(mouseX, mouseY, configPanelX + 270.0f, y - 1.0f, 24.0f, 24.0f)) {
                        if (config == HypnosiaConfigProfiles.DEFAULT_NAME) {
                            focusedField = null
                            return true
                        }
                        selectConfig(config)
                        renamingConfig = config
                        renameInput = config
                        focusedField = FocusedField.ConfigRename
                        return true
                    }
                    if (contains(mouseX, mouseY, configPanelX + 294.0f, y - 1.0f, 24.0f, 24.0f)) {
                        if (config == HypnosiaConfigProfiles.DEFAULT_NAME) {
                            focusedField = null
                            return true
                        }
                        if (renamingConfig == config) cancelRename()
                        HypnosiaConfigProfiles.delete(config)
                        refreshConfigNames()
                        selectedConfig = HypnosiaConfigProfiles.selectedName()
                        onConfigApplied()
                        focusedField = null
                        return true
                    }
                }
            }

            if (renamingConfig != null) cancelRename()
            focusedField = null
            return false
        }

        override fun mouseScrolled(
            mouseX: Float,
            mouseY: Float,
            horizontalAmount: Float,
            verticalAmount: Float,
        ): Boolean {
            val friendPanelX = bounds.x + 9.0f
            val configPanelX = bounds.x + 252.0f
            val panelY = bounds.y + 11.0f

            if (contains(mouseX, mouseY, friendPanelX + 14.0f, listClipY(panelY), 202.0f, LIST_VIEWPORT_HEIGHT)) {
                scrollList(friendScroll, visibleFriends(), verticalAmount)
                return true
            }

            if (contains(mouseX, mouseY, configPanelX + 14.0f, listClipY(panelY), 311.0f, LIST_VIEWPORT_HEIGHT)) {
                scrollList(configScroll, visibleConfigs(), verticalAmount)
                return true
            }

            return false
        }

        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
            val field = focusedField ?: return false
            return when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (field == FocusedField.Friends && friendInput.isNotEmpty()) friendInput = friendInput.dropLast(1)
                    if (field == FocusedField.Configs && configInput.isNotEmpty()) configInput = configInput.dropLast(1)
                    if (field == FocusedField.ConfigRename && renameInput.isNotEmpty()) renameInput = renameInput.dropLast(1)
                    true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (field == FocusedField.Friends) addFriendFromInput()
                    if (field == FocusedField.Configs) addConfigFromInput()
                    if (field == FocusedField.ConfigRename) confirmRename()
                    true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    if (field == FocusedField.ConfigRename) cancelRename()
                    else if (field == FocusedField.Friends && friendInput.isNotEmpty()) friendInput = ""
                    else if (field == FocusedField.Configs && configInput.isNotEmpty()) configInput = ""
                    else focusedField = null
                    true
                }
                else -> false
            }
        }

        override fun charTyped(chr: Char, modifiers: Int): Boolean {
            val field = focusedField ?: return false
            if (chr.isISOControl()) return false
            when (field) {
                FocusedField.Friends -> if (friendInput.length < 24) friendInput += chr
                FocusedField.Configs -> if (configInput.length < 28) configInput += chr
                FocusedField.ConfigRename -> if (renameInput.length < 28) renameInput += chr
            }
            return true
        }

        private fun drawPanelHeader(context: DrawContext, x: Float, y: Float, width: Float, color: Int, alpha: Float) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, 25.0f, 10.0f, withAlpha(color, alpha))
            HypnosiaRenderUtils.drawFigmaBox(context, x, y + 15.0f, width, 10.0f, 0.0f, withAlpha(color, alpha))
        }

        private fun drawInputField(
            context: DrawContext,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            placeholder: String,
            value: String,
            focused: Boolean,
            alpha: Float,
        ) {
            val visible = if (value.isBlank()) {
                placeholder
            } else {
                value + if (focused && ((System.nanoTime() / 500_000_000L) % 2L == 0L)) "_" else ""
            }
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = x,
                y = y,
                width = width,
                height = height,
                radius = 13.0f,
                bgColor = withAlpha(0x00111114, alpha),
                strokeColor = withAlpha(if (focused) WHITE else STROKE, alpha),
                strokeThickness = if (focused) 1.4f else 1.0f,
            )
            drawTextBox(
                context = context,
                text = fitInputText(visible, width - 22.0f),
                x = x + 10.0f,
                y = y + 1.0f,
                width = width - 22.0f,
                height = 20.0f,
                color = withAlpha(if (value.isBlank()) 0xFFDADAE0.toInt() else WHITE, alpha),
                style = FigmaTextRenderer.Styles.HomeField,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )
        }

        private fun drawFriendRow(context: DrawContext, x: Float, y: Float, width: Float, label: String, alpha: Float) {
            drawField(context, x, y, width, 24.0f, label, alpha)
            drawActionIcon(context, "user_remove.png", x + 175.0f, y - 1.0f, alpha)
        }

        private fun drawConfigRow(
            context: DrawContext,
            x: Float,
            y: Float,
            width: Float,
            label: String,
            alpha: Float,
            showVisibilityIcon: Boolean,
            isRenaming: Boolean,
            deletable: Boolean,
        ) {
            if (isRenaming) {
                val cursor = if ((System.nanoTime() / 500_000_000L) % 2L == 0L) "_" else ""
                HypnosiaRenderUtils.drawFigmaBox(
                    context = context,
                    x = x,
                    y = y,
                    width = width,
                    height = 24.0f,
                    radius = 8.0f,
                    bgColor = withAlpha(0x1AFFFFFF, alpha),
                    strokeColor = withAlpha(WHITE, alpha),
                    strokeThickness = 1.2f,
                )
                drawTextBox(
                    context = context,
                    text = fitInputText(renameInput + cursor, width - 22.0f),
                    x = x + 10.0f,
                    y = y + 1.0f,
                    width = width - 22.0f,
                    height = 20.0f,
                    color = withAlpha(WHITE, alpha),
                    style = FigmaTextRenderer.Styles.HomeField,
                    horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                    verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
                )
                return
            }
            drawField(context, x, y, width, 24.0f, label, alpha)
            if (showVisibilityIcon) {
                drawActionIcon(context, "show_file.png", x + 229.0f, y - 1.0f, alpha)
                if (deletable) {
                    drawActionIcon(context, "file_edit.png", x + 256.0f, y - 1.0f, alpha)
                    drawActionIcon(context, "interface_trash_empty.png", x + 280.0f, y - 1.0f, alpha)
                }
            } else {
                if (deletable) {
                    drawActionIcon(context, "file_edit.png", x + 257.0f, y - 1.0f, alpha)
                    drawActionIcon(context, "interface_trash_empty.png", x + 281.0f, y - 1.0f, alpha)
                }
            }
        }

        private fun drawActionIcon(context: DrawContext, fileName: String, x: Float, y: Float, alpha: Float) {
            icon(context, fileName, x, y, 24.0f, 24.0f, withAlpha(WHITE, alpha))
        }

        private fun drawFriendRows(context: DrawContext, panelX: Float, panelY: Float, rows: List<String>, alpha: Float) {
            val offset = updateScroll(friendScroll, rows)
            val clip = Rect(panelX + 14.0f, listClipY(panelY), 204.0f, LIST_VIEWPORT_HEIGHT)

            HypnosiaScissor.withLocalRect(context, clip) {
                rows.forEachIndexed { index, friend ->
                    val y = rowY(panelY, index, offset)
                    if (rowVisible(y, clip)) {
                        drawFriendRow(context, panelX + 15.0f, y, 202.0f, friend, alpha)
                    }
                }
            }

            drawListScrollbar(context, panelX + 224.0f, panelY, rows, offset, alpha)
        }

        private fun drawConfigRows(context: DrawContext, panelX: Float, panelY: Float, rows: List<String>, alpha: Float) {
            val offset = updateScroll(configScroll, rows)
            val clip = Rect(panelX + 14.0f, listClipY(panelY), 312.0f, LIST_VIEWPORT_HEIGHT)

            HypnosiaScissor.withLocalRect(context, clip) {
                rows.forEachIndexed { index, label ->
                    val y = rowY(panelY, index, offset)
                    if (rowVisible(y, clip)) {
                        drawConfigRow(
                            context = context,
                            x = panelX + 14.0f,
                            y = y,
                            width = 311.0f,
                            label = configLabel(label),
                            alpha = alpha,
                            showVisibilityIcon = label == selectedConfig,
                            isRenaming = label == renamingConfig,
                            deletable = label != HypnosiaConfigProfiles.DEFAULT_NAME,
                        )
                    }
                }
            }

            drawListScrollbar(context, panelX + 332.0f, panelY, rows, offset, alpha)
        }

        private fun drawListScrollbar(
            context: DrawContext,
            x: Float,
            panelY: Float,
            rows: List<String>,
            offset: Float,
            alpha: Float,
        ) {
            val max = maxScroll(rows)
            if (max <= 0.5f) {
                return
            }

            val trackY = listClipY(panelY) + 4.0f
            val trackHeight = LIST_VIEWPORT_HEIGHT - 8.0f
            val contentHeight = rows.size * ROW_STEP
            val thumbHeight = (trackHeight * (LIST_VIEWPORT_HEIGHT / contentHeight)).coerceIn(24.0f, trackHeight)
            val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0.0f)
            val thumbY = trackY + thumbTravel * (offset / max)

            HypnosiaRenderUtils.drawFigmaBox(context, x, trackY, 3.0f, trackHeight, 2.0f, withAlpha(0x332A2A33, alpha))
            HypnosiaRenderUtils.drawFigmaBox(context, x - 1.0f, thumbY, 5.0f, thumbHeight, 3.0f, withAlpha(0x99EDEDF1.toInt(), alpha))
        }

        private fun visibleFriends(): List<String> {
            val query = friendInput.trim()
            if (query.isBlank()) return friends
            return friends.filter { it.contains(query, ignoreCase = true) }
        }

        private fun visibleConfigs(): List<String> {
            val query = configInput.trim()
            if (query.isBlank()) return configs
            return configs.filter { it.contains(query, ignoreCase = true) }
        }

        private fun addFriendFromInput() {
            val name = friendInput.trim().takeIf { it.isNotBlank() } ?: return
            if (friends.none { it.equals(name, ignoreCase = true) }) {
                friends += name
                saveFriends()
                scrollToEnd(friendScroll, visibleFriends())
            }
            friendInput = ""
        }

        private fun addConfigFromInput() {
            val name = safeConfigName(configInput).takeIf { it.isNotBlank() } ?: return
            HypnosiaConfigProfiles.create(name, copyCurrent = true)
            refreshConfigNames()
            selectConfig(name)
            scrollToEnd(configScroll, visibleConfigs())
            configInput = ""
        }

        private fun confirmRename() {
            val oldName = renamingConfig ?: return
            val newName = safeConfigName(renameInput).takeIf { it.isNotBlank() } ?: return
            if (!newName.equals(oldName, ignoreCase = true)) {
                if (configs.any { it.equals(newName, ignoreCase = true) }) return
                HypnosiaConfigProfiles.rename(oldName, newName)
                refreshConfigNames()
                selectedConfig = HypnosiaConfigProfiles.selectedName()
            }
            renamingConfig = null
            renameInput = ""
            focusedField = null
        }

        private fun cancelRename() {
            renamingConfig = null
            renameInput = ""
            if (focusedField == FocusedField.ConfigRename) focusedField = null
        }

        private fun configLabel(config: String): String {
            return if (config == selectedConfig) "$config (Active)" else config
        }

        private fun refreshConfigNames() {
            configs.clear()
            configs += HypnosiaConfigProfiles.listNames()
            if (configs.none { it.equals(selectedConfig, ignoreCase = true) }) {
                selectedConfig = HypnosiaConfigProfiles.selectedName()
            }
        }

        private fun selectConfig(name: String) {
            if (!HypnosiaConfigProfiles.select(name)) return
            selectedConfig = HypnosiaConfigProfiles.selectedName()
            refreshConfigNames()
            onConfigApplied()
        }

        private fun fitInputText(value: String, maxWidth: Float): String {
            var text = value
            while (text.isNotEmpty() && FigmaTextRenderer.width(text, FigmaTextRenderer.Styles.HomeField) > maxWidth) {
                text = text.drop(1)
            }
            return text
        }

        private fun scrollList(scroll: SpringFloat, rows: List<String>, verticalAmount: Float) {
            val max = maxScroll(rows)
            if (max <= 0.0f) {
                scroll.snap(0.0f)
                return
            }
            scroll.target = (scroll.target - verticalAmount * ROW_STEP * 2.0f).coerceIn(0.0f, max)
        }

        private fun scrollToEnd(scroll: SpringFloat, rows: List<String>) {
            scroll.target = maxScroll(rows)
        }

        private fun updateScroll(scroll: SpringFloat, rows: List<String>): Float {
            val max = maxScroll(rows)
            scroll.target = scroll.target.coerceIn(0.0f, max)
            val offset = scroll.update(UiInputState.frameSeconds).coerceIn(0.0f, max)
            if (offset != scroll.value) {
                scroll.snap(offset)
            }
            return offset
        }

        private fun currentScroll(scroll: SpringFloat, rows: List<String>): Float {
            return scroll.value.coerceIn(0.0f, maxScroll(rows))
        }

        private fun maxScroll(rows: List<String>): Float {
            return (rows.size * ROW_STEP - LIST_VIEWPORT_HEIGHT).coerceAtLeast(0.0f)
        }

        private fun rowY(panelY: Float, index: Int, offset: Float): Float {
            return panelY + ROW_TOP_OFFSET + index * ROW_STEP - offset
        }

        private fun listClipY(panelY: Float): Float {
            return panelY + LIST_TOP_OFFSET
        }

        private fun rowVisible(rowY: Float, clip: Rect): Boolean {
            return rowY + ROW_HEIGHT >= clip.y && rowY <= clip.bottom
        }

        private fun loadFriends(): List<String> {
            val file = HypnosiaPaths.rootFile("friends.txt")
            if (!Files.exists(file)) {
                return listOf("Friend Slot 01", "Friend Slot 02", "Friend Slot 03", "Friend Slot 04")
            }

            return runCatching {
                Files.readAllLines(file, StandardCharsets.UTF_8)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
            }.getOrDefault(emptyList())
        }

        private fun saveFriends() {
            val file = HypnosiaPaths.rootFile("friends.txt")
            runCatching {
                Files.write(file, friends, StandardCharsets.UTF_8)
                FriendsManager.reload()
            }
        }

        private fun loadSelectedConfig(): String {
            return runCatching {
                HypnosiaClientSettings.string("ui.selected.config", "Default").takeIf { it.isNotBlank() } ?: "Default"
            }.getOrDefault("Default")
        }

        private fun saveSelectedConfig(name: String) {
            runCatching {
                HypnosiaClientSettings.set("ui.selected.config", name)
            }
        }

        private fun loadConfigNames(): List<String> {
            val dir = HypnosiaPaths.configsDir
            val names = mutableListOf<String>()
            runCatching {
                Files.list(dir).use { stream ->
                    stream.forEach { file ->
                        val fileName = file.fileName.toString()
                        if (Files.isRegularFile(file) && fileName.endsWith(".json", ignoreCase = true)) {
                            names += fileName.removeSuffix(".json")
                        }
                    }
                }
            }

            return names
                .distinctBy { it.lowercase() }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .ifEmpty { listOf("Default", "Cfg Save 02", "Cfg Save 03") }
        }

        private fun ensureLocalConfig(name: String) {
            val file = HypnosiaPaths.configsDir.resolve("$name.json")
            runCatching {
                if (!Files.exists(file)) {
                    Files.writeString(file, "{}\n", StandardCharsets.UTF_8)
                }
            }
        }

        private fun deleteLocalConfig(name: String) {
            runCatching {
                Files.deleteIfExists(HypnosiaPaths.configsDir.resolve("${safeConfigName(name)}.json"))
            }
        }

        private fun safeConfigName(value: String): String {
            return value
                .trim()
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', '.')
                .take(48)
        }

        companion object {
            private const val LIST_TOP_OFFSET = 90.0f
            private const val ROW_TOP_OFFSET = 94.0f
            private const val LIST_VIEWPORT_HEIGHT = 246.0f
            private const val ROW_STEP = 29.0f
            private const val ROW_HEIGHT = 24.0f
        }
    }
    private class ModuleSettingsDrawerNode(
        private val module: () -> ModuleEntry?,
        private val close: () -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(WIDTH), SizeMode.Fixed(HEIGHT))) {
        private var activeHudSlider: HudSlider? = null
        private var activeTargetSlider: TargetSliderKind? = null
        private var activeWorldSlider: WorldSliderKind? = null
        private var activeAspectSlider = false
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

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(WIDTH, HEIGHT))

        override fun render(context: DrawContext) {
            val title = module()?.title ?: "Icons"
            syncScrollState()
            HypnosiaRenderUtils.drawThemedBox(
                context = context,
                x = bounds.x,
                y = bounds.y,
                width = WIDTH,
                height = HEIGHT,
                radius = 10.0f,
                bgColor = DRAWER_BG,
                strokeColor = DRAWER_STROKE,
                strokeThickness = 1.0f,
                role = ThemeSettings.ThemeRole.DRAWER,
            )
            drawText(context, "CLIENT SETTINGS", bounds.x + 15.0f, bounds.y + 13.0f, 10.0f, 0xFF8E8E98.toInt())
            drawText(context, title, bounds.x + 15.0f, bounds.y + 31.0f, 18.0f, WHITE)
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
            HypnosiaScissor.withLocalRect(
                context,
                Rect(bounds.x + 1.0f, bounds.y + CONTENT_TOP, WIDTH - 2.0f, HEIGHT - CONTENT_TOP - CONTENT_BOTTOM_PAD),
            ) {
                context.matrices.pushMatrix()
                context.matrices.translate(0.0f, -offset)
                renderScrollableContent(context)
                context.matrices.popMatrix()
            }
            if (maxContentScroll > 0.5f) {
                renderContentScrollbar(context, offset)
            }
            renderColorPicker(context)
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
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
                val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
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
                val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val axisRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 40.0f)
                val xRect = Rect(bounds.x + 11.0f, bounds.y + 175.0f, 212.0f, 48.0f)
                val yRect = Rect(bounds.x + 11.0f, bounds.y + 235.0f, 212.0f, 48.0f)
                val highlightRect = Rect(bounds.x + 11.0f, bounds.y + 295.0f, 212.0f, 40.0f)
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
                val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val xRect = Rect(bounds.x + 11.0f, bounds.y + if (hudModule == HudModuleSettings.Module.PLAYER_INFO) 247.0f else 123.0f, 212.0f, 48.0f)
                val yRect = Rect(bounds.x + 11.0f, bounds.y + if (hudModule == HudModuleSettings.Module.PLAYER_INFO) 307.0f else 183.0f, 212.0f, 48.0f)
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
                val versionRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val xRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 48.0f)
                val yRect = Rect(bounds.x + 11.0f, bounds.y + 183.0f, 212.0f, 48.0f)
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
                    val modelXRect = Rect(bounds.x + 11.0f, bounds.y + 243.0f, 212.0f, 48.0f)
                    val modelYRect = Rect(bounds.x + 11.0f, bounds.y + 303.0f, 212.0f, 48.0f)
                    val yawRect = Rect(bounds.x + 11.0f, bounds.y + 363.0f, 212.0f, 48.0f)
                    val pitchRect = Rect(bounds.x + 11.0f, bounds.y + 423.0f, 212.0f, 48.0f)
                    val scaleRect = Rect(bounds.x + 11.0f, bounds.y + 483.0f, 212.0f, 48.0f)
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
                val blackHoleRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val colorRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 40.0f)
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
                            iconPaletteOpen = false
                            return true
                        }
                    }
                }
            } else if (module()?.id == "world.custom_fog") {
                val distanceRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 48.0f)
                val strengthRect = Rect(bounds.x + 11.0f, bounds.y + 183.0f, 212.0f, 48.0f)
                val softnessRect = Rect(bounds.x + 11.0f, bounds.y + 243.0f, 212.0f, 48.0f)
                val colorRect = Rect(bounds.x + 11.0f, bounds.y + 303.0f, 212.0f, 40.0f)
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
                            fogPaletteOpen = false
                            return true
                        }
                    }
                }
            } else if (module()?.id == "other.friends") {
                val displayRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                if (contains(mouseX, contentMouseY, displayRect.x, displayRect.y, displayRect.width, displayRect.height)) {
                    val next = !FriendsManager.isEnabled()
                    FriendsManager.setEnabled(next)
                    module()?.enabled = next
                    return true
                }
            } else if (module()?.id == "other.streamer_mode") {
                val levelRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val nameRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 40.0f)
                if (contains(mouseX, contentMouseY, levelRect.x, levelRect.y, levelRect.width, levelRect.height)) {
                    StreamerModeSettings.cycleLevel()
                    return true
                }
                if (contains(mouseX, contentMouseY, nameRect.x, nameRect.y, nameRect.width, nameRect.height)) {
                    streamerReplacementEditing = true
                    return true
                }
            } else if (module()?.id == "visuals.aspect_ratio") {
                val modeRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val freeRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 48.0f)
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
                val modeRect = Rect(bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f)
                val fontRect = Rect(bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 40.0f)
                val glassRect = Rect(bounds.x + 11.0f, bounds.y + 171.0f, 212.0f, 40.0f)
                val gradientRect = Rect(bounds.x + 11.0f, bounds.y + 219.0f, 212.0f, 40.0f)
                val baseRect = Rect(bounds.x + 11.0f, bounds.y + 267.0f, 212.0f, 40.0f)
                val startRect = Rect(bounds.x + 11.0f, bounds.y + 315.0f, 212.0f, 40.0f)
                val endRect = Rect(bounds.x + 11.0f, bounds.y + 363.0f, 212.0f, 40.0f)
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
                            themePaletteTarget = null
                            return true
                        }
                    }
                }
            }
            return contains(mouseX, mouseY, bounds.x, bounds.y, WIDTH, HEIGHT)
        }

        override fun mouseScrolled(
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

        override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, deltaX: Float, deltaY: Float): Boolean {
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
            return false
        }

        override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
            val wasDragging = activeHudSlider != null || activeTargetSlider != null || activeWorldSlider != null || activeAspectSlider || colorPickerDrag != null
            activeHudSlider = null
            activeTargetSlider = null
            activeWorldSlider = null
            activeAspectSlider = false
            colorPickerDrag = null
            return wasDragging
        }

        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
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

        override fun charTyped(chr: Char, modifiers: Int): Boolean {
            if (!streamerReplacementEditing || chr.isISOControl()) return false
            StreamerModeSettings.setReplacement((StreamerModeSettings.replacement() + chr).take(32))
            return true
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
            HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 171.0f, 212.0f, 98.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
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
            HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 455.0f, 212.0f, 98.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
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
            HypnosiaRenderUtils.drawFigmaBox(context, hue.x - 2.0f, hue.y + hsv[0] * hue.height - 6.0f, 12.0f, 12.0f, 6.0f, WHITE)

            val opacity = pickerOpacityRect()
            HypnosiaRenderUtils.drawAlphaStrip(context, opacity.x, opacity.y, opacity.width, opacity.height, 5.0f, color)
            HypnosiaRenderUtils.drawFigmaBox(context, opacity.x - 2.0f, opacity.y + alpha * opacity.height - 6.0f, 12.0f, 12.0f, 6.0f, WHITE)

            val valueY = y + 318.0f
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
                    val hue = ((mouseY - rect.y) / rect.height).coerceIn(0.0f, 1.0f)
                    colorFromHsv(hue, hsv[1], hsv[2], alpha)
                }
                ColorPickerDrag.OPACITY -> {
                    val rect = pickerOpacityRect()
                    val nextAlpha = ((mouseY - rect.y) / rect.height).coerceIn(0.0f, 1.0f)
                    colorFromHsv(hsv[0], hsv[1], hsv[2], nextAlpha)
                }
            }
            setColorForTarget(target, next)
        }

        private fun syncScrollState() {
            val moduleId = module()?.id
            if (moduleId != lastModuleId) {
                contentScroll.snap(0.0f)
                contentScroll.target = 0.0f
                activeWorldSlider = null
                activeAspectSlider = false
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
                HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 123.0f, 212.0f, 104.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
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
                HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 543.0f, 212.0f, 62.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
                drawText(context, "Options", bounds.x + 21.0f, bounds.y + 552.0f, 12.0f, 0xFFE7E7EA.toInt())
                drawTargetToggleChip(context, "Hand + Armor Strip", state.showEquipmentStrip, bounds.x + 20.0f, bounds.y + 578.0f, 196.0f)
            } else {
                HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 243.0f, 212.0f, 62.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
                drawText(context, "Options", bounds.x + 21.0f, bounds.y + 252.0f, 12.0f, 0xFFE7E7EA.toInt())
                drawTargetToggleChip(context, "Hand + Armor Strip", state.showEquipmentStrip, bounds.x + 20.0f, bounds.y + 278.0f, 196.0f)
            }
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
            HypnosiaRenderUtils.drawThemedBox(context, bounds.x + 11.0f, bounds.y + 377.0f, 212.0f, 98.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
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

        private fun drawGroupCard(context: DrawContext, x: Float, y: Float, title: String) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 212.0f, 96.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.CARD)
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
            val colors = listOf(
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
            return colors.mapIndexed { index, color ->
                val col = index % 11
                val row = index / 11
                color to Rect(colorPickerX() + 18.0f + col * 26.0f, colorPickerY() + 438.0f + row * 24.0f, 18.0f, 18.0f)
            }
        }

        private fun colorForTarget(target: ColorPickerTarget): Int =
            when (target) {
                ColorPickerTarget.ICON -> IconSettings.color
                ColorPickerTarget.FOG -> WorldVisualSettings.fogColor()
                ColorPickerTarget.THEME_BASE -> ThemeSettings.baseColor()
                ColorPickerTarget.THEME_GRADIENT_START -> ThemeSettings.gradientStart()
                ColorPickerTarget.THEME_GRADIENT_END -> ThemeSettings.gradientEnd()
            }

        private fun setColorForTarget(target: ColorPickerTarget, color: Int) {
            when (target) {
                ColorPickerTarget.ICON -> IconSettings.setColor(color)
                ColorPickerTarget.FOG -> WorldVisualSettings.setFogColor(color)
                ColorPickerTarget.THEME_BASE -> ThemeSettings.setBaseColor(color)
                ColorPickerTarget.THEME_GRADIENT_START -> ThemeSettings.setGradientStart(color)
                ColorPickerTarget.THEME_GRADIENT_END -> ThemeSettings.setGradientEnd(color)
            }
        }

        private fun pickerCanvasRect(): Rect = Rect(colorPickerX() + 28.0f, colorPickerY() + 100.0f, 284.0f, 134.0f)

        private fun pickerHueRect(): Rect = Rect(colorPickerX() + 18.0f, colorPickerY() + 282.0f, 296.0f, 10.0f)

        private fun pickerOpacityRect(): Rect = Rect(colorPickerX() + 18.0f, colorPickerY() + 332.0f, 296.0f, 10.0f)

        private fun colorPickerX(): Float = bounds.right + 8.0f

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
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 212.0f, 40.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
            drawText(context, label, x + labelOffX, y + 12.0f, 13.0f, 0xFFE7E7EA.toInt())
            drawText(context, value, x + valueOffX, y + 12.0f, 13.0f, valueColor)
        }

        private fun drawSliderRow(context: DrawContext, x: Float, y: Float, label: String, value: String) {
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 212.0f, 48.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
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
            HypnosiaRenderUtils.drawThemedBox(context, x, y, 212.0f, 48.0f, 9.0f, DRAWER_BG, DRAWER_STROKE, 1.0f, ThemeSettings.ThemeRole.BUTTON)
            drawText(context, label, x + 10.0f, y + 8.0f, 13.0f, 0xFFE7E7EA.toInt())
            drawText(context, valueText, x + 146.0f, y + 8.0f, 13.0f, 0xFFFF2F86.toInt())
            rect(context, x + 82.0f, y + 29.0f, 112.0f, 2.0f, 0xFF34343C.toInt())
            rect(context, x + 82.0f, y + 29.0f, 112.0f * value.coerceIn(0.0f, 1.0f), 2.0f, 0xFFFF2F86.toInt())
            HypnosiaRenderUtils.drawFigmaBox(context, x + 77.0f + 112.0f * value.coerceIn(0.0f, 1.0f), y + 25.0f, 10.0f, 10.0f, 5.0f, 0xFFFF2F86.toInt())
        }

        private data class HudSlider(val module: HudModuleSettings.Module, val kind: HudSliderKind)

        private enum class HudSliderKind {
            X,
            Y,
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
        }

        private enum class ColorPickerDrag {
            CANVAS,
            HUE,
            OPACITY,
        }

        companion object {
            private val DRAWER_BG = 0xFE0D0D0D.toInt()
            private val DRAWER_STROKE = 0xFE272727.toInt()
            private val hudModuleIds = setOf("hud.player_info", "hud.inventory", "hud.cooldowns", "hud.potions", "hud.hotkeys")
            const val WIDTH = 236.0f
            const val HEIGHT = 380.0f
            private const val COLOR_PICKER_WIDTH = 334.0f
            private const val COLOR_PICKER_HEIGHT = 500.0f
            private const val COLOR_SWATCH_X = 206.0f
            private const val CONTENT_TOP = 67.0f
            private const val CONTENT_BOTTOM_PAD = 12.0f
        }
    }

    private data class ModuleEntry(
        val id: String,
        val category: HypnosiaCategory,
        val title: String,
        var enabled: Boolean,
        val hasSettings: Boolean = !id.startsWith("world."),
        var settingsOpen: Boolean = false,
    )

    private fun drawPanel(context: DrawContext, x: Float, y: Float, width: Float, height: Float, radius: Float, alpha: Float) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, width, height, radius, withAlpha(SURFACE, alpha), withAlpha(STROKE, alpha), 1.0f, ThemeSettings.ThemeRole.CARD)
    }

    private fun drawField(context: DrawContext, x: Float, y: Float, width: Float, height: Float, label: String, alpha: Float) {
        HypnosiaRenderUtils.drawThemedBox(context, x, y, width, height, 13.0f, withAlpha(0x00111114, alpha), withAlpha(STROKE, alpha), 1.0f, ThemeSettings.ThemeRole.INPUT)
        drawTextBox(
            context = context,
            text = label,
            x = x + 10.0f,
            y = y + 1.0f,
            width = width - 22.0f,
            height = 20.0f,
            color = withAlpha(WHITE, alpha),
            style = FigmaTextRenderer.Styles.HomeField,
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
        )
    }

    private fun drawHelperText(context: DrawContext, text: String, x: Float, y: Float, width: Float, alpha: Float) {
        drawTextBox(
            context = context,
            text = text,
            x = x,
            y = y,
            width = width,
            height = 25.0f,
            color = withAlpha(0xFF929292.toInt(), alpha),
            style = FigmaTextRenderer.Styles.Helper,
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
        )
    }

    private fun rect(context: DrawContext, x: Float, y: Float, width: Float, height: Float, color: Int) {
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 0.0f, color)
    }

    private fun icon(
        context: DrawContext,
        fileName: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tint: Int,
        preserveTextureColor: Boolean = false,
    ) {
        val identifier = Identifier.of(HypnosiaClient.MOD_ID, "textures/gui/icons/$fileName")
        if (preserveTextureColor) {
            HypnosiaRenderUtils.drawRoundedTexture(
                context = context,
                identifier = identifier,
                x = x,
                y = y,
                width = width,
                height = height,
                radius = 0.0f,
                tintColor = tint,
            )
            return
        }

        HypnosiaRenderUtils.drawIconTexture(
            context = context,
            identifier = identifier,
            x = x,
            y = y,
            width = width,
            height = height,
            tintColor = tint,
        )
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

    private fun drawMultilineText(
        context: DrawContext,
        lines: List<String>,
        x: Float,
        y: Float,
        width: Float,
        lineHeight: Float,
        color: Int,
        style: FigmaTextRenderer.FigmaTextStyle,
        align: FigmaTextRenderer.HorizontalAlign,
    ) {
        lines.forEachIndexed { index, line ->
            drawTextBox(
                context = context,
                text = line,
                x = x,
                y = y + index * lineHeight,
                width = width,
                height = lineHeight,
                color = color,
                style = style,
                horizontalAlign = align,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
        }
    }

    private fun drawAccountHeroTitle(
        context: DrawContext,
        x: Float,
        y: Float,
        title: String,
        alpha: Float,
        style: FigmaTextRenderer.FigmaTextStyle,
    ) {
        icon(
            context = context,
            fileName = "black_hole.png",
            x = x + 118.0f,
            y = y + 23.0f,
            width = 32.0f,
            height = 32.0f,
            tint = withAlpha(WHITE, alpha),
            preserveTextureColor = true,
        )
        drawTextBox(
            context = context,
            text = title,
            x = x + 164.0f,
            y = y + 17.0f,
            width = 276.0f,
            height = 44.0f,
            color = withAlpha(WHITE, alpha),
            style = style,
            horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
            verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
        )
        icon(
            context = context,
            fileName = "black_hole.png",
            x = x + 454.0f,
            y = y + 23.0f,
            width = 32.0f,
            height = 32.0f,
            tint = withAlpha(WHITE, alpha),
            preserveTextureColor = true,
        )
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

    private fun contains(mouseX: Float, mouseY: Float, x: Float, y: Float, width: Float, height: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress.coerceIn(0.0f, 1.0f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun withAlpha(color: Int, multiplier: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * multiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
