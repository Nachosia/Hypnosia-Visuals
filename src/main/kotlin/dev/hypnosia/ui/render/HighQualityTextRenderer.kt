package dev.hypnosia.ui.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexFormat
import dev.hypnosia.HypnosiaClient
import dev.hypnosia.render.HypnosiaShaders
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gl.GpuSampler
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.ProjectionMatrix2
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.min
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

object HighQualityTextRenderer {
    private const val GUI_MODEL_VIEW_Z = -11000.0f
    private const val GUI_VERTEX_Z = 0.0f
    private const val ATLAS_SCALE = 4.0f
    private const val ATLAS_WIDTH = 2048
    private const val SDF_SPREAD = 8
    private const val GLYPH_PADDING = SDF_SPREAD + 2

    private val guiProjection = ProjectionMatrix2("hypnosia_hq_text_gui", 1000.0f, 11000.0f, true)
    private val atlases = mutableMapOf<AtlasKey, FontAtlas?>()
    private val baseFonts = mutableMapOf<FigmaTextRenderer.Font, Font?>()
    private var linearTextSampler: GpuSampler? = null

    fun draw(
        context: DrawContext,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        style: FigmaTextRenderer.FigmaTextStyle,
    ): Boolean {
        if (text.isEmpty()) {
            return true
        }

        val atlas = atlas(style) ?: return false
        val guiMatrix = createGuiMatrix(context)
        val baselineY = y + atlas.ascent
        var cursorX = x
        val queuedGlyphs = mutableListOf<QueuedGlyph>()

        text.forEach { char ->
            val glyph = atlas.glyphs[char] ?: atlas.glyphs[atlas.fallbackGlyph]
            if (glyph == null) {
                cursorX += style.size * 0.5f + style.letterSpacing
                return@forEach
            }

            if (!char.isWhitespace() && glyph.width > 0.0f && glyph.height > 0.0f) {
                queuedGlyphs += QueuedGlyph(glyph, cursorX + glyph.offsetX, baselineY + glyph.offsetY)
            }

            cursorX += glyph.advance + style.letterSpacing
        }

        if (queuedGlyphs.isEmpty()) {
            return true
        }

        context.drawDeferredElements()
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR)
        queuedGlyphs.forEach { queued ->
            val glyph = queued.glyph
            val gx = queued.x
            val gy = queued.y
            putVertex(buffer, guiMatrix, gx, gy, glyph.u0, glyph.v0, color)
            putVertex(buffer, guiMatrix, gx, gy + glyph.height, glyph.u0, glyph.v1, color)
            putVertex(buffer, guiMatrix, gx + glyph.width, gy + glyph.height, glyph.u1, glyph.v1, color)
            putVertex(buffer, guiMatrix, gx + glyph.width, gy, glyph.u1, glyph.v0, color)
        }

        val builtBuffer = buffer.end()
        val vertexBuffer = createOwnedVertexBuffer("Hypnosia HQ text vertices", builtBuffer)
        try {
            renderImmediate(context, vertexBuffer, atlas.texture, queuedGlyphs.size)
        } finally {
            vertexBuffer.close()
            builtBuffer.close()
            context.drawDeferredElements()
        }

        return true
    }

    fun width(text: String, style: FigmaTextRenderer.FigmaTextStyle): Float? {
        if (text.isEmpty()) {
            return 0.0f
        }
        val atlas = atlas(style) ?: return null
        var width = 0.0f
        text.forEachIndexed { index, char ->
            width += (atlas.glyphs[char] ?: atlas.glyphs[atlas.fallbackGlyph])?.advance ?: style.size * 0.5f
            if (index != text.lastIndex) {
                width += style.letterSpacing
            }
        }
        return width
    }

    fun prewarmCommonAtlases(): Int {
        val styles = listOf(
            FigmaTextRenderer.Font.Main to 8.5f,
            FigmaTextRenderer.Font.Main to 9.0f,
            FigmaTextRenderer.Font.Main to 9.5f,
            FigmaTextRenderer.Font.Main to 10.0f,
            FigmaTextRenderer.Font.Main to 10.5f,
            FigmaTextRenderer.Font.Main to 11.0f,
            FigmaTextRenderer.Font.Main to 12.0f,
            FigmaTextRenderer.Font.Main to 13.0f,
            FigmaTextRenderer.Font.Main to 14.0f,
            FigmaTextRenderer.Font.Main to 15.0f,
            FigmaTextRenderer.Font.Main to 16.0f,
            FigmaTextRenderer.Font.Main to 18.0f,
            FigmaTextRenderer.Font.Main to 20.0f,
            FigmaTextRenderer.Font.Main to 24.0f,
            FigmaTextRenderer.Font.Title to 24.0f,
            FigmaTextRenderer.Font.Title to 32.0f,
            FigmaTextRenderer.Font.Title to 64.0f,
        )
            .distinctBy { (font, size) -> AtlasKey(font, normalizeSize(size)) }

        var warmed = 0
        styles.forEach { (font, size) ->
            val style = FigmaTextRenderer.FigmaTextStyle(font, size, size)
            if (atlas(style) != null) {
                warmed++
            }
        }
        return warmed
    }

    private fun atlas(style: FigmaTextRenderer.FigmaTextStyle): FontAtlas? {
        val key = AtlasKey(style.font, normalizeSize(style.size))
        if (atlases.containsKey(key)) {
            return atlases[key]
        }

        val built = runCatching { buildAtlas(key) }.getOrNull()
        atlases[key] = built
        return built
    }

    private fun buildAtlas(key: AtlasKey): FontAtlas {
        val baseFont = baseFont(key.font)
            ?: throw IllegalStateException("Missing base font resource for ${key.font}")
        val awtFont = baseFont.deriveFont(key.size * ATLAS_SCALE)
        val frc = FontRenderContext(null, true, true)
        val lineMetrics = awtFont.getLineMetrics("Ag", frc)
        val ascent = lineMetrics.ascent / ATLAS_SCALE
        val fallbackGlyph = if (awtFont.canDisplay('?')) '?' else glyphChars().firstOrNull { awtFont.canDisplay(it) } ?: ' '

        val glyphSpecs = glyphChars()
            .filter { awtFont.canDisplay(it) }
            .let { chars -> if (fallbackGlyph in chars) chars else chars + fallbackGlyph }
            .distinct()
            .mapNotNull { char ->
                val vector = awtFont.createGlyphVector(frc, char.toString())
                val bounds = vector.getPixelBounds(frc, 0.0f, 0.0f)
                val advance = vector.getGlyphMetrics(0).advanceX / ATLAS_SCALE
                if (char.isWhitespace() || bounds.width <= 0 || bounds.height <= 0) {
                    GlyphSpec(char, vector, bounds.x, bounds.y, 0, 0, advance)
                } else {
                    GlyphSpec(
                        char = char,
                        vector = vector,
                        boundsX = bounds.x,
                        boundsY = bounds.y,
                        width = bounds.width + GLYPH_PADDING * 2,
                        height = bounds.height + GLYPH_PADDING * 2,
                        advance = advance,
                    )
                }
            }

        var penX = 0
        var penY = 0
        var rowHeight = 0
        val placed = mutableListOf<PlacedGlyph>()

        glyphSpecs.forEach { glyph ->
            if (glyph.width == 0 || glyph.height == 0) {
                placed += PlacedGlyph(glyph, 0, 0)
                return@forEach
            }
            if (penX + glyph.width > ATLAS_WIDTH) {
                penX = 0
                penY += rowHeight
                rowHeight = 0
            }
            placed += PlacedGlyph(glyph, penX, penY)
            penX += glyph.width
            rowHeight = max(rowHeight, glyph.height)
        }

        val atlasHeight = max(1, nextPowerOfTwo(penY + rowHeight))
        val atlasImage = BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        placed.forEach { placedGlyph ->
            val glyph = placedGlyph.glyph
            if (glyph.width > 0 && glyph.height > 0) {
                drawGlyphSdf(atlasImage, placedGlyph)
            }
        }

        val nativeImage = NativeImage(NativeImage.Format.RGBA, ATLAS_WIDTH, atlasHeight, false)
        for (yy in 0 until atlasHeight) {
            for (xx in 0 until ATLAS_WIDTH) {
                nativeImage.setColorArgb(xx, yy, atlasImage.getRGB(xx, yy))
            }
        }

        val texture = NativeImageBackedTexture({ "Hypnosia HQ text atlas ${key.font.name} ${key.size}" }, nativeImage)
        texture.upload()

        val glyphs = placed.associate { placedGlyph ->
            val glyph = placedGlyph.glyph
            if (glyph.width <= 0 || glyph.height <= 0) {
                glyph.char to Glyph(
                    advance = glyph.advance,
                    offsetX = 0.0f,
                    offsetY = 0.0f,
                    width = 0.0f,
                    height = 0.0f,
                    u0 = 0.0f,
                    v0 = 0.0f,
                    u1 = 0.0f,
                    v1 = 0.0f,
                )
            } else {
                glyph.char to Glyph(
                    advance = glyph.advance,
                    offsetX = (glyph.boundsX - GLYPH_PADDING) / ATLAS_SCALE,
                    offsetY = (glyph.boundsY - GLYPH_PADDING) / ATLAS_SCALE,
                    width = glyph.width / ATLAS_SCALE,
                    height = glyph.height / ATLAS_SCALE,
                    u0 = placedGlyph.x.toFloat() / ATLAS_WIDTH,
                    v0 = placedGlyph.y.toFloat() / atlasHeight,
                    u1 = (placedGlyph.x + glyph.width).toFloat() / ATLAS_WIDTH,
                    v1 = (placedGlyph.y + glyph.height).toFloat() / atlasHeight,
                )
            }
        }

        return FontAtlas(texture, glyphs, ascent, fallbackGlyph)
    }

    private fun baseFont(font: FigmaTextRenderer.Font): Font? {
        if (baseFonts.containsKey(font)) {
            return baseFonts[font]
        }

        val client = MinecraftClient.getInstance()
        val fontId = when (font) {
            FigmaTextRenderer.Font.Main -> Identifier.of(HypnosiaClient.MOD_ID, "font/inter_medium.ttf")
            FigmaTextRenderer.Font.Title -> Identifier.of(HypnosiaClient.MOD_ID, "font/satyr_sp.ttf")
        }
        val loaded = runCatching {
            val resource = client.resourceManager.getResource(fontId)
                .orElseThrow { IllegalStateException("Missing font resource: $fontId") }
            resource.inputStream.use { input -> Font.createFont(Font.TRUETYPE_FONT, input) }
        }.getOrNull()
        baseFonts[font] = loaded
        return loaded
    }

    private fun drawGlyphSdf(atlasImage: BufferedImage, placedGlyph: PlacedGlyph) {
        val glyph = placedGlyph.glyph
        val glyphImage = BufferedImage(glyph.width, glyph.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = glyphImage.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        graphics.color = Color.WHITE
        graphics.drawGlyphVector(
            glyph.vector,
            (GLYPH_PADDING - glyph.boundsX).toFloat(),
            (GLYPH_PADDING - glyph.boundsY).toFloat(),
        )
        graphics.dispose()

        val width = glyph.width
        val height = glyph.height
        val inside = BooleanArray(width * height)
        for (yy in 0 until height) {
            for (xx in 0 until width) {
                inside[yy * width + xx] = ((glyphImage.getRGB(xx, yy) ushr 24) and 0xFF) >= 128
            }
        }

        val maxDistance = SDF_SPREAD.toFloat()
        val maxDistanceSq = SDF_SPREAD * SDF_SPREAD
        for (yy in 0 until height) {
            for (xx in 0 until width) {
                val currentInside = inside[yy * width + xx]
                var nearestSq = maxDistanceSq + 1
                val y0 = max(0, yy - SDF_SPREAD)
                val y1 = min(height - 1, yy + SDF_SPREAD)
                val x0 = max(0, xx - SDF_SPREAD)
                val x1 = min(width - 1, xx + SDF_SPREAD)

                scan@ for (sy in y0..y1) {
                    val dy = sy - yy
                    for (sx in x0..x1) {
                        if (inside[sy * width + sx] == currentInside) {
                            continue
                        }
                        val dx = sx - xx
                        val distanceSq = dx * dx + dy * dy
                        if (distanceSq < nearestSq) {
                            nearestSq = distanceSq
                            if (nearestSq == 0) {
                                break@scan
                            }
                        }
                    }
                }

                val distance = if (nearestSq <= maxDistanceSq) sqrt(nearestSq.toFloat()) else maxDistance
                val signedDistance = if (currentInside) distance else -distance
                val coverage = (0.5f + signedDistance / (maxDistance * 2.0f)).coerceIn(0.0f, 1.0f)
                val alpha = (coverage * 255.0f + 0.5f).toInt().coerceIn(0, 255)
                val argb = (alpha shl 24) or 0x00FFFFFF
                atlasImage.setRGB(placedGlyph.x + xx, placedGlyph.y + yy, argb)
            }
        }
    }

    private fun glyphChars(): List<Char> {
        val chars = mutableListOf<Char>()
        chars += (32..126).map(Int::toChar)
        chars += (0x00A0..0x00FF).map(Int::toChar)
        chars += (0x2000..0x206F).map(Int::toChar)
        chars += (0x20A0..0x20CF).map(Int::toChar)
        chars += (0x2190..0x21FF).map(Int::toChar)
        chars += (0x2200..0x22FF).map(Int::toChar)
        chars += (0x0400..0x04FF).map(Int::toChar)
        chars += listOf('•', '–', '—', '…', '№', '×', '✓')
        chars += listOf('•', '–', '—', '…', '№')
        return chars.distinct()
    }

    private fun normalizeSize(size: Float): Float =
        (round(size * 4.0f) / 4.0f).coerceAtLeast(1.0f)

    private fun putVertex(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        color: Int,
    ) {
        val vec = Vector4f(x, y, GUI_VERTEX_Z, 1.0f).mul(matrix)
        buffer.vertex(vec.x, vec.y, vec.z).texture(u, v).color(color)
    }

    private fun createOwnedVertexBuffer(label: String, builtBuffer: BuiltBuffer): GpuBuffer {
        return RenderSystem.getDevice().createBuffer(
            { label },
            GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
            builtBuffer.buffer,
        )
    }

    private fun createGuiMatrix(context: DrawContext): Matrix4f {
        val pose = Matrix3x2f(context.matrices)
        return Matrix4f()
            .m00(pose.m00())
            .m01(pose.m01())
            .m10(pose.m10())
            .m11(pose.m11())
            .m30(pose.m20())
            .m31(pose.m21())
    }

    private fun renderImmediate(
        context: DrawContext,
        vertexBuffer: GpuBuffer,
        texture: NativeImageBackedTexture,
        glyphCount: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val framebuffer = client.framebuffer
        val window = client.window
        val scaledWidth = window.framebufferWidth.toFloat() / window.scaleFactor.toFloat().coerceAtLeast(1.0f)
        val scaledHeight = window.framebufferHeight.toFloat() / window.scaleFactor.toFloat().coerceAtLeast(1.0f)
        val dynamicTransforms = RenderSystem.getDynamicUniforms().write(
            Matrix4f().setTranslation(0.0f, 0.0f, GUI_MODEL_VIEW_Z),
            Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
            Vector3f(0.0f, 0.0f, 0.0f),
            Matrix4f(),
        )
        val indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
        val gpuIndexBuffer = indexBuffer.getIndexBuffer(glyphCount * 6)
        val indexType = indexBuffer.indexType

        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(guiProjection.set(scaledWidth, scaledHeight), ProjectionType.ORTHOGRAPHIC)
        try {
            RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    { "Hypnosia HQ text" },
                    framebuffer.getColorAttachmentView(),
                    OptionalInt.empty(),
                    if (framebuffer.useDepthAttachment) framebuffer.getDepthAttachmentView() else null,
                    OptionalDouble.empty(),
                ).use { pass ->
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform("DynamicTransforms", dynamicTransforms)
                    HypnosiaScissor.current()?.let { scissor ->
                        pass.enableScissor(scissor.x, scissor.y, scissor.width, scissor.height)
                    }
                    pass.bindTexture("Sampler0", texture.glTextureView, textSampler())
                    pass.setPipeline(HypnosiaShaders.HQ_TEXT)
                    pass.setVertexBuffer(0, vertexBuffer)
                    pass.setIndexBuffer(gpuIndexBuffer, indexType)
                    pass.drawIndexed(0, 0, glyphCount * 6, 1)
                }
        } finally {
            RenderSystem.restoreProjectionMatrix()
        }
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) {
            result = result shl 1
        }
        return result
    }

    private fun textSampler(): GpuSampler {
        linearTextSampler?.let { return it }
        return RenderSystem.getDevice().createSampler(
            AddressMode.CLAMP_TO_EDGE,
            AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR,
            FilterMode.LINEAR,
            1,
            OptionalDouble.empty(),
        ).also { linearTextSampler = it }
    }

    private data class AtlasKey(
        val font: FigmaTextRenderer.Font,
        val size: Float,
    )

    private data class GlyphSpec(
        val char: Char,
        val vector: java.awt.font.GlyphVector,
        val boundsX: Int,
        val boundsY: Int,
        val width: Int,
        val height: Int,
        val advance: Float,
    )

    private data class PlacedGlyph(
        val glyph: GlyphSpec,
        val x: Int,
        val y: Int,
    )

    private data class Glyph(
        val advance: Float,
        val offsetX: Float,
        val offsetY: Float,
        val width: Float,
        val height: Float,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
    )

    private data class QueuedGlyph(
        val glyph: Glyph,
        val x: Float,
        val y: Float,
    )

    private data class FontAtlas(
        val texture: NativeImageBackedTexture,
        val glyphs: Map<Char, Glyph>,
        val ascent: Float,
        val fallbackGlyph: Char,
    )
}
