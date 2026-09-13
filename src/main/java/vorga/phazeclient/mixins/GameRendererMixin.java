package vorga.phazeclient.mixins;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.AspectRatio;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;

@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class GameRendererMixin {

    @Shadow public abstract float getFarPlaneDistance();

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    private void phaze$overrideAspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio module = AspectRatio.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        Matrix4f projection = new Matrix4f();

        float ratio = module.getRatio();

        projection.perspective(
                (float) (fovDegrees * (Math.PI / 180.0)),
                ratio,
                0.05F,
                getFarPlaneDistance()
        );

        cir.setReturnValue(projection);
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void phaze$applyMotionBlur(RenderTickCounter tickCounter, CallbackInfo ci) {
        MotionBlur module = MotionBlur.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (module.shader == null) return;
        module.shader.applyMotionBlurBeforeHands();
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void phaze$applyColorCorrection(RenderTickCounter tickCounter, CallbackInfo ci) {
        ColorCorrection module = ColorCorrection.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (module.shader == null) return;
        module.shader.apply();
    }
}
