package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Smooth-scroll for the in-game chat HUD - the scroll-wheel pass that
 * runs whether the chat is closed (HUD path) or open (ChatScreen path,
 * which still calls {@code chatHud.render(ctx, ...)} from its own
 * {@code render}). Modeled on the well-known PingIsFun smooth-scroll
 * trick: keep an extra {@code scrollOffset} in pixels that decays toward
 * zero with frame-rate-independent exponential smoothing, and during
 * render re-route vanilla's integer {@code scrolledLines} through
 * (target - integerLines) plus a sub-pixel matrix translate equal to the
 * remainder. The integer-line transition is invisible because the
 * sub-pixel offset compensates for it - net visual effect is a
 * continuous slide identical to {@code ScrollableWidgetSmoothScrollMixin}.
 *
 * <p>The matrix-translate path replaces the prior @ModifyVariable hack
 * that bound to the LVT ordinal of the y-position local in vanilla's
 * for-loop; that's fragile across MC patch versions and would silently
 * break on any vanilla refactor of the render method.
 *
 * <p>{@link ChatHudMessageSlideMixin} also pushes a translate during
 * render. Because mixin priorities run higher-first, our HEAD/RETURN
 * book-end the slide mixin's. The slide mixin's own
 * {@code scrolledLines != 0} guard would mis-fire on our temporarily-
 * decremented value, so we set {@link #phaze$suppressSlide} for the
 * duration of our back-step and {@link ChatHudMessageSlideMixin} will
 * eventually consult it once we wire it through.
 */
@Mixin(value = ChatHud.class, priority = 1500)
public abstract class ChatHudSmoothScrollMixin {

    @Shadow private int scrolledLines;

    @Shadow protected abstract int getLineHeight();

    /** Lagging scroll position in pixels. Decays toward zero. */
    @Unique private float phaze$scrollOffset = 0F;
    /** Captures {@code scrolledLines} before each scroll/resetScroll/addMessage call. */
    @Unique private int phaze$preCallScrolledLines = 0;
    /** Real value of {@code scrolledLines} we save/restore around render's back-step. */
    @Unique private int phaze$savedScrolledLines = 0;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$rolledBack = false;
    @Unique private boolean phaze$pushedMatrix = false;
    /** True while we're inside addMessage so scroll(I) hooks ignore the auto-bump. */
    @Unique private boolean phaze$inAddMessage = false;
    /** Exposed so ChatHudMessageSlideMixin can suppress its slide during our back-step. */
    @Unique public static boolean phaze$suppressSlide = false;

    /** Snap-to-zero threshold (pixels). */
    @Unique private static final float SETTLE_EPSILON = 0.5F;

    @Unique
    private boolean phaze$enabled() {
        Animations module = Animations.getInstance();
        return module != null && module.isChatSmoothScrollEnabled();
    }

    // ---- scroll ------------------------------------------------------

    @Inject(method = "scroll(I)V", at = @At("HEAD"))
    private void phaze$scrollH(int amount, CallbackInfo ci) {
        phaze$preCallScrolledLines = scrolledLines;
    }

    @Inject(method = "scroll(I)V", at = @At("TAIL"))
    private void phaze$scrollT(int amount, CallbackInfo ci) {
        if (!phaze$enabled() || phaze$inAddMessage) return;
        int delta = scrolledLines - phaze$preCallScrolledLines;
        if (delta == 0) return;
        // Positive delta = user scrolled UP into older history. We want
        // the OLD content to keep showing while we slide into the new
        // window, so push a positive offset; render rolls back
        // scrolledLines by delta and translates by remaining sub-pixel.
        phaze$scrollOffset += delta * getLineHeight();
    }

    // ---- resetScroll -------------------------------------------------

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void phaze$resetScrollH(CallbackInfo ci) {
        phaze$preCallScrolledLines = scrolledLines;
    }

    @Inject(method = "resetScroll", at = @At("TAIL"))
    private void phaze$resetScrollT(CallbackInfo ci) {
        if (!phaze$enabled() || phaze$inAddMessage) return;
        int delta = scrolledLines - phaze$preCallScrolledLines;
        if (delta == 0) return;
        phaze$scrollOffset += delta * getLineHeight();
    }

    // ---- addMessage --------------------------------------------------
    // Vanilla's addMessage may auto-bump scrolledLines while user is
    // scrolled up to keep the visible window stable. We suppress our
    // scroll-tracking during that, so reading older history while new
    // messages arrive doesn't kick off a spurious smooth-scroll.

    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("HEAD"))
    private void phaze$addMessageH(ChatHudLine line, CallbackInfo ci) {
        phaze$inAddMessage = true;
    }

    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("TAIL"))
    private void phaze$addMessageT(ChatHudLine line, CallbackInfo ci) {
        phaze$inAddMessage = false;
    }

    // ---- render ------------------------------------------------------

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$renderH(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        phaze$rolledBack = false;
        phaze$pushedMatrix = false;
        phaze$suppressSlide = false;

        if (!phaze$enabled()) {
            phaze$scrollOffset = 0F;
            return;
        }

        long now = System.nanoTime();
        float dt;
        if (phaze$lastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - phaze$lastFrameNanos) / 1_000_000_000.0F;
            // Cap so an alt-tab pause doesn't snap to target.
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$lastFrameNanos = now;

        if (Math.abs(phaze$scrollOffset) < SETTLE_EPSILON) {
            phaze$scrollOffset = 0F;
            return;
        }

        Animations module = Animations.getInstance();
        float smoothness = module.smoothnessForSpeed(module.chatSmoothSpeed.getValue());
        double decay = Math.pow(smoothness, dt);
        phaze$scrollOffset = (float) (phaze$scrollOffset * decay);

        int lineHeight = getLineHeight();
        int roundedPx = Math.round(phaze$scrollOffset);
        // Java integer division truncates toward zero; for negative offsets
        // (user scrolled DOWN / closed chat) that already gives the right
        // sign for both integer-step and remainder so we don't need to
        // floorDiv.
        int integerLines = roundedPx / lineHeight;
        int subPixelPx = roundedPx - integerLines * lineHeight;

        if (integerLines == 0 && subPixelPx == 0) {
            return;
        }

        // Roll scrolledLines back by integerLines so vanilla draws the
        // line range that's visually closer to where the user came from.
        phaze$savedScrolledLines = scrolledLines;
        int adjusted = scrolledLines - integerLines;
        if (adjusted < 0) {
            // Underflow: clamp and zero the offset so we don't fight the
            // bottom of the visible buffer.
            adjusted = 0;
            phaze$scrollOffset = 0F;
            subPixelPx = 0;
        }
        scrolledLines = adjusted;
        phaze$rolledBack = true;
        phaze$suppressSlide = true;

        if (subPixelPx != 0) {
            // Negate: the back-step puts content one row visually
            // higher than the post-scroll target, then sub-pixel slides
            // it down (or up for negative offset) into rest position.
            float translateY = -(float) subPixelPx;
            ctx.draw();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0.0F, translateY, 0.0F);
            phaze$pushedMatrix = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$renderT(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        if (phaze$pushedMatrix) {
            // Flush translated chat batch with matrix still active.
            ctx.draw();
            ctx.getMatrices().pop();
            phaze$pushedMatrix = false;
        }
        if (phaze$rolledBack) {
            scrolledLines = phaze$savedScrolledLines;
            phaze$rolledBack = false;
        }
        phaze$suppressSlide = false;
    }
}
