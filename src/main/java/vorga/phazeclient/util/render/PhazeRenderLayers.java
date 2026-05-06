package vorga.phazeclient.util.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Util;

import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Custom render layers used by Phaze Client.
 * Mirrors the pattern used by vanilla {@code RenderLayer.DEBUG_LINE_STRIP}: a memoized
 * factory keyed by line width so that each thickness gets its own cached MultiPhase.
 */
public final class PhazeRenderLayers {

    private static final Function<Float, RenderLayer> THICK_LINES = Util.memoize(
            (Function<Float, RenderLayer>) (width -> RenderLayer.of(
                    "phaze_thick_lines_" + width,
                    VertexFormats.LINES,
                    VertexFormat.DrawMode.LINES,
                    1536,
                    RenderLayer.MultiPhaseParameters.builder()
                            .program(RenderPhase.LINES_PROGRAM)
                            .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                            .target(RenderPhase.ITEM_ENTITY_TARGET)
                            .writeMaskState(RenderPhase.ALL_MASK)
                            .cull(RenderPhase.DISABLE_CULLING)
                            .build(false)
            ))
    );

    private PhazeRenderLayers() {}

    public static RenderLayer getThickLines(float width) {
        return THICK_LINES.apply(width);
    }
}
