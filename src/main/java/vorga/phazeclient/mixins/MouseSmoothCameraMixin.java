package vorga.phazeclient.mixins;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.SmoothCamera;

/**
 * Apply Smooth Camera's exponential low-pass filter to the
 * per-tick mouse delta before vanilla feeds it to
 * {@code ClientPlayerEntity.changeLookDirection}.
 *
 * <h3>One-pole IIR</h3>
 * Each frame we compute
 * <pre>{@code smoothed += (input - smoothed) * (1 - smoothness)}</pre>
 * which is the standard one-pole low-pass. {@code smoothness=0}
 * passes input through unchanged (factor 1.0); {@code smoothness=1}
 * never updates (factor 0.0). Higher smoothness values give a more
 * cinematic glide because each frame contributes less of the new
 * delta to the running smoothed value.
 *
 * <h3>Why ModifyArg with low priority</h3>
 * {@code MouseZoomMixin} also touches {@code changeLookDirection}'s
 * {@code dx}/{@code dy} arguments via {@code @ModifyArg}. We run at
 * the default priority but in source-order; mixin merges multiple
 * {@code @ModifyArg} chains, so our smoothing applies AFTER zoom's
 * sensitivity divisor (good - smoothing the already-scaled delta
 * gives consistent feel regardless of zoom level).
 *
 * <h3>State location</h3>
 * Static fields because {@code Mouse} is a singleton inside
 * {@code MinecraftClient}. The fields hold the most recent
 * smoothed value for each axis; reset to 0 whenever the module
 * disables to avoid carrying stale state across toggles.
 */
@Mixin(value = Mouse.class, priority = 1100)
public class MouseSmoothCameraMixin {

    @Unique
    private static double phaze$smoothCamX = 0.0;
    @Unique
    private static double phaze$smoothCamY = 0.0;

    @ModifyArg(method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),
            index = 0)
    private double phaze$smoothLookX(double x) {
        SmoothCamera module = SmoothCamera.getInstance();
        if (module == null || !module.isEnabled()) {
            phaze$smoothCamX = 0.0;
            return x;
        }
        // Effective per-frame "catch-up" factor: smoothness=0 -> 1.0
        // (instant), smoothness=1 -> 0.0 (frozen). Slider clamps to
        // [0.05, 0.95] so we always make some progress and never
        // freeze entirely.
        double factor = 1.0 - module.getSmoothness();
        phaze$smoothCamX += (x - phaze$smoothCamX) * factor;
        return phaze$smoothCamX;
    }

    @ModifyArg(method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),
            index = 1)
    private double phaze$smoothLookY(double y) {
        SmoothCamera module = SmoothCamera.getInstance();
        if (module == null || !module.isEnabled()) {
            phaze$smoothCamY = 0.0;
            return y;
        }
        double factor = 1.0 - module.getSmoothness();
        phaze$smoothCamY += (y - phaze$smoothCamY) * factor;
        return phaze$smoothCamY;
    }
}
