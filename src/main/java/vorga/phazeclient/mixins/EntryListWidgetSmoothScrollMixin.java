package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Smooth scrolling for any EntryListWidget (option lists, server lists,
 * resource pack lists, etc.). Implementation strategy:
 *  - Mirror vanilla's {@code scrollAmount} into our own {@code targetScroll}
 *    via {@code setScrollAmount} HEAD, ignoring re-entrant calls we make
 *    ourselves.
 *  - Each frame inside {@code renderWidget} HEAD, exponentially decay a
 *    {@code displayScroll} float toward {@code targetScroll} and write the
 *    rounded value back to the underlying field through vanilla's setter
 *    (so any clamping / max-scroll logic still runs).
 *  - Wheel events go through {@code mouseScrolled}: we suspend our smoothing
 *    capture briefly so vanilla's increment is treated as a NEW target, not
 *    an absolute jump.
 */
@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetSmoothScrollMixin {

    @Shadow private double scrollAmount;

    @Shadow public abstract void setScrollAmount(double sc);

    @Unique private double phaze$targetScroll;
    @Unique private double phaze$displayScroll;
    @Unique private boolean phaze$bypass;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$initialized = false;

    /**
     * Treat any external setScrollAmount as a new TARGET, not an immediate
     * jump - except when the call comes from inside our own render tick
     * (bypass flag) or from inside vanilla's mouseScrolled which we adapt
     * separately below.
     */
    @Inject(method = "setScrollAmount", at = @At("TAIL"))
    private void phaze$captureTarget(double sc, CallbackInfo ci) {
        if (phaze$bypass) {
            return;
        }
        phaze$targetScroll = scrollAmount;
        if (!phaze$initialized) {
            // First sync: avoid a phantom slide on the very first render
            // when the widget opens with an existing scroll position.
            phaze$displayScroll = scrollAmount;
            phaze$initialized = true;
        }
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), require = 0)
    private void phaze$tickAndApply(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isListSmoothScrollEnabled()) {
            return;
        }
        if (!phaze$initialized) {
            phaze$targetScroll = scrollAmount;
            phaze$displayScroll = scrollAmount;
            phaze$initialized = true;
            return;
        }

        long now = System.nanoTime();
        float dt;
        if (phaze$lastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - phaze$lastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$lastFrameNanos = now;

        float smoothness = module.smoothnessForSpeed(module.listSpeed.getValue());
        double decay = Math.pow(smoothness, dt);
        phaze$displayScroll = (phaze$displayScroll - phaze$targetScroll) * decay + phaze$targetScroll;
        if (Math.abs(phaze$displayScroll - phaze$targetScroll) < 0.5) {
            phaze$displayScroll = phaze$targetScroll;
        }

        double rounded = Math.round(phaze$displayScroll);
        if (rounded != scrollAmount) {
            phaze$bypass = true;
            try {
                setScrollAmount(rounded);
            } finally {
                phaze$bypass = false;
            }
        }
    }

    /**
     * On wheel scroll vanilla calls {@code setScrollAmount(scrollAmount +
     * delta)} which would otherwise overwrite our target. Capture before/
     * after so the user-driven delta becomes the new TARGET while the
     * displayScroll keeps animating from where it currently is.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), require = 0)
    private void phaze$wheelHead(double mouseX, double mouseY, double horizontal, double vertical,
                                 CallbackInfoReturnable<Boolean> cir) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isListSmoothScrollEnabled()) {
            return;
        }
        // Make vanilla's "scrollAmount + delta" math run from our LATEST
        // target (so successive wheel ticks chain) instead of our smoothed
        // displayScroll, by temporarily sliding scrollAmount to target.
        phaze$bypass = true;
        try {
            setScrollAmount(phaze$targetScroll);
        } finally {
            phaze$bypass = false;
        }
    }
}
