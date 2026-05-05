package vorga.phazeclient.mixins;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;

@Mixin(value = GameRenderer.class, priority = 1100)
public class GameRendererMotionBlurMixin {

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void beforeRenderHand(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!MotionBlur.getInstance().isEnabled()) return;
        if (MotionBlur.getInstance().shader == null) return;
        MotionBlur.getInstance().shader.applyMotionBlurBeforeHands();
    }
}
