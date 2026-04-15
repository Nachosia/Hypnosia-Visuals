package dev.hypnosia.ui.layout

import dev.hypnosia.HypnosiaClient
import dev.hypnosia.ui.animation.SpringFloat
import dev.hypnosia.ui.component.CategorySidebar
import dev.hypnosia.ui.component.HypnosiaCategory
import dev.hypnosia.ui.component.ModuleRow
import dev.hypnosia.ui.render.FigmaTextRenderer
import dev.hypnosia.ui.render.HypnosiaRenderUtils
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

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
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val MUTED_TEXT = 0xFFB8B8C2.toInt()

    private val modulesByCategory = mapOf(
        HypnosiaCategory.Visuals to listOf(
            ModuleEntry("visuals.fullbright", HypnosiaCategory.Visuals, "Fullbright", true),
            ModuleEntry("visuals.entity_esp", HypnosiaCategory.Visuals, "Entity ESP", false),
            ModuleEntry("visuals.block_outline", HypnosiaCategory.Visuals, "Block Outline", true),
            ModuleEntry("visuals.item_glow", HypnosiaCategory.Visuals, "Item Glow", false),
            ModuleEntry("visuals.tracers", HypnosiaCategory.Visuals, "Tracers", false),
            ModuleEntry("visuals.no_render", HypnosiaCategory.Visuals, "No Render", false),
        ),
        HypnosiaCategory.World to listOf(
            ModuleEntry("world.auto_tool", HypnosiaCategory.World, "Auto Tool", false),
            ModuleEntry("world.fast_place", HypnosiaCategory.World, "Fast Place", true),
            ModuleEntry("world.scaffold_assist", HypnosiaCategory.World, "Scaffold Assist", false),
            ModuleEntry("world.safe_walk", HypnosiaCategory.World, "Safe Walk", false),
            ModuleEntry("world.ore_marker", HypnosiaCategory.World, "Ore Marker", true),
            ModuleEntry("world.weather_control", HypnosiaCategory.World, "Weather Control", false),
        ),
        HypnosiaCategory.Client to listOf(
            ModuleEntry("client.click_gui", HypnosiaCategory.Client, "Click GUI", true),
            ModuleEntry("client.config_sync", HypnosiaCategory.Client, "Config Sync", false),
            ModuleEntry("client.keybinds", HypnosiaCategory.Client, "Keybinds", false),
            ModuleEntry("client.notifications", HypnosiaCategory.Client, "Notifications", true),
            ModuleEntry("client.profiles", HypnosiaCategory.Client, "Profiles", false),
            ModuleEntry("client.panic_mode", HypnosiaCategory.Client, "Panic Mode", false),
            ModuleEntry("client.icons", HypnosiaCategory.Client, "Icons", false),
        ),
        HypnosiaCategory.Hud to listOf(
            ModuleEntry("hud.watermark", HypnosiaCategory.Hud, "Watermark", true),
            ModuleEntry("hud.array_list", HypnosiaCategory.Hud, "Array List", false),
            ModuleEntry("hud.target_hud", HypnosiaCategory.Hud, "Target HUD", false),
            ModuleEntry("hud.keystrokes", HypnosiaCategory.Hud, "Keystrokes", true),
            ModuleEntry("hud.coordinates", HypnosiaCategory.Hud, "Coordinates", false),
            ModuleEntry("hud.potion_list", HypnosiaCategory.Hud, "Potion List", false),
        ),
        HypnosiaCategory.Other to listOf(
            ModuleEntry("other.friends", HypnosiaCategory.Other, "Friends", true),
            ModuleEntry("other.streamer_mode", HypnosiaCategory.Other, "Streamer Mode", false),
            ModuleEntry("other.chat_tools", HypnosiaCategory.Other, "Chat Tools", false),
            ModuleEntry("other.discord_rpc", HypnosiaCategory.Other, "Discord RPC", false),
            ModuleEntry("other.debug_overlay", HypnosiaCategory.Other, "Debug Overlay", false),
            ModuleEntry("other.about", HypnosiaCategory.Other, "About", false),
        ),
    )

    fun create(): FigmaRoot {
        return FigmaRoot(
            designWidth = WINDOW_WIDTH,
            designHeight = WINDOW_HEIGHT,
            child = ShellNode(),
            anchor = RootAnchor.Center,
        )
    }

    private class ShellNode : BaseUiNode(
        LayoutSpec(
            width = SizeMode.Fixed(WINDOW_WIDTH),
            height = SizeMode.Fixed(WINDOW_HEIGHT),
        ),
    ) {
        private var page = Page.Welcome
        private var activeCategory = HypnosiaCategory.Home
        private var selectedModule: ModuleEntry? = null
        private var settingsOpen = false
        private var slideDirection = 1.0f
        private val transition = SpringFloat(1.0f, stiffness = 420.0f, damping = 38.0f)

        private val sidebar = CategorySidebar(
            selectedCategory = { activeCategory },
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
            onSettings = { module, open ->
                if (open) {
                    activeCategory = module.category
                    page = Page.Category
                    restartTransition(1.0f)
                }
                setModuleSettingsOpen(module, open)
            },
        )

        private val homeContent = HomeContentNode()
        private val welcomeContent = WelcomeContentNode()
        private val settingsDrawer = ModuleSettingsDrawerNode(
            module = { selectedModule },
            close = ::closeSettings,
        )

        override fun measure(constraints: Constraints): Size {
            sidebar.measure(Constraints(WINDOW_WIDTH, WINDOW_HEIGHT))
            contentNodes().forEach { it.measure(Constraints(CONTENT_WIDTH, CONTENT_HEIGHT)) }
            settingsDrawer.measure(Constraints(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
            return constraints.constrain(Size(WINDOW_WIDTH, WINDOW_HEIGHT))
        }

        override fun layout(x: Float, y: Float, width: Float, height: Float) {
            super.layout(x, y, WINDOW_WIDTH, WINDOW_HEIGHT)
            sidebar.layout(x, y, CategorySidebar.WIDTH, CategorySidebar.HEIGHT)
            contentNodes().forEach { it.layout(x + CONTENT_X, y + CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT) }
            settingsDrawer.layout(x + WINDOW_WIDTH + 8.0f, y + CONTENT_Y, ModuleSettingsDrawerNode.WIDTH, ModuleSettingsDrawerNode.HEIGHT)
        }

        override fun render(context: DrawContext) {
            renderBase(context)
            sidebar.render(context)
            renderTopBar(context)
            renderContent(context)
            if (settingsOpen) {
                settingsDrawer.render(context)
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
                closeSettings()
                restartTransition(1.0f)
                return true
            }

            if (sidebar.mouseClicked(mouseX, mouseY, button)) {
                return true
            }

            return activeContent().mouseClicked(mouseX, mouseY, button)
        }

        override fun mouseScrolled(
            mouseX: Float,
            mouseY: Float,
            horizontalAmount: Float,
            verticalAmount: Float,
        ): Boolean {
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
        }

        private fun setModuleSettingsOpen(module: ModuleEntry, open: Boolean) {
            modulesByCategory.values.flatten().forEach { entry ->
                if (entry != module) {
                    entry.settingsOpen = false
                }
            }
            module.settingsOpen = open
            selectedModule = if (open) module else null
            settingsOpen = open
        }

        private fun closeSettings() {
            selectedModule?.settingsOpen = false
            selectedModule = null
            settingsOpen = false
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
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = bounds.x,
                y = bounds.y,
                width = WINDOW_WIDTH,
                height = WINDOW_HEIGHT,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
            )
            rect(context, bounds.x - 0.5f, bounds.y + 50.5f, WINDOW_WIDTH, 1.0f, STROKE)
            rect(context, bounds.x + 50.5f, bounds.y + 51.5f, 1.0f, 412.0f, STROKE)
        }

        private fun renderTopBar(context: DrawContext) {
            val searchActive = page == Page.Search
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = bounds.x + 50.5f,
                y = bounds.y + 7.5f,
                width = 166.0f,
                height = 35.0f,
                radius = if (searchActive) 7.0f else 10.0f,
                bgColor = if (searchActive) WHITE else SURFACE,
                strokeColor = if (searchActive) WHITE else STROKE,
                strokeThickness = if (searchActive) 2.0f else 1.0f,
            )
            icon(context, "search.png", bounds.x + 182.5f, bounds.y + 8.5f, 31.0f, 31.0f, if (searchActive) 0xFF0D0D0D.toInt() else WHITE)

            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = bounds.x + 310.5f,
                y = bounds.y + 7.5f,
                width = 75.0f,
                height = 35.0f,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
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

            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = bounds.x + 552.5f,
                y = bounds.y + 7.5f,
                width = 137.0f,
                height = 35.0f,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
            )
            drawTextBox(
                context = context,
                text = "nick",
                x = bounds.x + 556.5f,
                y = bounds.y + 11.5f,
                width = 129.0f,
                height = 12.0f,
                color = WHITE,
                style = FigmaTextRenderer.Styles.Stats,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Top,
            )
            drawTextBox(
                context = context,
                text = "role",
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

        private fun renderContent(context: DrawContext) {
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = bounds.x + CONTENT_X,
                y = bounds.y + CONTENT_Y,
                width = CONTENT_WIDTH,
                height = CONTENT_HEIGHT,
                radius = 10.0f,
                bgColor = SURFACE,
                strokeColor = STROKE,
                strokeThickness = 1.0f,
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
                Page.Category -> categoryGrids.getValue(activeCategory)
            }
        }

        private fun contentNodes(): List<UiNode> {
            return listOf(welcomeContent, homeContent, searchGrid) + categoryGrids.values
        }

        private fun chapterTitle(): String {
            return when (page) {
                Page.Search -> "SEARCH"
                Page.Category -> activeCategory.displayName.uppercase()
                Page.Home -> "HOME"
                Page.Welcome -> "-"
            }
        }
    }

    private enum class Page {
        Welcome,
        Home,
        Search,
        Category,
    }

    private interface FadeNode : UiNode {
        var alpha: Float
    }

    private class ModuleGridNode(
        modules: List<ModuleEntry>,
        onSettings: (ModuleEntry, Boolean) -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        var category: HypnosiaCategory? = null
        override var alpha: Float = 1.0f

        private val moduleRows = mutableListOf<ModuleRow>()
        private val rows = modules.chunked(2).map { rowModules ->
            row(width = SizeMode.Hug, height = SizeMode.Fixed(ModuleRow.HEIGHT), gap = MODULE_GAP_X) {
                rowModules.forEach { module ->
                    val moduleRow = ModuleRow(
                        title = module.title,
                        description = "",
                        iconPath = "plug_socket.png",
                        isActive = module.enabled,
                        isSettingsOpen = { module.settingsOpen },
                        onActiveChanged = { module.enabled = it },
                        onSettingsChanged = { open -> onSettings(module, open) },
                    )
                    moduleRows += moduleRow
                    child(moduleRow)
                }
            }
        }

        private val scroll = scrollColumn(
            width = SizeMode.Fill,
            height = SizeMode.Fill,
            padding = Insets(left = GRID_PAD_X, top = GRID_PAD_Y, right = GRID_PAD_X, bottom = GRID_PAD_Y),
            gap = MODULE_GAP_Y,
            alignment = Alignment.Start,
            scrollStep = ModuleRow.HEIGHT + MODULE_GAP_Y,
            scrollbar = true,
        ) {
            rows.forEach(::child)
        }

        override fun measure(constraints: Constraints): Size {
            scroll.measure(Constraints(CONTENT_WIDTH, CONTENT_HEIGHT))
            return constraints.constrain(Size(CONTENT_WIDTH, CONTENT_HEIGHT))
        }

        override fun layout(x: Float, y: Float, width: Float, height: Float) {
            super<BaseUiNode>.layout(x, y, CONTENT_WIDTH, CONTENT_HEIGHT)
            scroll.layout(x, y, CONTENT_WIDTH, CONTENT_HEIGHT)
        }

        override fun render(context: DrawContext) {
            moduleRows.forEach { it.alpha = alpha }
            scroll.render(context)
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            return scroll.mouseClicked(mouseX, mouseY, button)
        }

        override fun mouseScrolled(mouseX: Float, mouseY: Float, horizontalAmount: Float, verticalAmount: Float): Boolean {
            return scroll.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
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

    private class HomeContentNode : BaseUiNode(LayoutSpec(SizeMode.Fixed(CONTENT_WIDTH), SizeMode.Fixed(CONTENT_HEIGHT))), FadeNode {
        override var alpha: Float = 1.0f

        private var selectedConfig = "Default (Active)"
        private var customName = false

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
            drawField(context, friendPanelX + 14.0f, panelY + 34.0f, 202.0f, 24.0f, "Serch or add...", a)
            drawHelperText(context, "Search or Enter to add", friendPanelX + 14.0f, panelY + 62.0f, 202.0f, a)
            repeat(4) { index ->
                val y = panelY + 94.0f + index * 29.0f
                drawFriendRow(context, friendPanelX + 15.0f, y, 202.0f, "Friend Slot 0${index + 1}", a)
            }

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
            drawField(context, configPanelX + 14.0f, panelY + 34.0f, 311.0f, 24.0f, "Serch or add Cfg", a)
            drawHelperText(context, "Search or Enter to add", configPanelX + 14.0f, panelY + 62.0f, 311.0f, a)
            val rows = listOf(selectedConfig, "Default", "Cfg Save 02", "Cfg Save 03")
            rows.forEachIndexed { index, label ->
                val y = panelY + 94.0f + index * 29.0f
                drawConfigRow(context, configPanelX + 14.0f, y, 311.0f, label, a, showVisibilityIcon = index == 0)
            }
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button != 0) return false
            repeat(4) { index ->
                val y = bounds.y + 105.0f + index * 29.0f
                if (contains(mouseX, mouseY, bounds.x + 266.0f, y, 311.0f, 24.0f)) {
                    selectedConfig = if (index == 0) {
                        if (customName) "Default (Active)" else "Default (Active)"
                    } else {
                        "Cfg Save 0${index + 1} (Active)"
                    }
                    customName = !customName
                    return true
                }
            }
            return false
        }

        private fun drawPanelHeader(context: DrawContext, x: Float, y: Float, width: Float, color: Int, alpha: Float) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, 25.0f, 10.0f, withAlpha(color, alpha))
            HypnosiaRenderUtils.drawFigmaBox(context, x, y + 15.0f, width, 10.0f, 0.0f, withAlpha(color, alpha))
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
        ) {
            drawField(context, x, y, width, 24.0f, label, alpha)
            if (showVisibilityIcon) {
                drawActionIcon(context, "show_file.png", x + 229.0f, y - 1.0f, alpha)
                drawActionIcon(context, "file_edit.png", x + 256.0f, y - 1.0f, alpha)
                drawActionIcon(context, "interface_trash_empty.png", x + 280.0f, y - 1.0f, alpha)
            } else {
                drawActionIcon(context, "file_edit.png", x + 257.0f, y - 1.0f, alpha)
                drawActionIcon(context, "interface_trash_empty.png", x + 281.0f, y - 1.0f, alpha)
            }
        }

        private fun drawActionIcon(context: DrawContext, fileName: String, x: Float, y: Float, alpha: Float) {
            icon(context, fileName, x, y, 24.0f, 24.0f, withAlpha(WHITE, alpha))
        }
    }

    private class ModuleSettingsDrawerNode(
        private val module: () -> ModuleEntry?,
        private val close: () -> Unit,
    ) : BaseUiNode(LayoutSpec(SizeMode.Fixed(WIDTH), SizeMode.Fixed(HEIGHT))) {
        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(WIDTH, HEIGHT))

        override fun render(context: DrawContext) {
            val title = module()?.title ?: "Icons"
            HypnosiaRenderUtils.drawFigmaBox(context, bounds.x, bounds.y, WIDTH, HEIGHT, 12.0f, 0xFF17171B.toInt(), 0xFF2A2A31.toInt(), 1.0f)
            drawText(context, "CLIENT SETTINGS", bounds.x + 15.0f, bounds.y + 13.0f, 10.0f, 0xFF8E8E98.toInt())
            drawText(context, title, bounds.x + 15.0f, bounds.y + 31.0f, 18.0f, WHITE)
            drawButton(context, bounds.x + 177.0f, bounds.y + 17.0f, 40.0f, 26.0f, "Close")
            rect(context, bounds.x + 15.0f, bounds.y + 59.0f, 204.0f, 1.0f, 0xFF2A2A31.toInt())
            drawSettingRow(context, bounds.x + 11.0f, bounds.y + 75.0f, 212.0f, 40.0f, "Apply To", "All GUI", 0xFFBFC0CA.toInt())
            drawSettingRow(context, bounds.x + 11.0f, bounds.y + 127.0f, 212.0f, 48.0f, "Water Icons", "Hide", 0xFFFF2F86.toInt())
            drawSettingRow(context, bounds.x + 11.0f, bounds.y + 187.0f, 212.0f, 48.0f, "Black-Hole", "Keep", 0xFFFF2F86.toInt())
            drawSettingRow(context, bounds.x + 11.0f, bounds.y + 249.0f, 212.0f, 40.0f, "Icon Color", "#F2F2F2", 0xFFBFC0CA.toInt())
            icon(context, "color_preview.png", bounds.x + 187.0f, bounds.y + 261.0f, 14.0f, 14.0f, WHITE)
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button != 0) return false
            if (contains(mouseX, mouseY, bounds.x + 177.0f, bounds.y + 17.0f, 40.0f, 26.0f)) {
                close()
                return true
            }
            return contains(mouseX, mouseY, bounds.x, bounds.y, WIDTH, HEIGHT)
        }

        private fun drawButton(context: DrawContext, x: Float, y: Float, width: Float, height: Float, label: String) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 7.0f, 0xFF1F1F26.toInt(), 0xFF34343C.toInt(), 1.0f)
            drawText(context, label, x + 5.0f, y + 5.0f, 12.0f, 0xFFD6D6DE.toInt())
        }

        private fun drawSettingRow(context: DrawContext, x: Float, y: Float, width: Float, height: Float, label: String, value: String, valueColor: Int) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 9.0f, 0xFF1A1A20.toInt(), 0xFF2A2A31.toInt(), 1.0f)
            drawText(context, label, x + 9.0f, y + if (height == 40.0f) 11.0f else 7.0f, 13.0f, 0xFFE7E7EA.toInt())
            drawText(context, value, x + if (height == 40.0f) 115.0f else 153.0f, y + if (height == 40.0f) 11.0f else 7.0f, 12.0f, valueColor)
        }

        companion object {
            const val WIDTH = 236.0f
            const val HEIGHT = 318.0f
        }
    }

    private data class ModuleEntry(
        val id: String,
        val category: HypnosiaCategory,
        val title: String,
        var enabled: Boolean,
        var settingsOpen: Boolean = false,
    )

    private fun drawPanel(context: DrawContext, x: Float, y: Float, width: Float, height: Float, radius: Float, alpha: Float) {
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, radius, withAlpha(SURFACE, alpha), withAlpha(STROKE, alpha), 1.0f)
    }

    private fun drawField(context: DrawContext, x: Float, y: Float, width: Float, height: Float, label: String, alpha: Float) {
        HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 13.0f, withAlpha(0x00111114, alpha), withAlpha(STROKE, alpha), 1.0f)
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

    private fun withAlpha(color: Int, multiplier: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * multiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
