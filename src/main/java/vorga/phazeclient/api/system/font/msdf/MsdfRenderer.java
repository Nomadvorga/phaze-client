package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class MsdfRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static boolean msdfFailed = false;

    public static final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("phaze", "core/msdf_font"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );

    private MsdfRenderer() {
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z) {
        renderText(font, text, size, color, matrix, x, y, z, 0.05F, 0.5F);
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z, float thickness, float smoothness) {
        if (msdfFailed || font == null) {
            fallback(text, color, matrix, x, y);
            return;
        }

        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, font.getTextureId());

            ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
            if (shader != null) {
                shader.getUniform("Range").set(font.getAtlas().range());
                shader.getUniform("Thickness").set(thickness);
                shader.getUniform("Smoothness").set(smoothness);
                shader.getUniform("Outline").set(0);
                shader.getUniform("OutlineThickness").set(0.0F);
                shader.getUniform("OutlineColor").set(0.0F, 0.0F, 0.0F, 0.0F);
                shader.getUniform("EnableFadeout").set(0);
                shader.getUniform("FadeoutStart").set(0.0F);
                shader.getUniform("FadeoutEnd").set(1.0F);
                shader.getUniform("MaxWidth").set(0.0F);
                shader.getUniform("TextPosX").set(x);
                shader.getUniform("ColorModulator").set(1.0F, 1.0F, 1.0F, 1.0F);
            }

            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            font.applyGlyphs(matrix, builder, text, size, thickness * 0.5F * size, 0.0F, x - 0.75F, y + (size * 0.7F), z, color);

            BuiltBuffer builtBuffer = builder.endNullable();
            if (builtBuffer != null) {
                BufferRenderer.drawWithGlobalProgram(builtBuffer);
            }

            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        } catch (Exception ignored) {
            msdfFailed = true;
            fallback(text, color, matrix, x, y);
        }
    }

    private static void fallback(String text, int color, Matrix4f matrix, float x, float y) {
        TextRenderer textRenderer = MC.textRenderer;
        if (textRenderer != null) {
            textRenderer.draw(text, x, y, color, false, matrix, MC.getBufferBuilders().getEntityVertexConsumers(), TextRenderer.TextLayerType.NORMAL, 0, 15728880);
        }
    }
}
