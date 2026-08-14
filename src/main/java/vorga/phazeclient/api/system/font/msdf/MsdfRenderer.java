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

    /** Scratch for {@link vorga.phazeclient.api.system.draw.GuiProjection#guiModelView}, render thread only. */
    private static final Matrix4f MSDF_GUI_POSE = new Matrix4f();

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
            // `thickness * 0.5F * size` is the GEOMETRIC advance; the raw
            // `thickness` is what the shader wants for its distance offset.
            // 1.21.4 passed the raw value through the loose `Thickness`
            // uniform, so keeping them separate here restores that split.
            font.applyGlyphs(
                    matrix, builder, text, size,
                    thickness * 0.5F * size, 0.0F,
                    x - 0.75F, y + (size * 0.7F), z, color,
                    MSDF_PARAMS, font.getAtlas().range(), thickness, smoothness);

            BuiltBuffer builtBuffer = builder.endNullable();
            if (builtBuffer != null) {
                // 1.21.11 defers DrawContext work into a GuiRenderState, so this
                // immediate draw runs outside the GUI pass and must install the
                // GUI ortho projection (and its z = -11000 model-view) itself.
                vorga.phazeclient.api.system.draw.GuiProjection.begin();
                try {
                    // GpuDraw writes DynamicTransforms from this argument rather
                    // than from the RenderSystem stack, so the GUI z offset has
                    // to be folded into the matrix itself.
                    //
                    // Only the z offset, NOT `matrix`. applyGlyphs emits every
                    // vertex through consumer.vertex(matrix, ...), which
                    // transforms the position on the CPU, so the pose is already
                    // baked into Position. msdf_font.vsh then computes
                    // ProjMat * ModelViewMat * Position, so putting `matrix` in
                    // the model-view too would apply it a second time: text
                    // would drift away from the BatchedRectangle boxes behind
                    // it (those get the pose exactly once) as soon as the pose
                    // is not identity - the menu open/close scale, a hovered
                    // ButtonComponent, a dragged ModuleComponent.
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

    /**
     * Draws one MSDF-decoded quad from an arbitrary MSDF atlas.
     *
     * <p>Exists for {@code UiMsdfIconAtlas}, whose icons are multi-channel
     * signed distance fields exactly like the glyphs here, just from a
     * different atlas. In 1.21.4 it reached the MSDF shader the same way text
     * did - {@code RenderSystem.setShader(MSDF_FONT_SHADER_KEY)} plus the
     * Range/Thickness/Smoothness uniforms. The port pointed it at
     * {@code PhazeDrawLayers.positionTexColor} instead, which is a plain
     * textured pipeline: it drew the raw distance-field texels, so every icon
     * came out as the red/green/blue MSDF encoding rather than a decoded
     * shape.
     *
     * <p>Routing it back through {@link #PIPELINE} restores the decode. The
     * three former uniforms travel as the {@code MsdfParams} vertex attribute,
     * identical on all four vertices, which is the same substitution the text
     * path uses.
     *
     * @param textureView the MSDF atlas to sample
     * @param matrix      GUI pose; applied on the CPU per vertex, so the draw
     *                    passes only the z offset as its model-view
     * @param range       the atlas' distance range (px), from its json
     * @param flipV       emit with the 1.21.4 "legacy image orientation"
     *                    winding, which mirrors the quad vertically
     */
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
                    // z offset only - `matrix` is already baked into every
                    // vertex by BufferBuilder.vertex(Matrix4f, ...), so passing
                    // it again would apply the pose twice.
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
