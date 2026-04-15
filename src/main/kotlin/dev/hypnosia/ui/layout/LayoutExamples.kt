package dev.hypnosia.ui.layout

import dev.hypnosia.ui.render.HypnosiaRenderUtils
import net.minecraft.client.gui.DrawContext

object LayoutExamples {
    fun sidebarButtons(): FigmaRoot {
        val sidebar = column(
            width = SizeMode.Fixed(228.0f),
            height = SizeMode.Hug,
            padding = Insets.symmetric(horizontal = 16.0f, vertical = 16.0f),
            gap = 12.0f,
            alignment = Alignment.Stretch,
        ) {
            child(button("Visuals"))
            child(button("World"))
            child(button("Client"))
        }

        return FigmaRoot(
            designWidth = 260.0f,
            designHeight = 520.0f,
            child = sidebar,
            anchor = RootAnchor.Center,
        )
    }

    private fun button(label: String): UiNode {
        return FigmaBoxNode(
            preferredWidth = 196.0f,
            preferredHeight = 46.0f,
            renderer = { context: DrawContext, rect: Rect ->
            HypnosiaRenderUtils.drawSdfShadowBox(
                context = context,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                radius = 10.0f,
                shadowSpread = 0.0f,
                shadowBlur = 10.0f,
                color = 0x44000000,
            )
            HypnosiaRenderUtils.drawFigmaBox(
                context = context,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                radius = 10.0f,
                bgColor = 0xFF191919.toInt(),
                strokeColor = 0xFF2A2A31.toInt(),
                strokeThickness = 1.0f,
            )
            context.drawText(
                MinecraftAccess.textRenderer(),
                label,
                (rect.x + 14.0f).toInt(),
                (rect.y + 17.0f).toInt(),
                0xFFF0F0F2.toInt(),
                false,
            )
            },
        )
    }
}

private object MinecraftAccess {
    fun textRenderer() = net.minecraft.client.MinecraftClient.getInstance().textRenderer
}
