package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Smooth scrolling for any {@link ScrollableWidget} that's actually an
 * {@link EntryListWidget} (option lists, server lists, resource pack lists,
 * etc.). Text-edit widgets also extend ScrollableWidget but we deliberately
 * skip those via an instanceof check so typing/cursor behaviour is untouched.
 *
 * <p>In 1.21.4 the field {@code scrollY} and the method {@code setScrollY}
 * live on {@link ScrollableWidget}; in earlier versions they lived on
 * {@link EntryListWidget} as {@code scrollAmount} / {@code setScrollAmount}.
 * Targeting the parent class is forward-compatible with however the subclass
 * hierarchy evolves and lets us shadow the private field directly.
 *
 * <p>Strategy:
 *   - Track a per-widget {@code displayScroll} that's eased toward
 *     {@code targetScroll} using frame-rate independent exponential decay.
 *   - {@code setScrollY} TAIL: capture the just-clamped {@code scrollY} as
 *     the new target, then RESTORE the field to {@code displayScroll} so
 *     vanilla's renderList picks up the smooth value rather than the jump.
 *   - {@code drawScrollbar} HEAD: per-render-frame tick that advances
 *     displayScroll and writes it back into the field; vanilla draws the
 *     scrollbar thumb against this value moments later.
 *   - {@code mouseScrolled} HEAD: prime {@code scrollY = targetScroll} so
 *     vanilla's "scrollY -= delta * deltaPerScroll" math chains against the
 *     latest target instead of the in-flight smoothed value.
 */
@Mixin(ScrollableWidget.class)
public abstract class ScrollableWidgetSmoothScrollMixin {

    @Shadow private double scrollY;

    @Unique private double phaze$targetScroll;
    @Unique private double phaze$displayScroll;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$initialized = false;

    @Unique
    private boolean phaze$shouldApply() {
        if (!((Object) this instanceof EntryListWidget)) {
            return false;
        }
        Animations module = Animations.getInstance();
        return module != null && module.isListSmoothScrollEnabled();
    }

    @Inject(method = "setScrollY", at = @At("TAIL"))
    private void phaze$captureTargetAndRestore(double y, CallbackInfo ci) {
        if (!phaze$shouldApply()) {
            // Sync state so a later toggle-on doesn't see a stale display.
            phaze$displayScroll = scrollY;
            phaze$targetScroll = scrollY;
            return;
        }

        phaze$targetScroll = scrollY;

        if (!phaze$initialized) {
            phaze$displayScroll = scrollY;
            phaze$initialized = true;
            return;
        }

        // Roll vanilla's instant jump back: keep the field at the smooth
        // value so the upcoming renderList still draws from where we are,
        // not where we're going.
        scrollY = phaze$displayScroll;
    }

    @Inject(method = "drawScrollbar", at = @At("HEAD"))
    private void phaze$tickDecay(DrawContext context, CallbackInfo ci) {
        if (!phaze$shouldApply()) {
            return;
        }
        if (!phaze$initialized) {
            phaze$displayScroll = scrollY;
            phaze$targetScroll = scrollY;
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

        Animations module = Animations.getInstance();
        float smoothness = module.smoothnessForSpeed(module.listSpeed.getValue());
        double decay = Math.pow(smoothness, dt);
        phaze$displayScroll = (phaze$displayScroll - phaze$targetScroll) * decay + phaze$targetScroll;
        if (Math.abs(phaze$displayScroll - phaze$targetScroll) < 0.5) {
            phaze$displayScroll = phaze$targetScroll;
        }

        scrollY = phaze$displayScroll;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), require = 0)
    private void phaze$wheelChain(double mouseX, double mouseY, double horizontal, double vertical,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!phaze$shouldApply() || !phaze$initialized) {
            return;
        }
        // Vanilla's body does setScrollY(scrollY - vertical * deltaPerScroll).
        // Priming scrollY to targetScroll makes successive wheel ticks chain
        // from the latest target instead of the in-flight smoothed value -
        // otherwise rapid wheels would spread out as displayScroll caught up.
        scrollY = phaze$targetScroll;
    }
}
