package vorga.phazeclient.api.system.colorcorrection;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class WorldColorRenderHelper {
    private static final ThreadLocal<Deque<float[]>> ENTITY_SHADER_COLOR_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<float[]>> ENTITY_SHADER_COLOR_POOL =
            ThreadLocal.withInitial(ArrayDeque::new);

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

    public static boolean beginEntityShaderAlpha(WorldColorCorrectionController.Target target) {
        if (shouldWrapEntityProvider(target)
                || !WorldColorCorrectionController.needsAlphaTransform(target)
                || WorldColorCorrectionController.needsColorTransform(target)) {
            return false;
        }

        float[] current = RenderSystem.getShaderColor();
        Deque<float[]> pool = ENTITY_SHADER_COLOR_POOL.get();
        float[] state = pool.pollFirst();
        if (state == null) {
            state = new float[4];
        }
        state[0] = current[0];
        state[1] = current[1];
        state[2] = current[2];
        state[3] = current[3];
        ENTITY_SHADER_COLOR_STACK.get().push(state);
        return true;
    }

    public static void endEntityShaderAlpha() {
        Deque<float[]> stack = ENTITY_SHADER_COLOR_STACK.get();
        if (stack.isEmpty()) {
            return;
        }

        float[] previous = stack.pop();
        ENTITY_SHADER_COLOR_POOL.get().push(previous);
    }

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
