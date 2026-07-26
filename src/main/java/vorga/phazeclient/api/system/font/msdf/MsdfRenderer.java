package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

/**
 * MSDF text renderer.
 *
 * <h3>1.21.11 port</h3>
 *
 * The 1.21.4 version bound {@code phaze:core/msdf_font} through a
 * {@code ShaderProgramKey} and pushed twelve uniforms. Only three of those
 * ever carried real values here ({@code Range}, {@code Thickness},
 * {@code Smoothness}); the other nine were nailed to constants at every
 * call site. Loose uniforms are gone from the 1.21.11 pipeline model, so
 * the three live values became a vertex attribute and the constant
 * branches were folded out of the shader.
 *
 * <p>The atlas is sampled from its {@code GpuTextureView} rather than an
 * Identifier, which a {@code RenderLayer} cannot express - hence the
 * explicit {@link GpuDraw} path.
 */
public final class MsdfRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static boolean msdfFailed = false;

    private static final VertexFormatElement MSDF_PARAMS;
    private static final VertexFormat FORMAT;
    private static final RenderPipeline PIPELINE;

    static {
        VertexFormatElement params = null;
        for (int id = 7; id < 32 && params == null; id++) {
            if (VertexFormatElement.get(id) == null) {
                params = VertexFormatElement.register(
                        id, 0, VertexFormatElement.ComponentType.FLOAT,
                        VertexFormatElement.Usage.GENERIC, 4);
            }
        }
        if (params == null) {
            throw new IllegalStateException("No free VertexFormatElement slot for MsdfRenderer");
        }
        MSDF_PARAMS = params;

        FORMAT = VertexFormat.builder()
                .add("Position", VertexFormatElement.POSITION)
                .add("UV0", VertexFormatElement.UV0)
                .add("Color", VertexFormatElement.COLOR)
                .add("MsdfParams", MSDF_PARAMS)
                .build();

        PIPELINE = RenderPipeline.builder()
                .withLocation(Identifier.of("phaze", "pipeline/msdf_font"))
                .withVertexShader(Identifier.of("phaze", "core/msdf_font"))
                .withFragmentShader(Identifier.of("phaze", "core/msdf_font"))
                .withSampler("Sampler0")
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();
    }

    private MsdfRenderer() {
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z) {
        renderText(font, text, size, color, matrix, x, y, z, 0.05F, 0.5F);
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z, float thickness, float smoothness) {
        if (msdfFailed || font == null) {
            // Fallback uses vanilla TextRenderer which submits its own draw,
            // so the batched rect queue must be drained first or those
            // pending rects would land over the fallback text.
            BatchedRectangle.flushIfBatching();
            fallback(text, color, matrix, x, y);
            return;
        }

        // Flush pending rects before MSDF text so rasterizer order matches
        // call order, and because the shared Tessellator only allows one
        // open buffer at a time.
        BatchedRectangle.flushIfBatching();

        try {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, FORMAT);
            font.applyGlyphs(
                    matrix, builder, text, size,
                    thickness * 0.5F * size, 0.0F,
                    x - 0.75F, y + (size * 0.7F), z, color,
                    MSDF_PARAMS, font.getAtlas().range(), smoothness);

            BuiltBuffer builtBuffer = builder.endNullable();
            if (builtBuffer != null) {
                try {
                    GpuDraw.draw(PIPELINE, builtBuffer, "Sampler0",
                            font.getTextureView(), FilterMode.LINEAR, matrix);
                } finally {
                    builtBuffer.close();
                }
            }
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
