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
import vorga.phazeclient.implement.features.modules.other.Zoom;

@Mixin(GameRenderer.class)
public abstract class GameRendererZoomMixin {

    @Unique
    private static double zoom$lastZoomDivisor = 1;
    @Unique
    private static boolean zoom$wasActive = false;
    @Unique
    private static boolean zoom$isZoomingIn = false;

    @ModifyVariable(method = "getFov", at = @At(value = "RETURN", shift = At.Shift.BEFORE), ordinal = 1)
    private float injectZoom(float fov) {
        // Disable zoom when GUI is open
        if (MinecraftClient.getInstance().currentScreen != null) {
            return fov;
        }
        
        float targetZoom;
        
        if (Zoom.getInstance().isEnabled() && Zoom.isZoomActive()) {
            targetZoom = Zoom.getInstance().getCurrentZoomLevel();
        } else {
            targetZoom = 1;
        }

        // Track zoom direction
        if (Zoom.isZoomActive() && !zoom$wasActive) {
            zoom$isZoomingIn = true;
        } else if (!Zoom.isZoomActive() && zoom$wasActive) {
            zoom$isZoomingIn = false;
        }

        // Determine animation speed based on zoom direction and current zoom level
        float animationSpeed;
        if (zoom$isZoomingIn) {
            // Zooming in - larger duration = slower animation
            animationSpeed = 1.0f / Zoom.getInstance().getZoomInDuration();
        } else {
            // Zooming out - larger duration = slower animation
            // Make unzoom faster on larger zoom levels (> 50)
            float currentZoom = (float) zoom$lastZoomDivisor;
            float baseSpeed = 1.0f / Zoom.getInstance().getZoomOutDuration();
            if (currentZoom > 50.0f) {
                // Scale speed based on how much larger than 50
                float multiplier = Math.min(currentZoom / 50.0f, 5.0f); // Cap at 5x speed
                animationSpeed = baseSpeed * multiplier;
            } else {
                animationSpeed = baseSpeed;
            }
        }

        zoom$lastZoomDivisor += 0.45 * (targetZoom - zoom$lastZoomDivisor) * zoom$interpolator(animationSpeed);

        zoom$wasActive = Zoom.isZoomActive();

        return (float) (fov / zoom$lastZoomDivisor);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        // Smooth transition handled in injectZoom
    }

    private static float zoom$interpolator(float animationSpeed) {
        return MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() * animationSpeed;
    }
}
