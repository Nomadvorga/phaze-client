package vorga.phazeclient.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Custom render layers used by Phaze Client.
 *
 * <h3>1.21.11 port</h3>
 *
 * {@code RenderPhase} and {@code RenderLayer.MultiPhaseParameters} were
 * removed outright. What used to be a pile of phase objects (program,
 * transparency, target, write mask, depth test, cull, line width) is now
 * split: the GPU state lives on a {@link RenderPipeline}, and the few
 * remaining render-graph concerns live on {@link RenderSetup}.
 *
 * <p>One behavioural note: the old layers set
 * {@code VIEW_OFFSET_Z_LAYERING} to nudge geometry forward slightly and
 * avoid z-fighting against world surfaces. That maps to
 * {@link LayeringTransform#VIEW_OFFSET_Z_LAYERING}, which is preserved below.
 *
 * <p>Each pipeline also has to declare the uniform blocks its shader
 * imports. {@code RenderLayer.draw} writes {@code DynamicTransforms} and
 * calls {@code RenderSystem.bindDefaultUniforms}, which supplies
 * {@code Projection}, {@code Fog}, {@code Globals} and {@code Lighting} -
 * but a block the pipeline never declared is not bound at all, so the
 * shader would read garbage transforms. The declarations below mirror
 * vanilla's own {@code POSITION_COLOR_SNIPPET} /
 * {@code RENDERTYPE_LINES_SNIPPET}.
 */
public final class PhazeRenderLayers {

    private static final RenderPipeline HITBOX_FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/hitbox_fill"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer HITBOX_FILL = RenderLayer.of(
            "phaze_hitbox_fill",
            RenderSetup.builder(HITBOX_FILL_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    /**
     * Memoized per line width, mirroring the vanilla pattern.
     *
     * <p>Line width used to be a {@code RenderPhase.LineWidth} on the
     * layer. There is no per-layer line width in the new model - it is a
     * vertex attribute, {@code LineWidth} in
     * {@link VertexFormats#POSITION_COLOR_NORMAL_LINE_WIDTH}, which
     * {@code core/rendertype_lines.vsh} reads per vertex. Every width
     * therefore resolves to the same pipeline and callers must emit
     * {@code .lineWidth(width)} on each vertex; the memoization is kept
     * so call sites and caching behaviour are unchanged.
     *
     * <p>{@code VertexFormats.LINES} itself is gone - the old
     * POSITION_COLOR_NORMAL format had no width channel because the
     * width lived in fixed-function {@code glLineWidth} state.
     */
    private static final RenderPipeline THICK_LINES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/thick_lines"))
            .withVertexShader(Identifier.of("minecraft", "core/rendertype_lines"))
            .withFragmentShader(Identifier.of("minecraft", "core/rendertype_lines"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build();

    private static final Function<Float, RenderLayer> THICK_LINES = Util.memoize(
            (Function<Float, RenderLayer>) (width -> RenderLayer.of(
                    "phaze_thick_lines_" + width,
                    RenderSetup.builder(THICK_LINES_PIPELINE)
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()))
    );

    private PhazeRenderLayers() {}

    public static RenderLayer getHitboxFill() {
        return HITBOX_FILL;
    }

    public static RenderLayer getThickLines(float width) {
        return THICK_LINES.apply(width);
    }
}
