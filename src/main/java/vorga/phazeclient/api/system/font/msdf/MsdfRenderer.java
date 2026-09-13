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
import net.minecraft.client.gl.UniformType;

public final class MsdfRenderer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static boolean msdfFailed = false;

    private static final VertexFormatElement MSDF_PARAMS;
    private static final VertexFormat FORMAT;
    private static final RenderPipeline PIPELINE;

    static {
        VertexFormatElement params = null;
        for (int id = 7; id < 32 && params == null; id++) {
            if (VertexFormatElement.byId(id) == null) {
                params = VertexFormatElement.register(
                        id, 0, VertexFormatElement.Type.FLOAT,
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
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build();
    }

    private static final Matrix4f MSDF_GUI_POSE = new Matrix4f();

    private MsdfRenderer() {
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z) {
        renderText(font, text, size, color, matrix, x, y, z, 0.05F, 0.5F);
    }

    public static void renderText(MsdfFont font, String text, float size, int color, Matrix4f matrix, float x, float y, float z, float thickness, float smoothness) {
        if (msdfFailed || font == null) {

            BatchedRectangle.flushIfBatching();
            fallback(text, color, matrix, x, y);
            return;
        }

        BatchedRectangle.flushIfBatching();

        try {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, FORMAT);

            font.applyGlyphs(
                    matrix, builder, text, size,
                    thickness * 0.5F * size, 0.0F,
                    x - 0.75F, y + (size * 0.7F), z, color,
                    MSDF_PARAMS, font.getAtlas().range(), thickness, smoothness);

            BuiltBuffer builtBuffer = builder.endNullable();
            if (builtBuffer != null) {

                vorga.phazeclient.api.system.draw.GuiProjection.begin();
                try {

                    GpuDraw.draw(PIPELINE, builtBuffer, "Sampler0",
                            font.getTextureView(), FilterMode.LINEAR,
                            vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(MSDF_GUI_POSE));
                } finally {
                    vorga.phazeclient.api.system.draw.GuiProjection.end();
                    builtBuffer.close();
                }
            }
        } catch (Exception ignored) {
            msdfFailed = true;
            fallback(text, color, matrix, x, y);
        }
    }

    public static void drawAtlasQuad(com.mojang.blaze3d.textures.GpuTextureView textureView,
                                     Matrix4f matrix,
                                     float x1, float y1, float x2, float y2,
                                     float minU, float minV, float maxU, float maxV,
                                     int color, float range, float thickness, float smoothness,
                                     boolean flipV) {
        if (msdfFailed || textureView == null) {
            return;
        }
        BatchedRectangle.flushIfBatching();
        try {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, FORMAT);
            if (flipV) {
                msdfVertex(builder, matrix, x1, y2, minU, minV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y2, minU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y1, maxU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x1, y1, maxU, minV, color, range, thickness, smoothness);
            } else {
                msdfVertex(builder, matrix, x1, y1, minU, minV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x1, y2, minU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y2, maxU, maxV, color, range, thickness, smoothness);
                msdfVertex(builder, matrix, x2, y1, maxU, minV, color, range, thickness, smoothness);
            }

            BuiltBuffer builtBuffer = builder.endNullable();
            if (builtBuffer != null) {
                vorga.phazeclient.api.system.draw.GuiProjection.begin();
                try {

                    GpuDraw.draw(PIPELINE, builtBuffer, "Sampler0",
                            textureView, FilterMode.LINEAR,
                            vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(MSDF_GUI_POSE));
                } finally {
                    vorga.phazeclient.api.system.draw.GuiProjection.end();
                    builtBuffer.close();
                }
            }
        } catch (Exception ignored) {
            msdfFailed = true;
        }
    }

    private static void msdfVertex(BufferBuilder builder, Matrix4f matrix,
                                   float x, float y, float u, float v, int color,
                                   float range, float thickness, float smoothness) {
        builder.vertex(matrix, x, y, 0.0F).texture(u, v).color(color);
        long ptr = builder.beginElement(MSDF_PARAMS);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr, range);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 4L, thickness);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 8L, smoothness);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 12L, 0.0F);
    }

    private static void fallback(String text, int color, Matrix4f matrix, float x, float y) {
        TextRenderer textRenderer = MC.textRenderer;
        if (textRenderer != null) {
            textRenderer.draw(text, x, y, color, false, matrix, MC.getBufferBuilders().getEntityVertexConsumers(), TextRenderer.TextLayerType.NORMAL, 0, 15728880);
        }
    }
}
