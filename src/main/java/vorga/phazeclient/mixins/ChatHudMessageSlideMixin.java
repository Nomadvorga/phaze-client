package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.helpers.ChatScrollState;
import vorga.phazeclient.implement.features.modules.other.Animations;

import java.util.List;

/**
 * Slide-in animation for newly received chat messages, modelled on the
 * <a href="https://modrinth.com/mod/chatanimation">ChatAnimation</a> mod.
 *
 * <p>The animation is per-{@link ChatHudLine.Visible}: only the visible
 * rows whose {@code addedTime()} matches the most recent
 * {@code addMessage} arrival are translated. Older rows stay put. We
 * achieve that by wrapping every {@code DrawContext.drawTextWithShadow}
 * and {@code DrawContext.fill} call inside vanilla's render loop with
 * {@link WrapOperation} - MixinExtras hands us the loop's local
 * {@code ChatHudLine.Visible}, we compare its {@code addedTime} against
 * the timestamp captured in {@code addMessage}, and shift only those
 * draw arguments. Untouched rows render at their normal anchor, so
 * scrolling the chat or sending repeated messages no longer drags the
 * whole stack across the screen.
 *
 * <p>Two slide directions, controlled by
 * {@link Animations#isChatMessageSlideLeft()}:
 * <ul>
 *   <li>{@code Up} (default) - small ~2 px vertical push that fades
 *       to zero over {@link Animations#chatSlideFadeMs()}.</li>
 *   <li>{@code Left} - the new line enters from beyond the left edge
 *       of the chat box (full chat-width travel) over
 *       {@link Animations#chatLeftSlideFadeMs()} (4x the Up duration
 *       to compensate for the much longer travel distance and avoid
 *       reading as an instant pop at high speed-slider values).</li>
 * </ul>
 *
 * <p>The animation is suppressed while the user has the chat scrolled
 * away from the bottom ({@code scrolledLines != 0}) since the displaced
 * row would fight the user's scroll position.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMessageSlideMixin {

    @Shadow private int scrolledLines;

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow protected abstract int getLineHeight();

    @Shadow public abstract int getWidth();

    /**
     * Vertical-displacement scale, expressed as a fraction of
     * {@code lineHeight}. The ChatAnimation reference uses 0.8 (a full
     * line of slide), but at that scale the matrix translate exposes an
     * 8 px gap above the chat box (vanilla only renders {@code N} lines,
     * shifting them down leaves the topmost {@code displacement} pixels
     * empty) which shows world content behind the chat and visibly
     * flickers on long messages / fast slider settings. 0.2 keeps the
     * slide perceptible while shrinking the gap to ~2 px and the round
     * below pins it to integer pixels so font glyphs aren't sampled
     * across half-pixel boundaries.
     */
    @Unique private static final float UP_DISPLACEMENT_SCALE = 0.2F;

    /**
     * Horizontal-displacement scale for the "Left" slide style,
     * expressed as a fraction of the chat-box width returned by
     * {@link ChatHud#getWidth()}. 1.0 means the chat starts fully
     * off-screen to the left of its anchor (so the new message
     * literally enters from beyond the chat box) and slides in to
     * its rest position over {@code fadeMs}. Unlike the Up style
     * there's no half-pixel sampling concern because the chat box
     * background hides the gap during the slide, so we don't need
     * to attenuate the displacement.
     */
    @Unique private static final float LEFT_DISPLACEMENT_SCALE = 1.0F;

    @Unique private long phaze$lastMessageNanos = 0L;
    /**
     * Tick stamp of the visible-row(s) produced by the most recent
     * {@code addMessage}. Vanilla writes that tick into
     * {@link ChatHudLine.Visible#addedTime()} for every wrapped line
     * the message expands into, so an exact equality check is enough
     * to identify "this row belongs to the message we're animating"
     * - including all wrapped lines of a long message, and excluding
     * the previous message even if it arrived in the same tick (we
     * stamp on TAIL so we always pick up the freshest value).
     */
    @Unique private int phaze$latestAddedTick = Integer.MIN_VALUE;

    /** Cached per-frame translate, recomputed in {@link #phaze$prepareFrame}. */
    @Unique private float phaze$frameDx = 0.0F;
    @Unique private float phaze$frameDy = 0.0F;
    /** True when the current frame's slide should actually apply. */
    @Unique private boolean phaze$frameActive = false;

    @Inject(
            method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            at = @At("TAIL")
    )
    private void phaze$stampMessageArrival(ChatHudLine line, CallbackInfo ci) {
        phaze$lastMessageNanos = System.nanoTime();
        // Vanilla prepends the new visible-rows to index 0 of
        // visibleMessages, so [0] is the bottom-most row of the just-
        // arrived message. Reading its addedTime gives us the exact
        // tick value vanilla used for every wrapped line of this
        // message (they all share one tick), which is what we compare
        // against in the per-row WrapOperations below.
        if (!visibleMessages.isEmpty()) {
            phaze$latestAddedTick = visibleMessages.get(0).addedTime();
        }
    }

    /**
     * Compute and cache the slide offset for this frame. Doing the
     * (relatively cheap but non-trivial) math once per frame instead
     * of inside every {@code WrapOperation} call avoids burning time
     * on long chats where vanilla iterates dozens of rows.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$prepareFrame(DrawContext context, int currentTick, int mouseX, int mouseY,
                                    boolean focused, CallbackInfo ci) {
        phaze$frameActive = false;
        phaze$frameDx = 0.0F;
        phaze$frameDy = 0.0F;

        Animations module = Animations.getInstance();
        if (module == null || !module.isChatSmoothScrollEnabled()) {
            return;
        }
        // ChatHudSmoothScrollMixin back-steps scrolledLines mid-render
        // to fill the slide-in gap, so the field reads as non-zero even
        // on a freshly-bottomed chat. Defer to the suppress flag while
        // it's active so we don't double-translate.
        if (ChatScrollState.suppressSlide) {
            return;
        }
        // While the user is scrolled up, vanilla pins the visible
        // window and the slide would make existing messages jitter.
        if (scrolledLines != 0) {
            return;
        }
        if (phaze$lastMessageNanos == 0L) {
            return;
        }

        boolean left = module.isChatMessageSlideLeft();
        // Different curve per direction. Left has its own getter
        // because the travel distance is ~150x larger than Up - reusing
        // the same fadeMs would whip the message across the screen too
        // fast to follow at high speed-slider settings.
        float fadeMs = left ? module.chatLeftSlideFadeMs() : module.chatSlideFadeMs();
        if (fadeMs <= 0.0F) {
            return;
        }

        float lifetimeMs = (System.nanoTime() - phaze$lastMessageNanos) / 1_000_000.0F;
        if (lifetimeMs >= fadeMs) {
            return; // animation finished
        }

        float alpha = lifetimeMs / fadeMs;
        if (alpha < 0.0F) alpha = 0.0F;
        if (alpha > 1.0F) alpha = 1.0F;

        if (left) {
            float maxLeft = getWidth() * LEFT_DISPLACEMENT_SCALE;
            // Reshape the 0..1 linear-time alpha through the user's
            // chosen easing curve before mapping it to pixel travel.
            // {@code Linear} reproduces the original behaviour, other
            // curves give the slide overshoot / spring / bounce feel
            // without changing the duration knob.
            Interpolation interp = module.getChatLeftInterpolation();
            float shaped = (float) interp.interpolate(alpha);
            // Negative because the row is sliding *from* the left:
            // starts at -maxLeft and eases to 0 as shaped -> 1.
            phaze$frameDx = -maxLeft * (1.0F - shaped);
            phaze$frameDy = 0.0F;
            if (Math.abs(phaze$frameDx) < 1.0F) {
                return;
            }
        } else {
            float maxUp = getLineHeight() * UP_DISPLACEMENT_SCALE;
            phaze$frameDx = 0.0F;
            // Round to integer pixels: avoids fractional Y translates
            // that sample the font atlas across half-pixel boundaries.
            phaze$frameDy = Math.round(maxUp * (1.0F - alpha));
            if (phaze$frameDy < 1.0F) {
                return;
            }
        }

        phaze$frameActive = true;
    }

    @Unique
    private boolean phaze$shouldShift(ChatHudLine.Visible visible) {
        return phaze$frameActive
                && visible != null
                && visible.addedTime() == phaze$latestAddedTick;
    }

    /**
     * Shift the row-background fill only when this iteration's
     * {@code visible} is part of the freshly-arrived message.
     * MixinExtras' {@code @Local} captures the loop variable; if
     * vanilla ever calls {@code fill} outside the row loop (e.g. for
     * the scroll indicator), no {@code ChatHudLine.Visible} local is
     * in scope and the WrapOperation is silently skipped for that
     * call site, leaving the unrelated draw untouched.
     */
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
            require = 0
    )
    private void phaze$shiftFill(DrawContext ctx, int x1, int y1, int x2, int y2, int color,
                                 Operation<Void> op,
                                 @Local ChatHudLine.Visible visible) {
        if (phaze$shouldShift(visible)) {
            int dx = (int) phaze$frameDx;
            int dy = (int) phaze$frameDy;
            op.call(ctx, x1 + dx, y1 + dy, x2 + dx, y2 + dy, color);
        } else {
            op.call(ctx, x1, y1, x2, y2, color);
        }
    }

    /**
     * Same idea as {@link #phaze$shiftFill}, but for the per-row text
     * draw. Targeting the {@code OrderedText} overload because that's
     * what vanilla's chat loop calls on every visible row.
     */
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"),
            require = 0
    )
    private int phaze$shiftText(DrawContext ctx, TextRenderer renderer, OrderedText text,
                                int x, int y, int color,
                                Operation<Integer> op,
                                @Local ChatHudLine.Visible visible) {
        if (phaze$shouldShift(visible)) {
            return op.call(ctx, renderer, text,
                    x + (int) phaze$frameDx,
                    y + (int) phaze$frameDy,
                    color);
        }
        return op.call(ctx, renderer, text, x, y, color);
    }
}
