package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

public final class MsdfRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger("PhazeMsdfText");
    private static Throwable lastFailure;
    private static BufferAllocator glyphAllocator;

    public static final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("phaze", "core/msdf_font"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );

    private MsdfRenderer() {
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z) {
        renderText(font, text, size, color, matrix, x, y, z, MsdfTextMetrics.DEFAULT_THICKNESS, 0.5F);
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z, float thickness, float smoothness) {
        render(font, text, size, color, matrix, x, y, z, thickness, smoothness, 0, 0, 0, 0, 0);
    }

    /** One glyph submission with a continuous per-fragment fade, not repeated scissor bands. */
    public static void renderTextFaded(MsdfFont font, String text, float size, int color, Matrix4f matrix,
                                       float x, float y, float z, float left, float right,
                                       float fadeWidth, float leftStrength, float rightStrength) {
        render(font, text, size, color, matrix, x, y, z, MsdfTextMetrics.DEFAULT_THICKNESS, 0.5F,
                left, right, fadeWidth, leftStrength, rightStrength);
    }

    public static boolean isUsingFallback() {
        return lastFailure != null;
    }

    /** Last diagnostic, retained until resource reload so failures are not silently permanent. */
    public static Throwable getLastFailure() {
        return lastFailure;
    }

    public static void onResourcesReloaded() {
        RenderSystem.assertOnRenderThread();
        if (lastFailure != null) LOGGER.info("Resources reloaded; retrying MSDF text rendering");
        lastFailure = null;
        close();
    }

    /** Releases only this renderer's owned native buffer; atlas textures belong to Minecraft. */
    public static void close() {
        RenderSystem.assertOnRenderThread();
        if (glyphAllocator != null) {
            glyphAllocator.close();
            glyphAllocator = null;
        }
    }

    static void reportFailure(Exception failure) {
        if (lastFailure == null) {
            lastFailure = failure;
            LOGGER.error("MSDF text rendering failed; using sized vanilla text until resources reload (F3+T)", failure);
        }
    }

    private static void render(MsdfFont font, String text, float size, int color, Matrix4f matrix,
                               float x, float y, float z, float thickness, float smoothness,
                               float left, float right, float fadeWidth, float leftStrength, float rightStrength) {
        if (text == null || text.isEmpty() || (color >>> 24) == 0 || !Float.isFinite(size) || size <= 0) return;
        RenderSystem.assertOnRenderThread();
        if (isUsingFallback() || font == null) {
            // Fallback uses vanilla TextRenderer which immediately
            // submits its own draw call against the same render thread
            // - the batched rect queue must be drained first or those
            // pending rects would render over (i.e. after) the
            // fallback text. Same reason as the non-fallback path
            // below: any draw that emits its own primitives into the
            // current GL pipeline has to come AFTER our cached batch.
            BatchedRectangle.flushIfBatching();
            fallback(font, text, size, thickness, color, matrix, x, y, z);
            return;
        }

        // Text must paint over all previously submitted rectangles.
        BatchedRectangle.flushIfBatching();

        try (TextRenderState state = new TextRenderState()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.setShaderTexture(0, font.getTextureId());

            ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
            if (shader == null) throw new IllegalStateException("Missing MSDF shader: " + MSDF_FONT_SHADER_KEY);
            uniform(shader, "Range").set(font.getAtlas().range());
            uniform(shader, "Thickness").set(thickness);
            uniform(shader, "Smoothness").set(smoothness);
            uniform(shader, "Outline").set(0);
            uniform(shader, "OutlineThickness").set(0.0F);
            uniform(shader, "OutlineColor").set(0.0F, 0.0F, 0.0F, 0.0F);
            uniform(shader, "EnableFadeout").set(0);
            uniform(shader, "FadeoutStart").set(0.0F);
            uniform(shader, "FadeoutEnd").set(1.0F);
            uniform(shader, "MaxWidth").set(0.0F);
            uniform(shader, "TextPosX").set(x);
            uniform(shader, "LabelFadeEnabled").set(fadeWidth > 0 ? 1 : 0);
            uniform(shader, "LabelFadeBounds").set(left, right, fadeWidth, 0.0F);
            uniform(shader, "LabelFadeStrength").set(leftStrength, rightStrength);
            uniform(shader, "ColorModulator").set(1.0F, 1.0F, 1.0F, 1.0F);

            drawGlyphs(font, text, size, thickness, color, matrix, x, y, z);
        } catch (Exception failure) {
            // State and buffer cleanup have already run before fallback starts.
            reportFailure(failure);
            fallback(font, text, size, thickness, color, matrix, x, y, z);
        }
    }

    private static GlUniform uniform(ShaderProgram shader, String name) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("MSDF shader is missing uniform: " + name);
        return uniform;
    }

    private static void drawGlyphs(MsdfFont font, String text, float size, float thickness, int color,
                                   Matrix4f matrix, float x, float y, float z) {
        // Owned allocator: a partial/failed glyph never poisons Minecraft's shared tessellator.
        if (glyphAllocator == null) glyphAllocator = new BufferAllocator(4096);
        try {
            BufferBuilder builder = new BufferBuilder(glyphAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            font.applyGlyphs(matrix, builder, text, size, thickness * 0.5F * size, 0.0F, x - 0.75F, y + (size * 0.7F), z, color);
            try (BuiltBuffer builtBuffer = builder.endNullable()) {
                if (builtBuffer != null) BufferRenderer.drawWithGlobalProgram(builtBuffer);
            }
        } finally {
            // endNullable may itself throw on an incomplete vertex. Drain the raw allocation too.
            try (var unused = glyphAllocator.getAllocated()) {
                // Closing returns even unfinished vertex bytes to the allocator.
            }
            glyphAllocator.clear();
        }
    }

    private static void fallback(MsdfFont font, String text, float size, float thickness, int color,
                                 Matrix4f matrix, float x, float y, float z) {
        TextRenderer textRenderer = MC.textRenderer;
        if (textRenderer == null) return;
        float scaleY = MsdfTextMetrics.fallbackScaleY(size, textRenderer.fontHeight);
        float targetWidth = font == null ? 0 : font.getRenderedWidth(text, size, thickness);
        float scaleX = MsdfTextMetrics.fallbackScaleX(targetWidth, textRenderer.getWidth(text), scaleY);
        Matrix4f scaled = new Matrix4f(matrix).translate(x, y, z).scale(scaleX, scaleY, 1);
        try (TextRenderState state = new TextRenderState(true); BufferAllocator allocator = new BufferAllocator(4096)) {
            // Vanilla upgrades vertex alpha 1..3 to opaque. Use the shader multiplier instead.
            RenderSystem.setShaderColor(1, 1, 1, (color >>> 24) / 255.0F);
            // Private provider preserves draw order without flushing unrelated vanilla/HUD consumers.
            VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
            textRenderer.draw(text, 0, 0, color | 0xFF000000, false, scaled, consumers, TextRenderer.TextLayerType.NORMAL, 0, 15728880);
            consumers.draw();
        }
    }
}
