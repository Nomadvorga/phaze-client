package vorga.phazeclient.mixins;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.helpers.ChatScrollState;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Smooth-scroll for the in-game chat HUD, ported from
 * <a href="https://github.com/AGudimenko/Minecraft-Smooth-Scrolling">smsk's
 * Smooth-Scrolling</a> ({@code ChatHudMixin}). Algorithm:
 *
 * <ol>
 *   <li>{@code scroll(I)} / {@code resetScroll()} HEAD-TAIL track the
 *       integer scroll delta and accumulate it into a float
 *       {@code scrollOffset} measured in pixels.</li>
 *   <li>{@code render} HEAD decays {@code scrollOffset} toward zero with
 *       a frame-rate-independent {@code Math.pow(smoothness, dt)}
 *       multiplier, then temporarily decrements vanilla's integer
 *       {@code scrolledLines} by {@code round(scrollOffset) / lineHeight}
 *       so the visible line range matches the user's pre-scroll content
 *       while the offset is large.</li>
 *   <li>{@code @ModifyVariable(name="y")} subtracts the sub-pixel
 *       remainder ({@code round(scrollOffset) - integerLines * lineHeight})
 *       from the per-line y-coordinate that vanilla computes inside the
 *       render-loop. Net: lines slide smoothly between row positions and
 *       the integer back-step is invisible at the swap point.</li>
 *   <li>{@code render} TAIL restores {@code scrolledLines} so the rest
 *       of the codebase observes vanilla's true value.</li>
 *   <li>{@code addVisibleMessage} {@code @ModifyVariable} on the
 *       broken-line list compensates {@code scrollOffset} when vanilla
 *       calls {@code scroll(1)} per new line while chat is focused +
 *       scrolled up; without it the smooth scroll would lurch on every
 *       incoming message.</li>
 *   <li>{@code refresh} HEAD/TAIL flag suppresses the addVisibleMessage
 *       compensation while vanilla rebuilds the visible cache.</li>
 * </ol>
 *
 * <p>{@link ChatHudMessageSlideMixin} also pushes a translate during
 * render. Higher mixin priority makes our HEAD run first; we set
 * {@link ChatScrollState#suppressSlide} for the duration of our
 * back-step so the slide mixin's {@code scrolledLines == 0} guard isn't
 * fooled into stacking translates while we're mid-animation.
 */
@Mixin(value = ChatHud.class, priority = 1500)
public abstract class ChatHudSmoothScrollMixin {

    @Shadow private int scrolledLines;

    @Shadow protected abstract int getLineHeight();

    /** Lagging scroll position in pixels. Decays toward zero. */
    @Unique private float phaze$scrollOffset = 0F;
    @Unique private int phaze$scrollValBefore = 0;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$refreshing = false;
    @Unique private boolean phaze$rolledBack = false;

    @Unique
    private boolean phaze$enabled() {
        Animations m = Animations.getInstance();
        return m != null && m.isChatSmoothScrollEnabled();
    }

    @Unique
    private float phaze$frameDtSeconds() {
        long now = System.nanoTime();
        float dt;
        if (phaze$lastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - phaze$lastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$lastFrameNanos = now;
        return dt;
    }

    /** Round to the nearest pixel - the integer part of the lag. */
    @Unique
    private int phaze$scrollOffsetPx() {
        return Math.round(phaze$scrollOffset);
    }

    /** Sub-pixel remainder: total offset minus the integer-line component. */
    @Unique
    private int phaze$drawOffsetPx() {
        int total = phaze$scrollOffsetPx();
        int lh = getLineHeight();
        if (lh <= 0) return 0;
        return total - (total / lh) * lh;
    }

    // ---- scroll / resetScroll: accumulate deltas as pixels --------------

    @Inject(method = "scroll(I)V", at = @At("HEAD"))
    private void phaze$scrollH(int amount, CallbackInfo ci) {
        phaze$scrollValBefore = scrolledLines;
    }

    @Inject(method = "scroll(I)V", at = @At("TAIL"))
    private void phaze$scrollT(int amount, CallbackInfo ci) {
        if (!phaze$enabled()) return;
        phaze$scrollOffset += (scrolledLines - phaze$scrollValBefore) * (float) getLineHeight();
    }

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void phaze$resetScrollH(CallbackInfo ci) {
        phaze$scrollValBefore = scrolledLines;
    }

    @Inject(method = "resetScroll", at = @At("TAIL"))
    private void phaze$resetScrollT(CallbackInfo ci) {
        if (!phaze$enabled()) return;
        phaze$scrollOffset += (scrolledLines - phaze$scrollValBefore) * (float) getLineHeight();
    }

    // ---- addVisibleMessage / refresh: compensate auto scroll bumps ------

    /**
     * When the user has chat focused and scrolled up, vanilla's
     * addVisibleMessage calls {@code scroll(1)} per broken line of the
     * incoming message; that bumps {@code scrollOffset} via our scroll
     * hook by {@code list.size() * lineHeight}, which would lurch the
     * smooth-scroll target off-screen. Counter-balance here so the user
     * stays on the same visible window.
     */
    @ModifyVariable(method = "addVisibleMessage", at = @At("STORE"), ordinal = 0)
    private List<OrderedText> phaze$onNewVisibleMessage(List<OrderedText> ot) {
        if (phaze$refreshing || !phaze$enabled() || ot == null) return ot;
        phaze$scrollOffset -= ot.size() * (float) getLineHeight();
        return ot;
    }

    @Inject(method = "refresh", at = @At("HEAD"))
    private void phaze$refreshH(CallbackInfo ci) {
        phaze$refreshing = true;
    }

    @Inject(method = "refresh", at = @At("TAIL"))
    private void phaze$refreshT(CallbackInfo ci) {
        phaze$refreshing = false;
    }

    // ---- render: decay, back-step scrolledLines, sub-pixel y modify -----

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$renderH(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        phaze$rolledBack = false;
        ChatScrollState.suppressSlide = false;

        if (!phaze$enabled()) {
            phaze$scrollOffset = 0F;
            return;
        }

        float dt = phaze$frameDtSeconds();
        Animations m = Animations.getInstance();
        float smoothness = m.smoothnessForSpeed(m.chatSmoothSpeed.getValue());
        phaze$scrollOffset = (float) (phaze$scrollOffset * Math.pow(smoothness, dt));

        int integerLines = phaze$scrollOffsetPx() / getLineHeight();
        if (integerLines == 0) {
            return;
        }

        phaze$scrollValBefore = scrolledLines;
        int adjusted = scrolledLines - integerLines;
        if (adjusted < 0) {
            // Underflow: clamp scrollOffset proportionally so the lag
            // can't fight the bottom of the visible buffer.
            adjusted = 0;
            phaze$scrollOffset = 0F;
        }
        scrolledLines = adjusted;
        phaze$rolledBack = true;
        ChatScrollState.suppressSlide = true;
    }

    /**
     * Pull each line's y-baseline up by the sub-pixel remainder so the
     * back-stepped content slides into the post-scroll positions
     * smoothly. Targets the {@code x} local in vanilla's render-loop
     * ({@code x = m - r * lineHeight}) by ordinal: it's the 16th
     * {@code istore} in {@code render}'s bytecode in MC 1.21.4, so
     * {@code ordinal=15} (0-indexed). The text {@code y} local is
     * computed downstream as {@code y = x + p}, so modifying {@code x}
     * cascades into both the indicator/background {@code fill} calls
     * (which read it directly) and the {@code drawTextWithShadow}
     * y-arg, keeping the per-line elements aligned through the slide.
     *
     * <p>We can't target by LVT {@code name="y"}: Minecraft's
     * production-remapped jar strips local-variable debug names so the
     * Mixin name-matcher would silently no-op. Ordinal positional
     * matching is what the reference smsk Smooth-Scrolling mod does too.
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 15)
    private int phaze$shiftLineY(int x) {
        if (!phaze$enabled()) return x;
        return x - phaze$drawOffsetPx();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$renderT(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        if (phaze$rolledBack) {
            scrolledLines = phaze$scrollValBefore;
            phaze$rolledBack = false;
        }
        ChatScrollState.suppressSlide = false;
    }
}
