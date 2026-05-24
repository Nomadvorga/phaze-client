package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.Zoom;

@Mixin(value = Mouse.class, priority = 500)
public class MouseZoomMixin {
    
    @Unique
    private static float cinematic$smoothX = 0;
    @Unique
    private static float cinematic$smoothY = 0;

    @Final
    @Shadow
    private MinecraftClient client;

    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void phaze$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (window == client.getWindow().getHandle()) {
            FreeLook freeLook = FreeLook.getInstance();
            if (freeLook != null && freeLook.isEnabled()) {
                freeLook.onBindStateChanged(button, action);
            }
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!Zoom.getInstance().isEnabled() || !Zoom.isZoomActive()) {
            return;
        }

        if (MinecraftClient.getInstance().currentScreen != null) {
            return;
        }

        // Geometric scroll: each scroll notch multiplies / divides
        // the current zoom by {@code multiplier}. With multiplier=2
        // and defaultZoom=4 the progression is 4 -> 8 -> 16 -> 32.
        // With multiplier=3 and defaultZoom=3 it's 3 -> 9 -> 27.
        // {@code sensitivity} acts as the exponent scale - 1.0 is a
        // full step per notch, 0.5 a half step (smoother), 2.0 a
        // double step (faster). The legacy linear (currentZoom/10)
        // ramp produced an additive feel that broke as soon as the
        // user wanted predictable doubling.
        double currentZoom = Zoom.getInstance().getCurrentZoomLevel();
        double multiplier = Zoom.getInstance().getZoomScrollMultiplier();
        double sensitivity = Zoom.getInstance().getZoomScrollSensitivity();
        double newZoom;
        if (multiplier <= 1.0001) {
            // Multiplier == 1 disables geometric scaling. Fall back
            // to the additive ramp so the slider remains useful at
            // the lower bound instead of "scroll does nothing".
            newZoom = currentZoom + vertical * (currentZoom / 10) * sensitivity;
        } else {
            double exponent = vertical * sensitivity;
            newZoom = currentZoom * Math.pow(multiplier, exponent);
        }

        // Prevent zooming out below minimum (only allow zooming in)
        if (newZoom < 2.0f) {
            newZoom = 2.0f;
        }

        if (Zoom.getInstance().isEnableLimits()) {
            newZoom = Math.max(2.0f, Math.min(newZoom, Zoom.getInstance().getMaxZoom()));
        }

        Zoom.getInstance().setCurrentZoomLevel((float) newZoom);
        ci.cancel();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void phaze$redirectChangeLookDirection(net.minecraft.client.network.ClientPlayerEntity player,
                                                   double cursorDeltaX, double cursorDeltaY) {
        // {@code cursorDeltaX/Y} here are the FINAL per-frame
        // yaw/pitch step in degrees: vanilla's {@code updateMouse}
        // has already run the sensitivity ramp, the optional
        // Smooth Camera smoother, and our {@link #zoomSensitivityX}/
        // {@code Y} {@code @ModifyArg} hooks (Zoom + Cinematic
        // Camera) on the locals before calling this method. So
        // when FreeLook is active we feed those processed deltas
        // into its accumulator and skip the player rotation - the
        // freelook camera now inherits Smooth Camera + Cinematic
        // Zoom for free instead of getting raw cursor delta as
        // before.
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook != null && freeLook.isEnabled() && freeLook.isActive()) {
            freeLook.onMouseLook(cursorDeltaX, cursorDeltaY);
            return;
        }
        player.changeLookDirection(cursorDeltaX, cursorDeltaY);
    }

    @ModifyArg(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"), index = 0)
    private double zoomSensitivityX(double x) {
        if (!Zoom.getInstance().isEnabled() || !Zoom.isZoomActive()) {
            cinematic$smoothX = 0;
            return x;
        }

        if (MinecraftClient.getInstance().currentScreen != null) {
            cinematic$smoothX = 0;
            return x;
        }

        float zoomLevel = Zoom.getInstance().getCurrentZoomLevel();
        
        if (Zoom.getInstance().isCinematicCamera()) {
            // FPS-independent smoothing: at the canonical 60 fps the
            // smoothing factor is 0.15 per frame (the original
            // hand-tuned value). For any other refresh rate we need
            // to convert the discrete-step decay constant into a
            // continuous one so the perceived smoothness stays the
            // same regardless of frame rate. Standard transform:
            //   alpha_t = 1 - (1 - 0.15)^(60 * dt)
            // Where dt is the frame time in seconds. At dt = 1/60
            // this collapses back to 0.15 exactly; at higher fps the
            // exponent shrinks so the per-frame nudge is smaller,
            // and at lower fps it grows so the camera still catches
            // up.
            float dt = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() / 20.0F;
            float alpha = 1.0F - (float) Math.pow(1.0F - 0.15F, 60.0F * dt);
            if (alpha < 0.0F) alpha = 0.0F;
            if (alpha > 1.0F) alpha = 1.0F;
            cinematic$smoothX = (float) (cinematic$smoothX + (x - cinematic$smoothX) * alpha);
            return (cinematic$smoothX / zoomLevel);
        }
        
        return x / zoomLevel;
    }

    @ModifyArg(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"), index = 1)
    private double zoomSensitivityY(double y) {
        if (!Zoom.getInstance().isEnabled() || !Zoom.isZoomActive()) {
            cinematic$smoothY = 0;
            return y;
        }

        if (MinecraftClient.getInstance().currentScreen != null) {
            cinematic$smoothY = 0;
            return y;
        }

        float zoomLevel = Zoom.getInstance().getCurrentZoomLevel();
        
        if (Zoom.getInstance().isCinematicCamera()) {
            // Same FPS-independent smoothing as zoomSensitivityX -
            // see that method for the alpha conversion rationale.
            float dt = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() / 20.0F;
            float alpha = 1.0F - (float) Math.pow(1.0F - 0.15F, 60.0F * dt);
            if (alpha < 0.0F) alpha = 0.0F;
            if (alpha > 1.0F) alpha = 1.0F;
            cinematic$smoothY = (float) (cinematic$smoothY + (y - cinematic$smoothY) * alpha);
            return (cinematic$smoothY / zoomLevel);
        }
        
        return y / zoomLevel;
    }
}
