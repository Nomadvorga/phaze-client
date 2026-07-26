package vorga.phazeclient.api.system.colorcorrection;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;

import java.util.Locale;

public final class WorldColorRenderHelper {

    private WorldColorRenderHelper() {
    }

    public static VertexConsumerProvider tintProvider(VertexConsumerProvider original, WorldColorCorrectionController.Target target) {
        if (original == null
                || !shouldWrapEntityProvider(target)
                || original instanceof TintingVertexConsumerProvider) {
            return original;
        }
        return new TintingVertexConsumerProvider(original, target);
    }

    public static boolean shouldWrapEntityProvider(WorldColorCorrectionController.Target target) {
        return WorldColorCorrectionController.needsColorTransform(target)
                || WorldColorCorrectionController.needsAlphaTransform(target);
    }

    /**
     * Entity alpha used to ride on the global shader colour.
     *
     * <p>1.21.4 read {@code RenderSystem.getShaderColor()}, multiplied
     * the alpha channel by the target's alpha and pushed the previous
     * value so {@link #endEntityShaderAlpha()} could restore it. Both
     * {@code getShaderColor} and {@code setShaderColor} are gone in
     * 1.21.11 - there is no global colour left to read or write, because
     * colour travels per-draw now - and there is no replacement that can
     * reach inside vanilla's entity draws from the outside.
     *
     * <p>Nothing is lost. The guard was already unsatisfiable:
     * {@link #shouldWrapEntityProvider} is
     * {@code needsColorTransform || needsAlphaTransform}, so
     * {@code !shouldWrapEntityProvider(target)} implies
     * {@code !needsAlphaTransform(target)} and the very next clause
     * rejected it. This returned {@code false} for every possible target
     * on 1.21.4 too. The path that actually applies the alpha is
     * {@link #tintProvider}, which wraps the provider in
     * {@link TintingVertexConsumerProvider} and scales the vertex colours
     * directly - that is the supported way to do this now, and it is what
     * {@code WorldRendererEntityWorldColorMixin} uses.
     *
     * <p>Kept as a no-op rather than deleted so the entry points stay
     * available if the dispatcher-level hook is ever reinstated.
     */
    public static boolean beginEntityShaderAlpha(WorldColorCorrectionController.Target target) {
        return false;
    }

    public static void endEntityShaderAlpha() {
        // See beginEntityShaderAlpha: nothing is ever pushed.
    }

    /**
     * Name-matching predicate for "this layer must blend even though
     * vanilla draws it opaque".
     *
     * <p>Currently unwired: it fed {@code RenderLayerBlendWorldColorMixin},
     * which injected into {@code RenderPhase.Transparency}'s start/end
     * tasks. Those tasks no longer exist - blending is a
     * {@code BlendFunction} baked into the immutable {@code RenderPipeline}
     * a layer was built with, and it cannot be flipped from outside the
     * draw. The tinting {@code VertexConsumerProvider} handles the alpha
     * cases this used to backstop.
     *
     * <p>TODO(1.21.11): if forced blending is ever needed again it has to
     * be done by substituting a whole pipeline, not by toggling state.
     * Kept because the classification logic is the non-obvious part.
     */
    public static boolean shouldForceBlendForLayer(RenderLayer layer) {
        if (layer == null) {
            return false;
        }

        String name = layer.toString().toLowerCase(Locale.ROOT);
        WorldColorCorrectionController.Target target = WorldColorCorrectionController.current();

        if (target == WorldColorCorrectionController.Target.CLOUDS && name.contains("cloud")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.CLOUDS);
        }

        if (target == WorldColorCorrectionController.Target.PLAYERS && name.contains("entity")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.PLAYERS);
        }

        if (target == WorldColorCorrectionController.Target.ENTITIES && name.contains("entity")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.ENTITIES);
        }

        if (target == WorldColorCorrectionController.Target.BLOCKS && (name.contains("block_entity") || name.contains("blockentity"))) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.BLOCKS);
        }

        if (target == WorldColorCorrectionController.Target.FLUIDS && name.contains("water")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.FLUIDS);
        }

        if (target == WorldColorCorrectionController.Target.FLUIDS && name.contains("lava")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.FLUIDS);
        }

        if (target == WorldColorCorrectionController.Target.FLUIDS && name.contains("fluid")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.FLUIDS);
        }

        if (target == WorldColorCorrectionController.Target.FLUIDS && name.contains("translucent")) {
            return WorldColorCorrectionController.needsBlend(WorldColorCorrectionController.Target.FLUIDS);
        }

        if (target == WorldColorCorrectionController.Target.CLOUDS && name.contains("translucent")) {
            return true;
        }

        return false;
    }
}
