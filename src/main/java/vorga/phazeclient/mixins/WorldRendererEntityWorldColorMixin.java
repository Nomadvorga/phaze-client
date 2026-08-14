package vorga.phazeclient.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.api.system.colorcorrection.WorldColorRenderHelper;

/**
 * Classifies each deferred living-entity render as player or other entity.
 * 1.21.11 no longer exposes a per-entity VertexConsumerProvider, so the
 * classification is carried to the command queue while model commands are
 * submitted synchronously.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class WorldRendererEntityWorldColorMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void phaze$beginDeferredEntityColor(LivingEntityRenderState state,
                                                 MatrixStack matrices,
                                                 OrderedRenderCommandQueue queue,
                                                 CameraRenderState cameraState,
                                                 CallbackInfo ci) {
        WorldColorRenderHelper.pushDeferredEntityTarget(
                state instanceof PlayerEntityRenderState
                        ? WorldColorCorrectionController.Target.PLAYERS
                        : WorldColorCorrectionController.Target.ENTITIES
        );
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN")
    )
    private void phaze$endDeferredEntityColor(LivingEntityRenderState state,
                                               MatrixStack matrices,
                                               OrderedRenderCommandQueue queue,
                                               CameraRenderState cameraState,
                                               CallbackInfo ci) {
        WorldColorRenderHelper.popDeferredEntityTarget();
    }
}
