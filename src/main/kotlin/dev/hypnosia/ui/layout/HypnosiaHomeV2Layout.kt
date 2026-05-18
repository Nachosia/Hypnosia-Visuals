package dev.hypnosia.ui.layout

import dev.hypnosia.HypnosiaClient
import dev.hypnosia.config.HypnosiaConfigProfiles
import dev.hypnosia.ui.animation.AnimatedColor
import dev.hypnosia.ui.animation.AnimatedFloat
import dev.hypnosia.ui.render.FigmaTextRenderer
import dev.hypnosia.ui.render.HypnosiaRenderUtils
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import kotlin.math.abs

object HypnosiaHomeV2Layout {
    private const val WINDOW_WIDTH = 698.0f
    private const val WINDOW_HEIGHT = 567.0f

    private const val SEARCH_X = 0.0f
    private const val SEARCH_Y = 0.0f
    private const val SEARCH_WIDTH = 160.0f
    private const val SEARCH_HEIGHT = 32.0f

    private const val MAIN_X = 0.0f
    private const val MAIN_Y = 40.0f
    private const val MAIN_WIDTH = 698.0f
    private const val MAIN_HEIGHT = 464.0f

    private const val NAV_Y = 520.0f
    private const val SIDE_BUTTON_X = 18.0f
    private const val SIDE_BUTTON_WIDTH = 160.0f
    private const val BOTTOM_BUTTON_HEIGHT = 47.0f
    private const val NAV_X = 194.0f
    private const val NAV_WIDTH = 309.0f
    private const val SIDE_NAV_GAP = 16.0f

    private var selectedNavId = "home"

    fun create(): FigmaRoot {
        HypnosiaConfigProfiles.bootstrap()
        return FigmaRoot(
            designWidth = WINDOW_WIDTH,
            designHeight = WINDOW_HEIGHT,
            child = HomeV2Node(),
            anchor = RootAnchor.Center,
            renderScale = 1.0f,
        )
    }

    private class HomeV2Node : BaseUiNode(
        LayoutSpec(SizeMode.Fixed(WINDOW_WIDTH), SizeMode.Fixed(WINDOW_HEIGHT)),
    ) {
        private val navAnimations = NAV_ITEMS.associate { it.id to NavAnimation() }
        private val navRects = mutableMapOf<String, Rect>()

        override fun measure(constraints: Constraints): Size = constraints.constrain(Size(WINDOW_WIDTH, WINDOW_HEIGHT))

        override fun render(context: DrawContext) {
            renderSearch(context)
            renderMain(context)
            renderBottomBar(context)
        }

        override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
            if (button == 0) {
                NAV_ITEMS.firstOrNull { item ->
                    val rect = navRects[item.id]
                    if (rect != null) {
                        contains(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)
                    } else {
                        contains(mouseX, mouseY, bounds.x + item.buttonX, bounds.y + item.buttonY, BASE_NAV_BUTTON_SIZE, BASE_NAV_BUTTON_SIZE)
                    }
                }?.let { item ->
                    selectedNavId = item.id
                    return true
                }
            }

            return contains(mouseX, mouseY, bounds.x, bounds.y, WINDOW_WIDTH, WINDOW_HEIGHT)
        }

        private fun renderSearch(context: DrawContext) {
            val x = bounds.x + SEARCH_X
            val y = bounds.y + SEARCH_Y
            box(context, x, y, SEARCH_WIDTH, SEARCH_HEIGHT, 10.0f)
            icon(context, "v2_search.png", x + 1.0f, y + 1.0f, 31.0f, 31.0f)
            drawTextBox(
                context = context,
                text = "search",
                x = x + 35.0f,
                y = y + 1.0f,
                width = 117.0f,
                height = 31.0f,
                color = WHITE,
                style = SEARCH_TEXT,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )
        }

        private fun renderMain(context: DrawContext) {
            val x = bounds.x + MAIN_X
            val y = bounds.y + MAIN_Y
            box(context, x, y, MAIN_WIDTH, MAIN_HEIGHT, 10.0f)

            when (selectedNavId) {
                "home" -> renderHomePage(context, x, y)
                "account" -> renderAccountPage(context, x, y)
                else -> renderPlaceholderPage(context, x, y, chapterLabel())
            }
        }

        private fun chapterLabel(): String {
            return NAV_ITEMS.firstOrNull { it.id == selectedNavId }?.label ?: "Home"
        }

        private fun renderBottomBar(context: DrawContext) {
            val y = bounds.y + NAV_Y
            val navLayout = buildNavLayout()
            val sideX = navLayout.dockX - SIDE_NAV_GAP - SIDE_BUTTON_WIDTH
            val profileX = navLayout.dockX + navLayout.dockWidth + SIDE_NAV_GAP

            box(context, sideX, y, SIDE_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT, 10.0f)
            drawTextBox(
                context = context,
                text = chapterLabel(),
                x = sideX + 5.0f,
                y = y + 9.0f,
                width = SIDE_BUTTON_WIDTH - 10.0f,
                height = 30.0f,
                color = WHITE,
                style = BOTTOM_TITLE,
                horizontalAlign = FigmaTextRenderer.HorizontalAlign.Center,
                verticalAlign = FigmaTextRenderer.VerticalAlign.Center,
            )

            box(context, navLayout.dockX, navLayout.dockY, navLayout.dockWidth, navLayout.dockHeight, 10.0f)
            navRects.clear()
            navLayout.items.forEach { visual ->
                navRects[visual.item.id] = visual.rect
                renderNavItem(context, visual)
            }

            box(context, profileX, y, SIDE_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT, 10.0f)
            drawTextBox(context, "Nick", profileX + 7.0f, y + 5.0f, 146.0f, 16.0f, WHITE, PROFILE_TEXT, FigmaTextRenderer.HorizontalAlign.Center, FigmaTextRenderer.VerticalAlign.Center)
            drawTextBox(context, "Role", profileX + 7.0f, y + 26.0f, 146.0f, 16.0f, WHITE, PROFILE_TEXT, FigmaTextRenderer.HorizontalAlign.Center, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun buildNavLayout(): NavLayout {
            val dockHovered = contains(UiInputState.mouseX, UiInputState.mouseY, bounds.x + NAV_X, bounds.y + NAV_Y, NAV_WIDTH, BOTTOM_BUTTON_HEIGHT)
            val seconds = UiInputState.frameSeconds
            val visuals = NAV_ITEMS.map { item ->
                val animation = navAnimations.getValue(item.id)
                val centerX = bounds.x + item.buttonX + BASE_NAV_BUTTON_SIZE * 0.5f
                val targetBoost = if (dockHovered && UiInputState.mouseX.isFinite()) {
                    val distance = abs(UiInputState.mouseX - centerX)
                    val linear = (1.0f - distance / NAV_HOVER_RANGE).coerceIn(0.0f, 1.0f)
                    1.0f - (1.0f - linear) * (1.0f - linear) * (1.0f - linear)
                } else {
                    0.0f
                }

                animation.boost.target = targetBoost
                val boost = animation.boost.update(seconds)
                val iconScale = 1.0f + NAV_ICON_BOOST * boost
                val size = BASE_NAV_BUTTON_SIZE + NAV_MAX_BOOST * boost
                NavVisual(item, Rect(0.0f, 0.0f, size, size), boost, iconScale)
            }

            val totalItemsWidth = visuals.sumOf { it.rect.width.toDouble() }.toFloat() + NAV_ITEM_GAP * (visuals.size - 1).coerceAtLeast(0)
            val dockWidth = maxOf(NAV_WIDTH, totalItemsWidth + NAV_DOCK_PADDING * 2.0f)
            val dockX = bounds.x + NAV_X + (NAV_WIDTH - dockWidth) * 0.5f
            val dockHeight = maxOf(BOTTOM_BUTTON_HEIGHT, visuals.maxOf { it.rect.height } + NAV_DOCK_VERTICAL_PADDING * 2.0f)
            var nextX = dockX + (dockWidth - totalItemsWidth) * 0.5f
            val centerY = bounds.y + NAV_Y + BOTTOM_BUTTON_HEIGHT * 0.5f
            val dockY = centerY - dockHeight * 0.5f

            return NavLayout(
                dockX = dockX,
                dockY = dockY,
                dockWidth = dockWidth,
                dockHeight = dockHeight,
                items = visuals.map { visual ->
                    val rect = Rect(nextX, centerY - visual.rect.height * 0.5f, visual.rect.width, visual.rect.height)
                    nextX += visual.rect.width + NAV_ITEM_GAP
                    visual.copy(rect = rect)
                },
            )
        }

        private fun renderNavItem(context: DrawContext, visual: NavVisual) {
            val item = visual.item
            val animation = navAnimations.getValue(item.id)
            val rect = visual.rect
            val hovered = contains(UiInputState.mouseX, UiInputState.mouseY, rect.x, rect.y, rect.width, rect.height)
            val selected = selectedNavId == item.id
            val active = hovered || selected
            val seconds = UiInputState.frameSeconds

            animation.stroke.target = when {
                selected -> WHITE
                hovered -> HOVER_WHITE
                else -> STROKE
            }
            animation.iconTint.target = when {
                selected -> WHITE
                hovered -> HOVER_WHITE
                else -> WHITE
            }
            animation.strokeWidth.target = if (active) 2.0f else 1.0f

            val iconWidth = item.iconWidth * visual.iconScale
            val iconHeight = item.iconHeight * visual.iconScale
            val iconCenterX = rect.x + rect.width * 0.5f
            val iconCenterY = rect.y + rect.height * 0.5f
            val iconFile = if (active) item.hoverIcon else item.icon

            HypnosiaRenderUtils.drawFigmaBox(
                context,
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                10.0f,
                SURFACE,
                animation.stroke.update(seconds),
                animation.strokeWidth.update(seconds),
            )
            icon(
                context = context,
                fileName = iconFile,
                x = iconCenterX - iconWidth * 0.5f,
                y = iconCenterY - iconHeight * 0.5f,
                width = iconWidth,
                height = iconHeight,
                tint = animation.iconTint.update(seconds),
            )
        }

        private fun renderHomePage(context: DrawContext, x: Float, y: Float) {
            val contentX = x + CONTENT_PADDING
            val contentY = y + CONTENT_PADDING
            val contentH = MAIN_HEIGHT - CONTENT_PADDING * 2.0f
            val columnW = 300.0f
            val dividerX = contentX + columnW + 24.0f
            val rightX = dividerX + 25.0f

            renderHomeFriendsColumn(context, contentX, contentY, columnW, contentH)
            rect(context, dividerX, contentY, 1.0f, contentH, DIVIDER)
            renderHomeConfigsColumn(context, rightX, contentY, columnW, contentH)
        }

        private fun renderHomeFriendsColumn(context: DrawContext, x: Float, y: Float, width: Float, height: Float) {
            drawLabel(context, "FRIENDS", x, y, width)
            renderInput(context, x, y + 27.0f, width, "Search or add...")
            drawSmall(context, "Press Enter to add", x + 1.0f, y + 60.0f, width, MUTED_DARK)

            val names = listOf("Username123", "GhostPlayer", "Ninja_Assasin", "SniperPro2000", "DarkKnight", "ShadowWalker", "LoneWolf")
            var rowY = y + 82.0f
            names.forEach { name ->
                renderTextRow(context, name, x, rowY, width, active = false, actions = listOf("-"))
                rowY += 34.0f
                if (rowY + 28.0f > y + height) return@forEach
            }
        }

        private fun renderHomeConfigsColumn(context: DrawContext, x: Float, y: Float, width: Float, height: Float) {
            drawLabel(context, "CONFIG MANAGER", x, y, width)
            renderInput(context, x, y + 27.0f, width, "Search or add Cfg...")
            drawSmall(context, "Press Enter to add", x + 1.0f, y + 60.0f, width, MUTED_DARK)

            val configs = listOf("Default", "Legit_V1", "Rage_Testing", "HVH_Main", "Backup_Settings")
            var rowY = y + 82.0f
            configs.forEachIndexed { index, config ->
                renderTextRow(context, config, x, rowY, width, active = index == 0, actions = if (index == 0) listOf("o", "S", "x") else listOf("S", "x"))
                rowY += 34.0f
                if (rowY + 28.0f > y + height) return@forEach
            }
        }

        private fun renderAccountPage(context: DrawContext, x: Float, y: Float) {
            val contentX = x + CONTENT_PADDING
            val contentY = y + CONTENT_PADDING
            val contentH = MAIN_HEIGHT - CONTENT_PADDING * 2.0f
            val profileW = 300.0f
            val dividerX = contentX + profileW
            val rightX = dividerX + 24.0f
            val rightW = x + MAIN_WIDTH - CONTENT_PADDING - rightX

            renderAccountProfile(context, contentX, contentY, profileW - 24.0f, contentH)
            rect(context, dividerX, contentY, 1.0f, contentH, DIVIDER)
            renderAccountConfigs(context, rightX, contentY, rightW, contentH)
        }

        private fun renderAccountProfile(context: DrawContext, x: Float, y: Float, width: Float, height: Float) {
            drawLabel(context, "ПРОФИЛЬ АККАУНТА", x, y, width)
            renderKeyValue(context, "ID Аккаунта:", "1", x, y + 32.0f, width)
            renderKeyValue(context, "Создан:", "2026-04-19", x, y + 58.0f, width)
            rect(context, x, y + 88.0f, width, 1.0f, DIVIDER)
            drawTextBox(context, "Роли:", x, y + 104.0f, width, 14.0f, MUTED_DARK, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)

            var chipX = x
            listOf(
                "Owner" to 0xFFFFD700.toInt(),
                "Admin" to 0xFFFF5555.toInt(),
                "QA" to 0xFF00E5FF.toInt(),
                "SPONSOR" to 0xFFFFA500.toInt(),
                "USER" to TEXT_MUTED,
            ).forEach { (role, color) ->
                val chipW = if (role == "SPONSOR") 58.0f else 42.0f
                fieldBox(context, chipX, y + 128.0f, chipW, 17.0f, 4.0f, FIELD, BORDER_DARK, 1.0f)
                drawTextBox(context, role, chipX + 3.0f, y + 129.0f, chipW - 6.0f, 14.0f, color, CHIP_TEXT, FigmaTextRenderer.HorizontalAlign.Center, FigmaTextRenderer.VerticalAlign.Center)
                chipX += chipW + 6.0f
            }

            renderPlainInput(context, x, y + 166.0f, width, "Введите имя...")
            renderPlainInput(context, x, y + 206.0f, width, "Введите контакт...")
            drawSmall(context, "Без привязанного аккаунта TG или DC", x, y + height - 72.0f, width, MUTED_DARK)
            drawSmall(context, "восстановление невозможно.", x, y + height - 58.0f, width, MUTED_DARK)
            drawSmall(context, "Для переноса обращайтесь:", x, y + height - 36.0f, width, MUTED_DARK)
            drawSmall(context, "DS: nachosia", x, y + height - 20.0f, width * 0.5f, TEXT_MUTED)
            drawSmall(context, "TG: @Hypnosia_NSXS", x + width * 0.52f, y + height - 20.0f, width * 0.48f, TEXT_MUTED)
        }

        private fun renderAccountConfigs(context: DrawContext, x: Float, y: Float, width: Float, height: Float) {
            val topH = (height - 17.0f) * 0.5f
            drawLabel(context, "ЛОКАЛЬНЫЕ КОНФИГИ", x, y, width)
            renderPlainInput(context, x, y + 27.0f, width, "Вставьте ключ облака...")
            drawTinyAction(context, "link", x + width - 27.0f, y + 34.0f, 16.0f, MUTED_DARK)
            var rowY = y + 72.0f
            listOf("default.cfg", "legit_v2.cfg", "rage_main.cfg", "hvh_secret.cfg").forEach { config ->
                renderTextRow(context, config, x, rowY, width, active = false, actions = listOf("ok", "x"), compact = true)
                rowY += 28.0f
                if (rowY + 24.0f > y + topH) return@forEach
            }

            val dividerY = y + topH + 8.0f
            rect(context, x, dividerY, width, 1.0f, DIVIDER)
            drawLabel(context, "ОБЛАЧНЫЕ КОНФИГИ", x, dividerY + 18.0f, width - 50.0f)
            drawSmall(context, "10/100", x + width - 42.0f, dividerY + 18.0f, 42.0f, MUTED_DARK, FigmaTextRenderer.HorizontalAlign.Right)
            rowY = dividerY + 48.0f
            listOf("Cloud key 1", "Cloud key 2", "Cloud key 3", "Cloud key 4", "Cloud key 5").forEach { config ->
                renderTextRow(context, config, x, rowY, width, active = false, actions = listOf("C", "x"), compact = true)
                rowY += 28.0f
                if (rowY + 24.0f > y + height) return@forEach
            }
        }

        private fun renderPlaceholderPage(context: DrawContext, x: Float, y: Float, title: String) {
            drawTextBox(context, title, x, y + 185.0f, MAIN_WIDTH, 42.0f, WHITE, BOTTOM_TITLE, FigmaTextRenderer.HorizontalAlign.Center, FigmaTextRenderer.VerticalAlign.Center)
            drawTextBox(context, "Coming soon", x, y + 232.0f, MAIN_WIDTH, 20.0f, TEXT_MUTED, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Center, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun renderInput(context: DrawContext, x: Float, y: Float, width: Float, placeholder: String) {
            fieldBox(context, x, y, width, 28.0f, 8.0f, FIELD_ALT, 0x00000000, 0.0f)
            icon(context, "v2_search.png", x + 6.0f, y + 6.0f, 16.0f, 16.0f, tint = TEXT_MUTED)
            drawTextBox(context, placeholder, x + 29.0f, y + 1.0f, width - 34.0f, 26.0f, MUTED_DARK, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun renderPlainInput(context: DrawContext, x: Float, y: Float, width: Float, placeholder: String) {
            fieldBox(context, x, y, width, 28.0f, 8.0f, FIELD, 0x00000000, 0.0f)
            drawTextBox(context, placeholder, x + 13.0f, y + 1.0f, width - 26.0f, 26.0f, MUTED_DARK, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun renderTextRow(
            context: DrawContext,
            text: String,
            x: Float,
            y: Float,
            width: Float,
            active: Boolean,
            actions: List<String>,
            compact: Boolean = false,
        ) {
            val rowH = if (compact) 25.0f else 30.0f
            val hovered = contains(UiInputState.mouseX, UiInputState.mouseY, x, y, width, rowH)
            val bg = when {
                active -> 0x801F1F1F.toInt()
                hovered -> HOVER_FIELD
                else -> 0x00000000
            }
            val stroke = if (active) 0x55333333 else 0x00000000
            fieldBox(context, x, y, width, rowH, 6.0f, bg, stroke, if (active) 1.0f else 0.0f)
            drawTextBox(context, text, x + 10.0f, y + 1.0f, width - 76.0f, rowH - 2.0f, if (active) WHITE else TEXT_MAIN, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)
            if (hovered || active) {
                var actionX = x + width - 18.0f * actions.size - 5.0f
                actions.forEach { action ->
                    drawTinyAction(context, action, actionX, y + (rowH - 16.0f) * 0.5f, 16.0f, if (action == "x") DANGER else TEXT_MUTED)
                    actionX += 18.0f
                }
            }
        }

        private fun renderKeyValue(context: DrawContext, label: String, value: String, x: Float, y: Float, width: Float) {
            drawTextBox(context, label, x, y, width * 0.55f, 18.0f, MUTED_DARK, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)
            drawTextBox(context, value, x + width * 0.45f, y, width * 0.55f, 18.0f, TEXT_MAIN, BODY_TEXT, FigmaTextRenderer.HorizontalAlign.Right, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun drawLabel(context: DrawContext, text: String, x: Float, y: Float, width: Float) {
            drawTextBox(context, text, x, y, width, 16.0f, TEXT_MUTED, HEADER_TEXT, FigmaTextRenderer.HorizontalAlign.Left, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun drawSmall(
            context: DrawContext,
            text: String,
            x: Float,
            y: Float,
            width: Float,
            color: Int,
            align: FigmaTextRenderer.HorizontalAlign = FigmaTextRenderer.HorizontalAlign.Left,
        ) {
            drawTextBox(context, text, x, y, width, 12.0f, color, SMALL_TEXT, align, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun drawTinyAction(context: DrawContext, label: String, x: Float, y: Float, size: Float, color: Int) {
            drawTextBox(context, label, x, y, size, size, color, SMALL_TEXT, FigmaTextRenderer.HorizontalAlign.Center, FigmaTextRenderer.VerticalAlign.Center)
        }

        private fun rect(context: DrawContext, x: Float, y: Float, width: Float, height: Float, color: Int) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, 0.0f, color)
        }

        private fun fieldBox(context: DrawContext, x: Float, y: Float, width: Float, height: Float, radius: Float, bg: Int, stroke: Int, strokeWidth: Float) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, radius, bg, stroke, strokeWidth)
        }

        private fun box(context: DrawContext, x: Float, y: Float, width: Float, height: Float, radius: Float) {
            HypnosiaRenderUtils.drawFigmaBox(context, x, y, width, height, radius, SURFACE, STROKE, 1.0f)
        }

        private fun icon(
            context: DrawContext,
            fileName: String,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            tint: Int = WHITE,
        ) {
            val identifier = Identifier.of(HypnosiaClient.MOD_ID, "textures/gui/v2/icons/$fileName")
            HypnosiaRenderUtils.drawRoundedTexture(context, identifier, x, y, width, height, 0.0f, tint)
        }

        private fun welcomeAsset(
            context: DrawContext,
            fileName: String,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ) {
            val identifier = Identifier.of(HypnosiaClient.MOD_ID, "textures/gui/v2/welcome/$fileName")
            HypnosiaRenderUtils.drawRoundedTexture(context, identifier, x, y, width, height, 0.0f, WHITE)
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

        private fun contains(mouseX: Float, mouseY: Float, x: Float, y: Float, width: Float, height: Float): Boolean {
            return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height
        }
    }

    private data class NavItem(
        val id: String,
        val label: String,
        val buttonX: Float,
        val buttonY: Float,
        val icon: String,
        val hoverIcon: String,
        val iconX: Float,
        val iconY: Float,
        val iconWidth: Float,
        val iconHeight: Float,
    )

    private data class NavVisual(
        val item: NavItem,
        val rect: Rect,
        val boost: Float,
        val iconScale: Float,
    )

    private data class NavLayout(
        val dockX: Float,
        val dockY: Float,
        val dockWidth: Float,
        val dockHeight: Float,
        val items: List<NavVisual>,
    )

    private class NavAnimation {
        val boost = AnimatedFloat(0.0f, speed = 18.0f)
        val stroke = AnimatedColor(STROKE, speed = 18.0f)
        val iconTint = AnimatedColor(WHITE, speed = 18.0f)
        val strokeWidth = AnimatedFloat(1.0f, speed = 18.0f)
    }

    private val NAV_ITEMS = listOf(
        NavItem("home", "Home", 202.0f, 526.0f, "v2_home.png", "v2_home_hover.png", 204.0f, 528.0f, 31.0f, 31.0f),
        NavItem("visuals", "Visuals", 245.0f, 526.0f, "v2_visuals.png", "v2_visuals_hover.png", 249.0f, 530.0f, 27.0f, 27.0f),
        NavItem("world", "World", 288.0f, 526.0f, "v2_world.png", "v2_world_hover.png", 290.0f, 528.0f, 31.0f, 31.0f),
        NavItem("client", "Client", 331.0f, 526.0f, "v2_client.png", "v2_client_hover.png", 333.0f, 528.0f, 31.0f, 31.0f),
        NavItem("hud", "Hud", 374.0f, 526.0f, "v2_hud.png", "v2_hud_hover.png", 380.0f, 532.0f, 23.0f, 23.0f),
        NavItem("other", "Other", 417.0f, 526.0f, "v2_other.png", "v2_other_hover.png", 421.0f, 531.0f, 24.0f, 25.0f),
        NavItem("account", "Account", 460.0f, 526.0f, "v2_account_cloud.png", "v2_account_cloud_hover.png", 465.0f, 531.0f, 24.0f, 24.0f),
    )

    private const val BASE_NAV_BUTTON_SIZE = 35.0f
    private const val NAV_ITEM_GAP = 8.0f
    private const val NAV_DOCK_PADDING = 8.0f
    private const val NAV_DOCK_VERTICAL_PADDING = 6.0f
    private const val NAV_MAX_BOOST = 10.0f
    private const val NAV_ICON_BOOST = 0.18f
    private const val NAV_HOVER_RANGE = 108.0f
    private const val CONTENT_PADDING = 24.0f
    private const val WELCOME_CONTENT_WIDTH = 604.0f
    private const val WELCOME_CONTENT_HEIGHT = 370.0f
    private const val SURFACE = 0xFF0D0D0D.toInt()
    private const val STROKE = 0xFF272727.toInt()
    private const val HOVER_WHITE = 0xFFE9E9E9.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val FIELD = 0xFF151515.toInt()
    private const val FIELD_ALT = 0xFF222222.toInt()
    private const val HOVER_FIELD = 0xFF2A2A2A.toInt()
    private const val DIVIDER = 0x801A1A1A.toInt()
    private const val BORDER_DARK = 0xFF2A2A2A.toInt()
    private const val TEXT_MAIN = 0xFFE5E5E5.toInt()
    private const val TEXT_MUTED = 0xFF888888.toInt()
    private const val MUTED_DARK = 0xFF666666.toInt()
    private const val DANGER = 0xFFEF4444.toInt()

    private val SEARCH_TEXT = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 20.0f, 24.204544f, baselineOffset = 2.0f)
    private val BOTTOM_TITLE = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 24.0f, 29.045454f, baselineOffset = 3.0f)
    private val PROFILE_TEXT = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 16.0f, 19.363636f, baselineOffset = 2.0f)
    private val HEADER_TEXT = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 11.0f, 14.0f, baselineOffset = 1.0f)
    private val BODY_TEXT = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 13.0f, 16.0f, baselineOffset = 1.0f)
    private val SMALL_TEXT = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 10.0f, 12.0f, baselineOffset = 1.0f)
    private val CHIP_TEXT = FigmaTextRenderer.FigmaTextStyle(FigmaTextRenderer.Font.Main, 9.0f, 11.0f, baselineOffset = 0.5f)
}
