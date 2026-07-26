package vorga.phazeclient.api.system.shape.implement;

import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;

/**
 * Rounded rectangle.
 *
 * <h3>1.21.11: the batched path is the only path</h3>
 *
 * On 1.21.4 this class had two implementations. The eager one bound
 * {@code phaze:core/round} and pushed nine loose uniforms ({@code size},
 * {@code location}, {@code radius}, {@code softness}, {@code thickness},
 * {@code color1..4}, {@code outlineColor}) before a one-rect draw call;
 * the batched one deferred to {@link BatchedRectangle}, which carries the
 * same parameters as vertex attributes and draws many rects at once.
 *
 * <p>1.21.11 removed loose uniforms from the pipeline model entirely -
 * {@code UniformType} exposes only {@code UNIFORM_BUFFER} and
 * {@code TEXEL_BUFFER}, so the eager path would have to hand-pack a
 * std140 block per draw, with the alignment rules to match. The batched
 * shader needs no uniforms at all, so it maps onto the new model
 * directly and is now the sole implementation.
 *
 * <p>Output is unchanged: {@code round_batched.fsh} is a direct port of
 * {@code round.fsh} with identical SDF, outline and softness maths, and
 * the per-corner gradient the old shader rebuilt from {@code color1..4}
 * is reproduced by the rasterizer interpolating the four vertex colors.
 *
 * <p>Consequence to be aware of: there is no longer a fallback for the
 * case where {@code VertexFormatElement.register} cannot allocate GENERIC
 * slots. On the supported baseline - vanilla plus Fabric API, no other
 * mods - slots 7+ are free, so allocation succeeds. Mods that claim every
 * GENERIC slot would leave rectangles undrawn rather than falling back.
 */
public class Rectangle implements Shape, QuickImports {

    @Override
    public void render(ShapeProperties shape) {
        BatchedRectangle.submit(shape);
    }
}
