package vorga.phazeclient.mixins;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.NoFluid;
import vorga.phazeclient.implement.features.modules.other.SmoothCamera;

/**
 * Consolidated {@link Camera} mixin merging FreeLook rotation
 * override, Smooth-F5 distance interpolation, and the NoFluid
 * submersion-type rewrite. All three target {@code Camera} so a
 * single file is the natural fit; each feature lives in its own
 * injector with no shared state.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    // SmoothCamera state — VISUAL-ONLY low-pass on Camera.setRotation.
    // Server-bound rotation packets carry the player's real yaw/pitch,
    // so anti-cheat sees identical input to a player WITHOUT smoothing.
    // Bans for "Aim assist" / "Aim-K" are therefore impossible on this
    // path. Trade-off: the rendered camera lags very slightly behind
    // the actual hit-detection direction, which the half-life cap (see
    // SmoothCamera) keeps imperceptible at default settings.
    @org.spongepowered.asm.mixin.Unique private static float phaze$smoothCamYaw = Float.NaN;
    @org.spongepowered.asm.mixin.Unique private static float phaze$smoothCamPitch = Float.NaN;
    @org.spongepowered.asm.mixin.Unique private static long phaze$smoothCamLastNanos = 0L;
    @org.spongepowered.asm.mixin.Unique private static net.minecraft.client.option.Perspective phaze$smoothCamLastPerspective = null;

    /** SmoothCamera: low-pass filter the camera-side rotation only.
     *  Mouse input (changeLookDirection) is untouched, so the player's
     *  real yaw/pitch and the packets sent to the server stay raw. */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void phaze$smoothCameraRotation(Args args) {
        SmoothCamera module = SmoothCamera.getInstance();
        if (module == null || !module.isEnabled()) {
            phaze$smoothCamYaw = Float.NaN;
            phaze$smoothCamPitch = Float.NaN;
            phaze$smoothCamLastNanos = 0L;
            phaze$smoothCamLastPerspective = null;
            return;
        }
        // FreeLook overrides the same setRotation call below; if FreeLook
        // is active, let it produce the camera angles and skip our
        // smoothing.
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook != null && freeLook.isEnabled() && freeLook.isActive()) {
            return;
        }

        float targetYaw = args.<Float>get(0);
        float targetPitch = args.<Float>get(1);

        // FPS-independent decay: factor = 1 - 0.5^(dt/halfLife).
        // Slider 0..1 -> halfLife 0..0.15s.
        float smoothness = module.getSmoothness();
        if (smoothness < 0.0F) smoothness = 0.0F;
        if (smoothness > 0.99F) smoothness = 0.99F;
        float halfLife = smoothness * 0.15F;

        long now = System.nanoTime();
        float dtSeconds;
        if (phaze$smoothCamLastNanos == 0L) {
            dtSeconds = 1.0F / 60.0F;
        } else {
            dtSeconds = (now - phaze$smoothCamLastNanos) / 1_000_000_000.0F;
            if (dtSeconds > 0.25F) dtSeconds = 0.25F;
            if (dtSeconds < 0.0F) dtSeconds = 0.0F;
        }
        phaze$smoothCamLastNanos = now;

        float factor;
        if (halfLife <= 0.0001F) {
            factor = 1.0F;
        } else {
            factor = 1.0F - (float) Math.pow(0.5, dtSeconds / halfLife);
        }
        if (factor < 0.0F) factor = 0.0F;
        if (factor > 1.0F) factor = 1.0F;

        net.minecraft.client.option.Perspective currentPerspective = phaze$currentPerspective();

        // First frame: align without smoothing.
        if (Float.isNaN(phaze$smoothCamYaw) || Float.isNaN(phaze$smoothCamPitch)) {
            phaze$smoothCamYaw = targetYaw;
            phaze$smoothCamPitch = targetPitch;
            phaze$smoothCamLastPerspective = currentPerspective;
            return;
        }

        // Snap on perspective change (F1 / F3 / F5 / F5-front). Mouse
        // movement, however fast, is always smoothed; only an explicit
        // perspective swap bypasses the filter so the camera doesn't
        // chase a 180° flip across half a second of slide.
        //
        // FRONT (third-person inverted) view: vanilla pre-flips the
        // yaw arg to setRotation by 180°. After the snap on the
        // initial BACK→FRONT swap, our accumulator is at the new
        // (flipped) base, and subsequent mouse movement lands as a
        // small delta against THAT base, so the IIR keeps converging
        // normally - smoothing in FRONT works the same as in BACK.
        if (currentPerspective != phaze$smoothCamLastPerspective) {
            phaze$smoothCamYaw = targetYaw;
            phaze$smoothCamPitch = targetPitch;
            phaze$smoothCamLastPerspective = currentPerspective;
            return;
        }

        // wrapDegrees: shortest signed yaw delta so 359 -> 1 reads as
        // +2 instead of -358. Fast mouse flicks are smoothed normally
        // through this path.
        float deltaYaw = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - phaze$smoothCamYaw);
        float deltaPitch = targetPitch - phaze$smoothCamPitch;

        // Pass-through guard for FRONT (third-person inverted) view.
        // Vanilla's Camera.update calls setRotation TWICE in this mode
        // within the same frame: once with the player's actual yaw,
        // and again with yaw+180° / -pitch for the inverted-view
        // camera. Our IIR sees the second call as a giant 180° delta
        // and partially smooths it, which leaves the args at a near-
        // BACK angle - the visible bug "FRONT view doesn't work".
        // Threshold high enough (>170° / >80°) that ordinary fast
        // mouse flicks stay below it and continue to be smoothed.
        // For the inverse-pass call we leave args untouched and skip
        // the accumulator update so the next BACK-pass call still
        // sees a sensible reference yaw.
        if (Math.abs(deltaYaw) > 170.0F || Math.abs(deltaPitch) > 80.0F) {
            return;
        }

        phaze$smoothCamYaw += deltaYaw * factor;
        phaze$smoothCamPitch += deltaPitch * factor;

        if (phaze$smoothCamPitch > 90.0F) phaze$smoothCamPitch = 90.0F;
        if (phaze$smoothCamPitch < -90.0F) phaze$smoothCamPitch = -90.0F;

        args.set(0, phaze$smoothCamYaw);
        args.set(1, phaze$smoothCamPitch);
    }

    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.client.option.Perspective phaze$currentPerspective() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return null;
        return mc.options.getPerspective();
    }


    /** FreeLook: forces vanilla's {@code setRotation(yaw, pitch)} call
     *  inside {@code update} to use the FreeLook module's drift values
     *  instead of the player's actual look angles. */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void phaze$freeLookRotation(Args args) {
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook == null || !freeLook.isEnabled() || !freeLook.isActive()) {
            return;
        }
        args.set(0, freeLook.getCameraYaw(1.0f));
        args.set(1, freeLook.getCameraPitch(1.0f));
    }

    /** Smooth-F5 step 1: tick the interpolator at HEAD so subsequent
     *  hooks read current state. */
    @Inject(method = "update", at = @At("HEAD"))
    private void phaze$tickSmoothF5(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Animations animations = Animations.getInstance();
        if (mc == null || mc.options == null || animations == null) {
            return;
        }
        animations.tickSmoothF5(mc.options.getPerspective());
    }

    /** Smooth-F5 step 2: force {@code thirdPerson=true} while the
     *  interpolator is mid-slide so vanilla still goes through the
     *  third-person camera path even when the perspective field has
     *  flipped to {@code FIRST_PERSON}. */
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean phaze$forceThirdPersonDuringSlide(boolean thirdPerson) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isSmoothF5Enabled()) {
            return thirdPerson;
        }
        return thirdPerson || animations.isF5AnimationActive();
    }

    /** Smooth-F5 step 3: substitute the live interpolated distance
     *  for vanilla's fixed {@code BASE_CAMERA_DISTANCE} (4.0F) when
     *  it calls {@code clipToSpace}. */
    @ModifyArg(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float phaze$smoothCameraDistance(float distance) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isSmoothF5Enabled()) {
            return distance;
        }
        return animations.currentF5Distance();
    }

    /** NoFluid: rewrite the {@code getSubmersionType} return value
     *  to {@code NONE} when the camera is in water/lava and the
     *  module's per-fluid toggle is on. Powder-snow / bubble-column
     *  are intentionally untouched. */
    @Inject(method = "getSubmersionType", at = @At("RETURN"), cancellable = true, require = 0)
    private void phaze$rewriteSubmersionType(CallbackInfoReturnable<CameraSubmersionType> cir) {
        NoFluid mod = NoFluid.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        CameraSubmersionType type = cir.getReturnValue();
        if (type == CameraSubmersionType.WATER && mod.shouldHideWater()) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        } else if (type == CameraSubmersionType.LAVA && mod.shouldHideLava()) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        }
    }
}
