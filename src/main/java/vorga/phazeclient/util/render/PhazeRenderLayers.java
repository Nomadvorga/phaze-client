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

    /**
     * Filled world box that sits flush against real block faces.
     *
     * <p>Identical to {@link #HITBOX_FILL_PIPELINE} apart from the depth bias.
     * Through 1.21.4 the block-overlay fill was pushed towards the camera with
     * {@code RenderSystem.polygonOffset(-1, -1)} + {@code enablePolygonOffset}
     * around the draw; without it a fill drawn exactly on a block face
     * z-fights with it and flickers.
     *
     * <p>1.21.11 removed both calls - depth bias is a pipeline property now.
     * That is strictly better here: the old imperative pair had to be undone
     * afterwards or the bias leaked into unrelated draws, whereas this travels
     * with the layer and cannot escape it.
     */
    private static final RenderPipeline BLOCK_FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/block_fill"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthBias(-1.0F, -1.0F)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer BLOCK_FILL = RenderLayer.of(
            "phaze_block_fill",
            RenderSetup.builder(BLOCK_FILL_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    /**
     * World-space triangles, additively blended - the glow disc.
     *
     * <p>{@code BlendFunction.LIGHTNING} is {@code (SRC_ALPHA, ONE)}, which is
     * exactly the {@code RenderSystem.blendFunc} the 1.21.4 glow paths set by
     * hand. Note it is NOT {@code BlendFunction.ADDITIVE} - that one is
     * {@code (ONE, ONE)} and would ignore the per-vertex alpha the fade relies
     * on.
     */
    private static final RenderPipeline TRIANGLES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/world_triangles"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer TRIANGLES = RenderLayer.of(
            "phaze_world_triangles",
            RenderSetup.builder(TRIANGLES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .expectedBufferSize(1536)
                    .build());

    /**
     * Textured world quads, for billboards.
     *
     * <p>A texture is part of the {@link RenderSetup} now rather than
     * imperative {@code RenderSystem.setShaderTexture} state, so a layer is
     * specific to the texture it samples - hence one memoized layer per
     * Identifier. The set of billboard textures Phaze uses is small and fixed,
     * so they live for the process.
     *
     * <p>Additive, like the 1.21.4 path: these are glow sprites whose art has
     * an opaque black surround, and only {@code (SRC_ALPHA, ONE)} makes that
     * surround contribute nothing. Ordinary translucency draws it as a black
     * square around the glow.
     */
    private static final RenderPipeline TEXTURED_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/world_textured"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderLayer> TEXTURED = Util.memoize(
            (Function<Identifier, RenderLayer>) (texture -> RenderLayer.of(
                    "phaze_world_textured_" + texture,
                    RenderSetup.builder(TEXTURED_PIPELINE)
                            .texture("Sampler0", texture)
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()))
    );

    private PhazeRenderLayers() {}

    public static RenderLayer getHitboxFill() {
        return HITBOX_FILL;
    }

    /** World-space triangles, POSITION_COLOR. */
    public static RenderLayer getTriangles() {
        return TRIANGLES;
    }

    /** World-space textured quads, POSITION_TEXTURE_COLOR. */
    public static RenderLayer getTextured(Identifier texture) {
        return TEXTURED.apply(texture);
    }

    /** Depth-biased filled box, for overlays drawn flush against block faces. */
    public static RenderLayer getBlockFill() {
        return BLOCK_FILL;
    }

    public static RenderLayer getThickLines(float width) {
        return THICK_LINES.apply(width);
    }
}
