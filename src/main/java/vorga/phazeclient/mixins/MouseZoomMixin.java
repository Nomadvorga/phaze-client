package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
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
    private void phaze$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (window == client.getWindow().getHandle()) {
            FreeLook freeLook = FreeLook.getInstance();
            if (freeLook != null && freeLook.isEnabled()) {
                freeLook.onBindStateChanged(input.button(), action);
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

        double currentZoom = Zoom.getInstance().getCurrentZoomLevel();
        double multiplier = Zoom.getInstance().getZoomScrollMultiplier();
        double sensitivity = Zoom.getInstance().getZoomScrollSensitivity();
        double newZoom;
        if (multiplier <= 1.0001) {

            newZoom = currentZoom + vertical * (currentZoom / 10) * sensitivity;
        } else {
            double exponent = vertical * sensitivity;
            newZoom = currentZoom * Math.pow(multiplier, exponent);
        }

        if (newZoom < 2.0f) {
            newZoom = 2.0f;
        }

        if (!Double.isFinite(newZoom)) {
            newZoom = Zoom.getInstance().getSafeMaxZoomLevel();
        }

        newZoom = Math.max(2.0f, Math.min(newZoom, Zoom.getInstance().getSafeMaxZoomLevel()));
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

            float dt = MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks() / 20.0F;
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

            float dt = MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks() / 20.0F;
            float alpha = 1.0F - (float) Math.pow(1.0F - 0.15F, 60.0F * dt);
            if (alpha < 0.0F) alpha = 0.0F;
            if (alpha > 1.0F) alpha = 1.0F;
            cinematic$smoothY = (float) (cinematic$smoothY + (y - cinematic$smoothY) * alpha);
            return (cinematic$smoothY / zoomLevel);
        }

        return y / zoomLevel;
    }
}
