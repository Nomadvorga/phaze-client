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
import vorga.phazeclient.implement.features.modules.other.Zoom;

@Mixin(GameRenderer.class)
public abstract class GameRendererZoomMixin {

    @Unique
    private static double zoom$lastZoomDivisor = 1;
    @Unique
    private static boolean zoom$wasActive = false;
    @Unique
    private static boolean zoom$isZoomingIn = false;

    /** Animation start point captured the moment a transition flips
     *  direction. Used by the eased branch to lerp between this and
     *  the live target through the configured curve. The legacy
     *  Default branch ignores this field entirely - it falls back to
     *  the original exponential-approach formula that was in place
     *  before any easing options existed. */
    @Unique
    private static double zoom$animStart = 1.0;
    @Unique
    private static long zoom$animStartedAtMs = 0L;
    /** Last target the eased branch was animating toward. Tracked
     *  so we can detect mid-zoom target changes (the player is
     *  scrolling the wheel to dial zoom in/out further) and restart
     *  the easing curve from the current divisor toward the new
     *  target. Without this, scrolling during an active zoom would
     *  snap instantly because t was already at 1.0 from the
     *  previous animation completion. */
    @Unique
    private static double zoom$lastTarget = 1.0;

    @ModifyVariable(method = "getFov", at = @At(value = "RETURN", shift = At.Shift.BEFORE), ordinal = 1)
    private float injectZoom(float fov) {
        // No GUI early-return: when a screen opens we DO want the
        // unzoom animation to play out via the normal targetZoom=1
        // path. Releasing the bind on GUI open is handled in
        // {@link ScreenOpenMixin} which flips {@link Zoom#zoomActive}
        // off, so the rest of this method already drives the
        // zoom-out curve correctly.

        float targetZoom;

        if (Zoom.getInstance().isEnabled() && Zoom.isZoomActive()) {
            targetZoom = Zoom.getInstance().getCurrentZoomLevel();
        } else {
            targetZoom = 1;
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

        // Mid-zoom target change (scroll wheel adjustment): restart
        // the eased animation from the current visible position
        // toward the new target. Tolerance keeps frame-to-frame FP
        // jitter from triggering a needless restart.
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
            // Legacy "exponential approach" branch - matches the
            // pre-interpolations behaviour byte-for-byte. The user
            // explicitly asked for Default to be the original feel.
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
            // Time-based eased branch. Progress runs 0..1 through
            // the configured easing curve; the lerp is on the
            // divisor (not the FOV) so animations across different
            // zoom levels feel consistent.
            //
            // <p>Perceived-duration matching: the legacy Default
            // branch uses duration as an exponential-approach
            // coefficient that hits ~95% of the target in roughly
            // {@code duration * 0.35} seconds, not the full
            // duration itself. Without scaling, switching from
            // Default to any other curve would feel ~3x slower for
            // the same slider value, which the user reported as
            // "интерполяция увеличивает время зума". The 0.35
            // factor was eyeballed against the Default curve and
            // gives a near-identical feel at matching duration
            // values.
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

        zoom$wasActive = active;

        return (float) (fov / zoom$lastZoomDivisor);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        // Smooth transition handled in injectZoom
    }

    @Unique
    private static float zoom$frameStep(float animationSpeed) {
        return MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() * animationSpeed;
    }
}
