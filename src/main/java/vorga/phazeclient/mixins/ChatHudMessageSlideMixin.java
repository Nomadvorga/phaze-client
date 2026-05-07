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

    /** Vertical-displacement scale: 0.8 of a line height = ChatAnimation default. */
    @Unique private static final float DISPLACEMENT_SCALE = 0.8F;

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

        float maxDisplacement = getLineHeight() * DISPLACEMENT_SCALE;
        float displacement = maxDisplacement - (alpha * maxDisplacement);
        if (displacement <= 0.05F) {
            return;
        }

        // Flush the layered draw batch BEFORE we translate so the HUD
        // elements vanilla queued earlier (hotbar background etc.) render
        // at their original Y instead of inheriting our translate.
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, displacement, 0.0F);
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
