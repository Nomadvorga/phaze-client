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

    /**
     * 1.21.11: {@code GameRenderer.getBasicProjectionMatrix} no longer
     * consults the far plane field directly - it calls the public
     * {@code getFarPlaneDistance()}, which is
     * {@code max(viewDistanceBlocks * 4, cloudRenderDistance * 16)}.
     * The field itself was also renamed {@code viewDistance ->
     * viewDistanceBlocks}, so shadowing the accessor is both correct
     * and rename-proof.
     */
    @Shadow public abstract float getFarPlaneDistance();

    /** AspectRatio override - replaces vanilla's projection matrix. */
    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    private void phaze$overrideAspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio module = AspectRatio.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        Matrix4f projection = new Matrix4f();

        float ratio = module.getRatio();

        // 1.21.11: the vanilla debug-camera zoom fields (zoom, zoomX,
        // zoomY) were removed from GameRenderer entirely, and
        // getBasicProjectionMatrix no longer applies any
        // translate/scale before the perspective call. The old
        // `if (zoom != 1.0F)` block that mirrored it is therefore gone
        // too - this override now matches vanilla exactly apart from
        // the substituted aspect ratio.
        projection.perspective(
                (float) (fovDegrees * (Math.PI / 180.0)),
                ratio,
                0.05F,
                getFarPlaneDistance()
        );

        cir.setReturnValue(projection);
    }

    /** MotionBlur pass - runs BEFORE renderHand so the blurred frame
     *  doesn't smear over the held item.
     *
     *  <p>1.21.11: {@code renderHand(Camera, float, Matrix4f)} became
     *  {@code renderHand(float tickProgress, boolean sleeping,
     *  Matrix4f)} - the Camera argument was dropped (it reads
     *  {@code this.camera} now) and a boolean was added. Descriptor
     *  updated to {@code (FZLorg/joml/Matrix4f;)V}; still exactly one
     *  call site inside {@code renderWorld}. */
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

    /** ColorCorrection pass - runs AFTER renderHand so the colour
     *  filter applies to the full composited world+hand frame. */
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
