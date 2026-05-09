package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
    /**
     * True while we're inside the vanilla {@code mouseDragged} body.
     * The flag exists purely so {@link #phaze$captureTargetAndRestore}
     * can tell scrollbar-thumb drags apart from every other scroll
     * source (wheel, programmatic, keyboard) and skip smoothing for
     * drags only - smoothing a drag would visibly desync the thumb
     * from the cursor, which is exactly the lag bug we're fixing.
     */
    @Unique private boolean phaze$insideMouseDragged = false;

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

        // Drag-from-scrollbar-thumb path: skip smoothing entirely so
        // the thumb tracks the cursor pixel-for-pixel. Without this,
        // every dragged frame would set targetScroll to the cursor
        // and then roll scrollY back to the in-flight displayScroll,
        // leaving the thumb visibly behind the cursor by however far
        // the lerp hadn't caught up yet.
        if (phaze$insideMouseDragged) {
            phaze$displayScroll = scrollY;
            phaze$targetScroll = scrollY;
            phaze$initialized = true;
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

    /*
     * Mark the surrounding mouseDragged call so any setScrollY invoked
     * from inside it can identify itself as a thumb-drag and bypass
     * the smoothing logic. HEAD/RETURN bracket guarantees the flag is
     * always cleared even if the body throws (RETURN fires on normal
     * exit; mixin {@code @Inject} doesn't run on exception, but in
     * practice mouseDragged in vanilla never throws and a stuck flag
     * would only "snap" a single subsequent setScrollY anyway, so the
     * blast radius of a missed clear is one frame at worst).
     */
    @Inject(method = "mouseDragged", at = @At("HEAD"), require = 0)
    private void phaze$dragHead(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY,
                                CallbackInfoReturnable<Boolean> cir) {
        phaze$insideMouseDragged = true;
    }

    @Inject(method = "mouseDragged", at = @At("RETURN"), require = 0)
    private void phaze$dragTail(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY,
                                CallbackInfoReturnable<Boolean> cir) {
        phaze$insideMouseDragged = false;
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

    /**
     * Multiply the wheel-tick scroll delta by the user-configured lines-
     * per-scroll value. Vanilla's {@code mouseScrolled} body passes
     * {@code scrollY - vertical * deltaPerScroll} to {@code setScrollY};
     * we rewrite that argument to extend the implied delta by the
     * multiplier. Result for {@code lines = N} is the wheel advancing
     * N entries instead of the vanilla 1.
     *
     * <p>Skipped for non-{@link EntryListWidget} widgets (e.g. text edits)
     * and for any module-disabled state - in both cases the original
     * argument is returned untouched so vanilla feel is preserved.
     */
    @ModifyArg(
            method = "mouseScrolled",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/ScrollableWidget;setScrollY(D)V"),
            require = 0
    )
    private double phaze$multiplyWheelDelta(double newScrollY) {
        if (!((Object) this instanceof EntryListWidget)) {
            return newScrollY;
        }
        Animations module = Animations.getInstance();
        if (module == null) {
            return newScrollY;
        }
        int lines = module.linesPerScroll();
        if (lines <= 1) {
            return newScrollY;
        }
        // newScrollY = current - vertical * deltaPerScroll
        // delta = current - newScrollY = vertical * deltaPerScroll
        // multipliedDelta = delta * lines
        // newY' = current - multipliedDelta = current + (newScrollY - current) * lines
        // Use phaze$targetScroll if initialized to chain rapid ticks
        // from the latest target rather than the in-flight smoothed
        // value (mirrors phaze$wheelChain's reasoning - phaze$shouldApply
        // there primed scrollY = targetScroll, but we may run before
        // phaze$shouldApply ever gated, so fall back to the field).
        double anchor = phaze$initialized ? phaze$targetScroll : scrollY;
        return anchor + (newScrollY - anchor) * lines;
    }
}
