package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.api.system.colorcorrection.WorldColorRenderHelper;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererEntityWorldColorMixin {
    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private VertexConsumerProvider phaze$wrapEntityProvider(
            VertexConsumerProvider original,
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float tickDelta,
            MatrixStack matrices
    ) {
        if (entity == null) {
            return original;
        }

        WorldColorCorrectionController.Target target =
                entity instanceof PlayerEntity
                        ? WorldColorCorrectionController.Target.PLAYERS
                        : WorldColorCorrectionController.Target.ENTITIES;
        return WorldColorRenderHelper.shouldWrapEntityProvider(target)
                ? WorldColorRenderHelper.tintProvider(original, target)
                : original;
    }
}
