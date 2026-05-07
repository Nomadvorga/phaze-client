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
 * Smooth-scroll for the in-game chat HUD - direct port of
 * <a href="https://github.com/AGudimenko/Minecraft-Smooth-Scrolling">smsk's
 * Smooth-Scrolling</a> {@code ChatHudMixin}, retargeted to MC 1.21.4
 * yarn mappings.
 *
 * <p>Algorithm (from the reference):
 * <ol>
 *   <li>{@code scroll(I)} / {@code resetScroll()} HEAD-TAIL accumulate
 *       the integer scroll delta into a float {@code scrollOffset}
 *       measured in pixels.</li>
 *   <li>{@code render} HEAD decays {@code scrollOffset} toward zero with
 *       a frame-rate-independent {@code Math.pow(smoothness, dt)} factor
 *       and back-steps {@code scrolledLines} by
 *       {@code round(scrollOffset)/lineHeight}.</li>
 *   <li>{@code @ModifyVariable(ordinal=18)} subtracts the sub-pixel
 *       remainder ({@code getChatDrawOffset}) from the per-line
 *       y-baseline. In MC 1.21.4 LVT, ordinal=18 is the 19th int local
 *       which is slot 32 ({@code x = m - r * lineHeight}); modifying
 *       it cascades into both the indicator/background {@code fill}
 *       calls and the downstream text-y ({@code y = x + p}).</li>
 *   <li>{@code @ModifyVariable(ordinal=12)} on the loop counter {@code r}
 *       (slot 23) extends the loop one row past the visible window so
 *       the line sliding in from below has somewhere to come from.</li>
 *   <li>{@code render} TAIL restores {@code scrolledLines} so the rest
 *       of the codebase observes vanilla's true value.</li>
 *   <li>{@code addVisibleMessage} {@code @ModifyVariable} on the
 *       broken-line list compensates {@code scrollOffset} when vanilla
 *       calls {@code scroll(1)} per new line while chat is focused +
 *       scrolled up; without it the smooth scroll would lurch on every
 *       incoming message.</li>
 *   <li>{@code refresh} HEAD/TAIL flag suppresses that compensation
 *       while vanilla rebuilds the visible cache.</li>
 * </ol>
 *
 * <p>Mixin's {@code @ModifyVariable(ordinal=N)} counts entries of the
 * matching type in the target method's LVT, not raw {@code istore}
 * instructions. The reference's ordinals 7/12/14/18 happen to map to
 * the same logical locals in 1.21.4 (m/r/t/x respectively), so we keep
 * them verbatim.
 *
 * <p>Cooperates with {@link ChatHudMessageSlideMixin}: while we have
 * {@code scrolledLines} back-stepped we set
 * {@link ChatScrollState#suppressSlide} so that mixin's
 * {@code scrolledLines == 0} guard isn't fooled into stacking a second
 * translate on top of ours.
 */
@Mixin(value = ChatHud.class, priority = 1500)
public abstract class ChatHudSmoothScrollMixin {

    @Shadow private int scrolledLines;

    @Shadow protected abstract int getLineHeight();

    @Unique private float phaze$scrollOffset;
    @Unique private boolean phaze$refreshing = false;
    @Unique private int phaze$scrollValBefore;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$rolledBack = false;

    @Unique
    private boolean phaze$enabled() {
        Animations m = Animations.getInstance();
        return m != null && m.isChatSmoothScrollEnabled();
    }

    @Unique
    private float phaze$smoothness() {
        Animations m = Animations.getInstance();
        return m.smoothnessForSpeed(m.chatSmoothSpeed.getValue());
    }

    @Unique
    private float phaze$dtSeconds() {
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

    @Unique
    private int phaze$getChatScrollOffset() {
        return Math.round(phaze$scrollOffset);
    }

    @Unique
    private int phaze$getChatDrawOffset() {
        int total = phaze$getChatScrollOffset();
        int lh = getLineHeight();
        if (lh <= 0) return 0;
        return total - (total / lh) * lh;
    }

    // ---- scroll / resetScroll: accumulate deltas as pixels --------------
    // (always-on, mirroring the reference; the visual no-op happens when
    // phaze$enabled() returns false from the render-time hooks.)

    @Inject(method = "scroll(I)V", at = @At("HEAD"))
    private void phaze$scrollH(int scroll, CallbackInfo ci) {
        phaze$scrollValBefore = scrolledLines;
    }

    @Inject(method = "scroll(I)V", at = @At("TAIL"))
    private void phaze$scrollT(int scroll, CallbackInfo ci) {
        phaze$scrollOffset += (scrolledLines - phaze$scrollValBefore) * (float) getLineHeight();
    }

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void phaze$resetScrollH(CallbackInfo ci) {
        phaze$scrollValBefore = scrolledLines;
    }

    @Inject(method = "resetScroll", at = @At("TAIL"))
    private void phaze$resetScrollT(CallbackInfo ci) {
        phaze$scrollOffset += (scrolledLines - phaze$scrollValBefore) * (float) getLineHeight();
    }

    // ---- addVisibleMessage / refresh: compensate auto-scroll bumps ------

    @ModifyVariable(method = "addVisibleMessage", at = @At("STORE"), ordinal = 0)
    private List<OrderedText> phaze$onNewMessage(List<OrderedText> ot) {
        if (phaze$refreshing || ot == null) return ot;
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

    // ---- render: decay, back-step, sub-pixel y, extra row -------------

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$renderH(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        phaze$rolledBack = false;
        ChatScrollState.suppressSlide = false;

        if (!phaze$enabled()) {
            phaze$scrollOffset = 0F;
            return;
        }

        float dt = phaze$dtSeconds();
        phaze$scrollOffset = (float) (phaze$scrollOffset * Math.pow(phaze$smoothness(), dt));

        phaze$scrollValBefore = scrolledLines;
        scrolledLines -= phaze$getChatScrollOffset() / getLineHeight();
        if (scrolledLines < 0) scrolledLines = 0;
        phaze$rolledBack = true;
        ChatScrollState.suppressSlide = true;
    }

    /**
     * Pull each line's y-baseline up by the sub-pixel remainder. Targets
     * LVT ordinal=18 = slot 32 (x = m - r*lineHeight). The downstream
     * text-y (slot 33 = x + p) inherits the shift, and the indicator /
     * background {@code fill} calls iload slot 32 directly so they
     * track the slide in lock-step.
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 18)
    private int phaze$changePosY(int y) {
        if (!phaze$enabled()) return y;
        return y - phaze$getChatDrawOffset();
    }

    /**
     * Extend the visible-line loop one row past its natural bottom so
     * the line sliding in from below the chat anchor has a frame to
     * render at. Ordinal=12 = slot 23 (the {@code r} loop counter).
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 12)
    private int phaze$addLinesUnder(int r) {
        if (scrolledLines == 0 || !phaze$enabled() || phaze$getChatScrollOffset() <= 0) return r;
        return r - 1;
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
