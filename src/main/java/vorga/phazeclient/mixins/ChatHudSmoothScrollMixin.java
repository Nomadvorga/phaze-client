package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Smooth scrolling for the in-game chat history. Vanilla snaps the message
 * list to a new {@code scrolledLines} value the moment the user wheels or
 * presses PgUp/PgDn; we keep a per-frame floating-point offset that absorbs
 * each integer jump and decays back to zero so the messages appear to slide
 * by the line-height delta.
 *
 * Implementation note: we don't need a scissor of our own because vanilla
 * already enables one for the chat area inside its {@code render} method.
 * Pushing a matrix translate at HEAD and popping at RETURN is enough; the
 * scissor clips the slide for us. There is one cosmetic side-effect: while
 * the slide is in-flight, a one-line gap can briefly appear at the leading
 * edge of the scroll because vanilla doesn't render the "next" line yet.
 * For the short durations involved (~150ms) this reads as a polish detail.
 */
@Mixin(net.minecraft.client.gui.hud.ChatHud.class)
public abstract class ChatHudSmoothScrollMixin {

    @Shadow private int scrolledLines;

    @Shadow protected abstract int getLineHeight();

    @Unique private int phaze$prevScrolledLines = 0;
    @Unique private float phaze$smoothOffset = 0.0F;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$pushedThisFrame = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$pushChatScroll(DrawContext context, int currentTick, int mouseX, int mouseY,
                                      boolean focused, CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isChatSmoothScrollEnabled()) {
            phaze$smoothOffset = 0.0F;
            phaze$prevScrolledLines = scrolledLines;
            phaze$lastFrameNanos = 0L;
            return;
        }

        // Capture vanilla's instant jump as an inverse visual offset so the
        // currently rendered batch starts in the SAME spot as last frame,
        // then decays toward zero (target) over subsequent frames.
        int delta = scrolledLines - phaze$prevScrolledLines;
        if (delta != 0) {
            phaze$smoothOffset -= delta * getLineHeight();
            phaze$prevScrolledLines = scrolledLines;
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

        float smoothness = module.smoothnessForSpeed(module.chatSmoothSpeed.getValue());
        float decay = (float) Math.pow(smoothness, dt);
        phaze$smoothOffset *= decay;
        if (Math.abs(phaze$smoothOffset) < 0.5F) {
            phaze$smoothOffset = 0.0F;
        }

        phaze$pushedThisFrame = false;
        if (phaze$smoothOffset != 0.0F) {
            context.draw();
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, phaze$smoothOffset, 0.0F);
            phaze$pushedThisFrame = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$popChatScroll(DrawContext context, int currentTick, int mouseX, int mouseY,
                                     boolean focused, CallbackInfo ci) {
        if (phaze$pushedThisFrame) {
            context.draw();
            context.getMatrices().pop();
            phaze$pushedThisFrame = false;
        }
    }
}
