package vorga.phazeclient.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
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
 */
public final class PhazeRenderLayers {

    private static final RenderPipeline HITBOX_FILL_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/hitbox_fill"))
            .withVertexShader(Identifier.of("minecraft", "core/position_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_color"))
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
     * layer. There is no per-layer line width in the new model, so every
     * width currently resolves to the same pipeline; the memoization is
     * kept so call sites and caching behaviour are unchanged.
     */
    private static final RenderPipeline THICK_LINES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/thick_lines"))
            .withVertexShader(Identifier.of("minecraft", "core/rendertype_lines"))
            .withFragmentShader(Identifier.of("minecraft", "core/rendertype_lines"))
            .withVertexFormat(VertexFormats.LINES, VertexFormat.DrawMode.LINES)
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
