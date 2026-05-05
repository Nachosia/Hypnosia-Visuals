package dev.hypnosia.ui.render

import dev.hypnosia.HypnosiaClient
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Style
import net.minecraft.text.StyleSpriteSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.round

object FigmaTextRenderer {
    enum class Font(val id: Identifier, val baseSize: Float) {
        Main(Identifier.of(HypnosiaClient.MOD_ID, "main"), 12.0f),
        Title(Identifier.of(HypnosiaClient.MOD_ID, "title"), 12.0f),
    }

    enum class HorizontalAlign {
        Left,
        Center,
        Right,
    }

    enum class VerticalAlign {
        Top,
        Center,
        Bottom,
    }

    data class FigmaTextStyle(
        val font: Font,
        val size: Float,
        val lineHeight: Float,
        val letterSpacing: Float = 0.0f,
        val baselineOffset: Float = 0.0f,
    )

    object Styles {
        val ModuleTitle = FigmaTextStyle(
            font = Font.Main,
            size = 12.0f,
            lineHeight = 16.0f,
            letterSpacing = 0.0f,
            baselineOffset = 1.0f,
        )

        val Ui10 = FigmaTextStyle(Font.Main, 10.0f, 12.0f, baselineOffset = 1.0f)
        val Ui12 = FigmaTextStyle(Font.Main, 12.0f, 14.0f, baselineOffset = 1.0f)
        val Ui13 = FigmaTextStyle(Font.Main, 13.0f, 16.0f, baselineOffset = 1.0f)
        val Ui14 = FigmaTextStyle(Font.Main, 14.0f, 18.0f, baselineOffset = 1.0f)
        val Ui16 = FigmaTextStyle(Font.Main, 16.0f, 20.0f, baselineOffset = 2.0f)
        val Ui18 = FigmaTextStyle(Font.Main, 18.0f, 22.0f, baselineOffset = 2.0f)
        val Helper = FigmaTextStyle(Font.Main, 9.0f, 12.0f, baselineOffset = 1.0f)
        val Chapter = FigmaTextStyle(Font.Main, 14.0f, 24.0f, baselineOffset = 1.0f)
        val Stats = FigmaTextStyle(Font.Main, 12.0f, 11.0f, baselineOffset = 1.0f)
        val HomeHeader = FigmaTextStyle(Font.Main, 12.0f, 18.0f, baselineOffset = 2.0f)
        val HomeField = FigmaTextStyle(Font.Main, 16.0f, 20.0f, baselineOffset = 2.0f)

        val WelcomeTitle = FigmaTextStyle(
            font = Font.Title,
            size = 64.0f,
            lineHeight = 11.0f,
            letterSpacing = 0.0f,
            baselineOffset = -22.0f,
        )
    }

    fun draw(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        font: Font = Font.Main,
    ) {
        draw(
            context = context,
            text = text,
            x = x,
            y = y,
            color = color,
            style = FigmaTextStyle(
                font = font,
                size = size,
                lineHeight = size * 1.2f,
            ),
        )
    }

    fun drawCentered(
        context: DrawContext,
        text: String,
        centerX: Float,
        y: Float,
        size: Float,
        color: Int,
        font: Font = Font.Main,
    ) {
        draw(
            context = context,
            text = text,
            x = centerX - width(text, size, font) * 0.5f,
            y = y,
            size = size,
            color = color,
            font = font,
        )
    }

    fun draw(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        style: FigmaTextStyle,
    ) {
        drawRaw(
            context = context,
            text = text,
            x = x,
            y = y + style.baselineOffset,
            color = color,
            style = style,
        )
    }

    fun drawInBox(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        style: FigmaTextStyle,
        horizontalAlign: HorizontalAlign = HorizontalAlign.Left,
        verticalAlign: VerticalAlign = VerticalAlign.Top,
    ) {
        val textWidth = width(text, style)
        val drawXRaw = when (horizontalAlign) {
            HorizontalAlign.Left -> x
            HorizontalAlign.Center -> x + (width - textWidth) * 0.5f
            HorizontalAlign.Right -> x + width - textWidth
        }
        val drawYRaw = when (verticalAlign) {
            VerticalAlign.Top -> y
            VerticalAlign.Center -> y + (height - style.lineHeight) * 0.5f
            VerticalAlign.Bottom -> y + height - style.lineHeight
        }
        draw(context, text, drawXRaw, drawYRaw, color, style)
    }

    fun width(text: String, size: Float, font: Font = Font.Main): Float {
        HighQualityTextRenderer.width(
            text,
            FigmaTextStyle(
                font = font,
                size = size,
                lineHeight = size * 1.2f,
            ),
        )?.let { return it }

        val scale = size / font.baseSize
        return MinecraftClient.getInstance().textRenderer.getWidth(styled(text, font)) * scale
    }

    fun width(text: String, style: FigmaTextStyle): Float {
        HighQualityTextRenderer.width(text, style)?.let { return it }
        val baseWidth = width(text, style.size, style.font)
        val gaps = (text.length - 1).coerceAtLeast(0)
        return baseWidth + gaps * style.letterSpacing
    }

    private fun drawRaw(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        style: FigmaTextStyle,
    ) {
        if (text.isEmpty()) {
            return
        }

        val drawX = snapHalf(x)
        val drawY = snapHalf(y)

        if (HighQualityTextRenderer.draw(context, text, drawX, drawY, color, style)) {
            return
        }

        if (style.letterSpacing == 0.0f) {
            drawMinecraftFont(context, text, drawX, drawY, style.size, color, style.font)
            return
        }

        var cursorX = drawX
        text.forEach { char ->
            val glyph = char.toString()
            drawMinecraftFont(context, glyph, snapHalf(cursorX), drawY, style.size, color, style.font)
            cursorX += width(glyph, style.size, style.font) + style.letterSpacing
        }
    }

    private fun snapHalf(value: Float): Float =
        round(value * 2.0f) * 0.5f

    private fun drawMinecraftFont(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        font: Font,
    ) {
        val scale = size / font.baseSize
        context.matrices.pushMatrix()
        context.matrices.translate(x, y)
        context.matrices.scale(scale, scale)
        context.drawText(
            MinecraftClient.getInstance().textRenderer,
            styled(text, font),
            0,
            0,
            color,
            false,
        )
        context.matrices.popMatrix()
    }

    private fun styled(text: String, font: Font): Text {
        return Text.literal(text).setStyle(Style.EMPTY.withFont(StyleSpriteSource.Font(font.id)))
    }
}
