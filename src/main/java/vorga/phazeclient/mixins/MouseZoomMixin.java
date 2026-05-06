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

        // Adjust zoom level with scroll
        double currentZoom = Zoom.getInstance().getCurrentZoomLevel();
        double multiplier = Zoom.getInstance().getZoomScrollMultiplier();
        double newZoom = currentZoom + vertical * (currentZoom / 10) * Zoom.getInstance().getZoomScrollSensitivity() * multiplier;

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

    @Inject(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"), cancellable = true)
    private void phaze$onUpdateMouse(double timeDelta, CallbackInfo ci) {
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook != null && freeLook.isEnabled() && freeLook.onMouseLook(cursorDeltaX, cursorDeltaY)) {
            ci.cancel();
        }
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
            // Apply cinematic smoothing with stronger effect
            cinematic$smoothX = (float) (cinematic$smoothX + (x - cinematic$smoothX) * 0.15);
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
            // Apply cinematic smoothing with stronger effect
            cinematic$smoothY = (float) (cinematic$smoothY + (y - cinematic$smoothY) * 0.15);
            return (cinematic$smoothY / zoomLevel);
        }
        
        return y / zoomLevel;
    }
}
