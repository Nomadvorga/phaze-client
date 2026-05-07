package vorga.phazeclient.mixins;

import java.util.List;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.OrderedText;
import org.joml.Vector3f;
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

    @Shadow public abstract int getVisibleLineCount();

    @Shadow private boolean isChatHidden() { return false; }

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
     * Set up a tight scissor at the natural chat boundary AND push a
     * matrix translate by the sub-pixel remainder so that every row -
     * including the back-stepped row at the top, the addLinesUnder row
     * at the bottom, and the per-row backgrounds and indicators - moves
     * as one rigid block. The scissor clips whatever spills past the
     * natural top/bottom, so the visible chat rectangle stays static
     * while content slides inside it.
     *
     * <p>Targets LVT ordinal=7 = slot 12 ({@code m}, the chat baseline).
     * MC's {@link DrawContext#enableScissor} works in unscaled GUI
     * coords with no matrix-translate compensation, so we read the
     * current matrix translate ourselves and shift our chat-local rect
     * into screen space.
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 7)
    private int phaze$mask(int m, @Local(argsOnly = true) DrawContext ctx) {
        phaze$pushedMatrix = false;
        if (!phaze$enabled() || isChatHidden()) return m;

        // Natural chat extent: top is the topmost line's y-baseline (in
        // MC's drawText that's also the top of the glyph box); bottom is
        // the bottommost baseline plus a typical text descender of ~9px.
        int visible = getVisibleLineCount();
        int lh = getLineHeight();
        int natTop = m - (visible - 1) * lh;
        int natBottom = m + lh; // include descender of bottom row

        Vector3f translate = ctx.getMatrices().peek().getPositionMatrix()
                .getTranslation(new Vector3f());
        int tx = (int) translate.x;
        int ty = (int) translate.y;

        ctx.enableScissor(
                tx - 20,
                ty + natTop,
                tx + ctx.getScaledWindowWidth(),
                ty + natBottom);

        // Apply a uniform vertical translate so every row (including
        // the back-stepped extra row at the top and the addLinesUnder
        // extra row at the bottom) shifts together. Flush pending HUD
        // draw queue first so this only affects subsequent chat draws.
        int drawOff = phaze$getChatDrawOffset();
        if (drawOff != 0) {
            ctx.draw();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0.0F, (float) -drawOff, 0.0F);
            phaze$pushedMatrix = true;
        }
        return m;
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

    /**
     * Pop the matrix translate we pushed in mask, then drop the
     * scissor. Targets the only {@code lstore} in {@code render}
     * (slot 23 reused as a long after the int loop scope closes), which
     * fires immediately after the visible-line loop completes.
     */
    @ModifyVariable(method = "render", at = @At("STORE"))
    private long phaze$demask(long a, @Local(argsOnly = true) DrawContext ctx) {
        if (phaze$pushedMatrix) {
            ctx.draw();
            ctx.getMatrices().pop();
            phaze$pushedMatrix = false;
        }
        if (phaze$enabled() && !isChatHidden()) {
            ctx.disableScissor();
        }
        return a;
    }

    @Unique private boolean phaze$pushedMatrix = false;

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
