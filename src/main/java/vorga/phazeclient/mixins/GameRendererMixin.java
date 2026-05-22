package vorga.phazeclient.mixins;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
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

/**
 * Consolidated {@link GameRenderer} mixin: AspectRatio matrix
 * override, ColorCorrection post-pass shader, and MotionBlur
 * pre-hand pass. All three share {@code priority = 1100} so they
 * compose into a single file without changing ordering relative to
 * vanilla / other mods.
 *
 * <p>{@code GameRendererZoomMixin} stays separate because it uses
 * the default priority (1000), and {@code GameRendererAccessor} is
 * an interface, neither of which compose with this class-form
 * mixin.
 */
@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class GameRendererMixin {

    @Shadow private float zoom;
    @Shadow private float zoomX;
    @Shadow private float zoomY;
    @Shadow private float viewDistance;

    /** AspectRatio override - replaces vanilla's projection matrix. */
    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    private void phaze$overrideAspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio module = AspectRatio.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        MatrixStack stack = new MatrixStack();
        stack.peek().getPositionMatrix().identity();

        float ratio = module.getRatio();

        if (zoom != 1.0F) {
            stack.translate(zoomX, -zoomY, 0.0F);
            stack.scale(zoom, zoom, 1.0F);
        }

        stack.peek().getPositionMatrix().mul(
                new Matrix4f().setPerspective(
                        (float) (fovDegrees * (Math.PI / 180.0)),
                        ratio,
                        0.05F,
                        viewDistance * 4.0F
                )
        );

        cir.setReturnValue(stack.peek().getPositionMatrix());
    }

    /** MotionBlur pass - runs BEFORE renderHand so the blurred frame
     *  doesn't smear over the held item. */
    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void phaze$applyMotionBlur(RenderTickCounter tickCounter, CallbackInfo ci) {
        MotionBlur module = MotionBlur.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (module.shader == null) return;
        module.shader.applyMotionBlurBeforeHands();
    }

    /** ColorCorrection pass - runs AFTER renderHand so the colour
     *  filter applies to the full composited world+hand frame. */
    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V",
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
