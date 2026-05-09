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
import vorga.phazeclient.helpers.ChatScrollState;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Slide-in animation for newly received chat messages, modelled on the
 * <a href="https://modrinth.com/mod/chatanimation">ChatAnimation</a> mod.
 *
 * <p>On every {@code addMessage(ChatHudLine)} call (the private funnel that
 * both public overloads call into) we stamp {@code lastMessageNanos}. While
 * the chat is rendered we push a Y translate equal to
 * {@code lineHeight * 0.8 * (1 - alpha)} where {@code alpha} ramps linearly
 * from 0 to 1 over the {@link Animations#chatSlideFadeMs()} duration. The
 * effect: at t=0 the chat sits one (scaled) line below its normal anchor,
 * then slides up to its rest position - so the new message appears to push
 * the old ones up, even though the matrix translate is global.
 *
 * <p>The animation is suppressed while the user has the chat scrolled away
 * from the bottom ({@code scrolledLines != 0}); otherwise the displacement
 * would visually fight the user's scroll position.
 *
 * <p>This is the only chat-scroll animation we ship: a previous
 * matrix-decay smooth-scroll port (smsk-style back-step + sub-pixel
 * matrix translate) was removed because it inherently makes every row
 * slide a fractional pixel during the decay window, and there's no
 * way to keep both top and bottom rows static at the chat-box edges
 * while still moving the content continuously. The new-message slide
 * here side-steps that entirely by using time-since-arrival as the
 * animation driver - rows only move during the brief slide-in window
 * after a message arrives, and they all share one rigid translate.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMessageSlideMixin {

    @Shadow private int scrolledLines;

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
    @Unique private boolean phaze$pushedThisFrame = false;

    @Inject(
            method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            at = @At("TAIL")
    )
    private void phaze$stampMessageArrival(ChatHudLine line, CallbackInfo ci) {
        phaze$lastMessageNanos = System.nanoTime();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$pushSlide(DrawContext context, int currentTick, int mouseX, int mouseY,
                                 boolean focused, CallbackInfo ci) {
        phaze$pushedThisFrame = false;

        Animations module = Animations.getInstance();
        if (module == null || !module.isChatSmoothScrollEnabled()) {
            return;
        }
        // ChatHudSmoothScrollMixin back-steps scrolledLines mid-render to
        // fill the slide-in gap, so the field reads as non-zero even on
        // a freshly-bottomed chat. Defer to the suppress flag instead of
        // the raw field while it's active so we don't double-translate.
        if (ChatScrollState.suppressSlide) {
            return;
        }
        // While the user is scrolled up, vanilla pins the visible window
        // and the slide would make existing messages jitter. Skip.
        if (scrolledLines != 0) {
            return;
        }
        if (phaze$lastMessageNanos == 0L) {
            return;
        }

        float fadeMs = module.chatSlideFadeMs();
        if (fadeMs <= 0.0F) {
            return;
        }

        float lifetimeMs = (System.nanoTime() - phaze$lastMessageNanos) / 1_000_000.0F;
        if (lifetimeMs >= fadeMs) {
            // Animation finished - leave matrix untouched so vanilla draws
            // at its normal anchor.
            return;
        }

        float alpha = lifetimeMs / fadeMs;
        if (alpha < 0.0F) alpha = 0.0F;
        if (alpha > 1.0F) alpha = 1.0F;

        // Two different translate axes depending on user choice. The
        // "Up" style (default) keeps the original ChatAnimation feel:
        // a small vertical push that lets the new message appear to
        // shove the older ones up. The "Left" style is a much larger
        // horizontal slide that visually has the new chat enter from
        // beyond the left edge - what the user described as "from
        // the end of the screen". We do not combine the two: the user
        // explicitly wants the left slide to run *without* the scale
        // / vertical movement, so each branch picks one axis only.
        float dx;
        float dy;
        if (module.isChatMessageSlideLeft()) {
            float maxLeft = getWidth() * LEFT_DISPLACEMENT_SCALE;
            // Negative because we're sliding *from* the left: the
            // chat starts at -maxLeft and eases to 0 as alpha -> 1.
            // No half-pixel rounding needed - the chat-box background
            // covers the in-flight gap, so the font atlas isn't being
            // sampled at fractional offsets like in the Up case.
            dx = -maxLeft * (1.0F - alpha);
            dy = 0.0F;
            if (Math.abs(dx) < 1.0F) {
                return;
            }
        } else {
            float maxUp = getLineHeight() * UP_DISPLACEMENT_SCALE;
            // Round to integer pixels: avoids fractional Y translates
            // that sample the font atlas across half-pixel boundaries
            // (visible as a tearing artefact on the topmost glyph
            // row, especially with long messages whose wrapped lines
            // magnify the effect).
            dx = 0.0F;
            dy = Math.round(maxUp * (1.0F - alpha));
            if (dy < 1.0F) {
                return;
            }
        }

        // Flush the layered draw batch BEFORE we translate so the HUD
        // elements vanilla queued earlier (hotbar background etc.) render
        // at their original position instead of inheriting our translate.
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(dx, dy, 0.0F);
        phaze$pushedThisFrame = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$popSlide(DrawContext context, int currentTick, int mouseX, int mouseY,
                                boolean focused, CallbackInfo ci) {
        if (!phaze$pushedThisFrame) {
            return;
        }
        // Flush the translated chat batch with the matrix still active so
        // it commits at the displaced Y, then pop.
        context.draw();
        context.getMatrices().pop();
        phaze$pushedThisFrame = false;
    }
}
