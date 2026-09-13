/**
 * Zoom functionality
 * Code from ok-boomer by glisco (MIT License)
 * Copyright (c) 2022 glisco
 * https://modrinth.com/mod/ok-boomer
 */

package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.base.util.animation.Interpolations;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.other.Zoom;

@Mixin(GameRenderer.class)
public abstract class GameRendererZoomMixin {

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void phaze$beginNametagBlurFrame(RenderTickCounter tickCounter, CallbackInfo ci) {
        NametagHud nametag = NametagHud.getInstance();
        boolean blurEnabled = nametag != null
                && nametag.isEnabled()
                && nametag.background.isValue()
                && nametag.backgroundBlurRadius.getValue() > 0.0F;
        Blur.INSTANCE.beginWorldSpaceFrame(blurEnabled);
    }

    @Unique
    private static double zoom$lastZoomDivisor = 1;
    @Unique
    private static boolean zoom$wasActive = false;
    @Unique
    private static boolean zoom$isZoomingIn = false;

    @Unique
    private static double zoom$animStart = 1.0;
    @Unique
    private static long zoom$animStartedAtMs = 0L;

    @Unique
    private static double zoom$lastTarget = 1.0;

    @ModifyVariable(method = "getFov", at = @At(value = "RETURN", shift = At.Shift.BEFORE), ordinal = 1)
    private float injectZoom(float fov) {

        float targetZoom;

        if (Zoom.getInstance().isEnabled() && Zoom.isZoomActive()) {
            targetZoom = Zoom.getInstance().getCurrentZoomLevel();
        } else {
            targetZoom = 1;
        }
        if (!Float.isFinite(targetZoom) || targetZoom < 1.0f) {
            targetZoom = 1.0f;
        }
        if (!Double.isFinite(zoom$lastZoomDivisor) || zoom$lastZoomDivisor < 1.0) {
            zoom$lastZoomDivisor = 1.0;
        }
        if (!Double.isFinite(zoom$animStart) || zoom$animStart < 1.0) {
            zoom$animStart = zoom$lastZoomDivisor;
        }
        if (!Double.isFinite(zoom$lastTarget) || zoom$lastTarget < 1.0) {
            zoom$lastTarget = 1.0;
        }

        boolean active = Zoom.isZoomActive();
        boolean directionFlipped = false;
        if (active && !zoom$wasActive) {
            zoom$isZoomingIn = true;
            directionFlipped = true;
        } else if (!active && zoom$wasActive) {
            zoom$isZoomingIn = false;
            directionFlipped = true;
        }

        boolean targetChanged = active
                && Math.abs(targetZoom - zoom$lastTarget) > 0.001;

        if (directionFlipped || targetChanged) {
            zoom$animStart = zoom$lastZoomDivisor;
            zoom$animStartedAtMs = System.currentTimeMillis();
        }
        zoom$lastTarget = targetZoom;

        String curve = zoom$isZoomingIn
                ? Zoom.getInstance().getZoomInInterpolation()
                : Zoom.getInstance().getZoomOutInterpolation();

        if (Interpolations.DEFAULT_NAME.equals(curve)) {

            float duration = zoom$isZoomingIn
                    ? Zoom.getInstance().getZoomInDuration()
                    : Zoom.getInstance().getZoomOutDuration();
            float animationSpeed;
            if (zoom$isZoomingIn) {
                animationSpeed = 1.0f / duration;
            } else {
                float currentZoom = (float) zoom$lastZoomDivisor;
                float baseSpeed = 1.0f / duration;
                if (currentZoom > 50.0f) {
                    float multiplier = Math.min(currentZoom / 50.0f, 5.0f);
                    animationSpeed = baseSpeed * multiplier;
                } else {
                    animationSpeed = baseSpeed;
                }
            }
            zoom$lastZoomDivisor += 0.45 * (targetZoom - zoom$lastZoomDivisor) * zoom$frameStep(animationSpeed);
        } else {

            float duration = zoom$isZoomingIn
                    ? Zoom.getInstance().getZoomInDuration()
                    : Zoom.getInstance().getZoomOutDuration();
            duration *= 0.35f;
            if (duration < 0.01f) duration = 0.01f;
            long elapsed = System.currentTimeMillis() - zoom$animStartedAtMs;
            float t = elapsed / (duration * 1000.0f);
            if (t < 0.0f) t = 0.0f;
            if (t > 1.0f) t = 1.0f;
            Interpolation interp = Interpolations.getByName(curve);
            float eased = (float) interp.interpolate(t);
            zoom$lastZoomDivisor = zoom$animStart + (targetZoom - zoom$animStart) * eased;
        }
        if (!Double.isFinite(zoom$lastZoomDivisor) || zoom$lastZoomDivisor < 1.0) {
            zoom$lastZoomDivisor = 1.0;
        }

        zoom$wasActive = active;

        return (float) (fov / zoom$lastZoomDivisor);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {

    }

    @Unique
    private static float zoom$frameStep(float animationSpeed) {

        return MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks() * animationSpeed;
    }
}
