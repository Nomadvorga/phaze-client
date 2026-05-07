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
 * Smooth-scroll for the in-game chat HUD. Applies the same exponential
 * decay pattern we already use for {@link ScrollableWidgetSmoothScrollMixin}
 * and {@link ChatHudMessageSlideMixin}: track a float {@code displayLines}
 * that eases toward the integer {@code scrolledLines}, and translate the
 * chat render matrix by the lagging delta so the user sees the lines
 * physically slide into place when they roll the mouse wheel or hit
 * {@code resetScroll}.
 *
 * <p>Algorithmic reference: PingIsFun-style smooth-scrolling mods that
 * popularised the {@code pow(smoothness, dt)} frame-rate-independent
 * decay. We do <strong>not</strong> port their {@code scrolledLines}
 * back-stepping / sub-pixel ModifyVariable trick - it depends on the
 * exact LVT ordinal of the y-position local in vanilla's {@code render},
 * which is fragile across MC patches. Instead we accept that the matrix
 * translate causes the topmost incoming line to peek in from above and
 * the outgoing bottom line to slip below the chat anchor for the
 * duration of the slide; chat is anchored bottom-left of the screen so
 * the overflow lands in empty pixels and is visually inoffensive.
 *
 * <p>Coordinates the per-message slide already provided by
 * {@link ChatHudMessageSlideMixin}: that mixin only fires while
 * {@code scrolledLines == 0}, so the two never both push a translate at
 * the same time. The matrix push/pop is still balanced regardless of
 * which order the @Inject HEADs run.
 *
 * <p>{@code addMessage} bookend: when vanilla's {@code addMessage} sees
 * the user is scrolled up, it auto-bumps {@code scrolledLines} to keep
 * the visible window stable. We bump {@code displayLines} by the same
 * delta in lockstep so that auto-bump is invisible (no spurious slide
 * fires while the user reads older history).
 */
@Mixin(value = ChatHud.class, priority = 1500)
public abstract class ChatHudSmoothScrollMixin {

    @Shadow private int scrolledLines;

    @Shadow protected abstract int getLineHeight();

    /** Visual scroll position, possibly fractional. Lags behind {@link #scrolledLines}. */
    @Unique private float phaze$displayLines;
    @Unique private long phaze$lastFrameNanos = 0L;
    @Unique private boolean phaze$initialized = false;
    @Unique private boolean phaze$pushedMatrix = false;
    @Unique private int phaze$scrolledLinesBeforeAdd = 0;

    /** Snap-to-target threshold (lines). Below this we drop the matrix push. */
    @Unique private static final float SETTLE_EPSILON = 0.05F;

    @Unique
    private boolean phaze$enabled() {
        Animations module = Animations.getInstance();
        return module != null && module.isChatSmoothScrollEnabled();
    }

    /**
     * Capture {@code scrolledLines} before vanilla's auto-scroll logic
     * inside {@code addMessage} potentially bumps it (it does so when
     * the user is scrolled up, to keep the visible window stable).
     */
    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("HEAD"))
    private void phaze$addMessageH(ChatHudLine line, CallbackInfo ci) {
        phaze$scrolledLinesBeforeAdd = scrolledLines;
    }

    /**
     * Mirror the auto-scroll bump on {@code displayLines} so the new
     * message is invisible from a smooth-scroll standpoint - target and
     * display move together, the easing decay produces zero motion.
     */
    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("TAIL"))
    private void phaze$addMessageT(ChatHudLine line, CallbackInfo ci) {
        int delta = scrolledLines - phaze$scrolledLinesBeforeAdd;
        if (delta != 0) {
            phaze$displayLines += delta;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$renderH(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        phaze$pushedMatrix = false;

        if (!phaze$enabled()) {
            // Re-sync so a later toggle-on doesn't see a stale lag.
            phaze$displayLines = scrolledLines;
            phaze$initialized = true;
            return;
        }

        if (!phaze$initialized) {
            phaze$displayLines = scrolledLines;
            phaze$initialized = true;
            return;
        }

        long now = System.nanoTime();
        float dt;
        if (phaze$lastFrameNanos == 0L) {
            dt = 1.0F / 60.0F;
        } else {
            dt = (now - phaze$lastFrameNanos) / 1_000_000_000.0F;
            // Cap dt so an alt-tab pause doesn't snap the slide to its
            // target (makes the resume feel jumpy).
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$lastFrameNanos = now;

        Animations module = Animations.getInstance();
        float smoothness = module.smoothnessForSpeed(module.chatSmoothSpeed.getValue());
        double decay = Math.pow(smoothness, dt);
        phaze$displayLines = (float) ((phaze$displayLines - scrolledLines) * decay + scrolledLines);

        float diff = scrolledLines - phaze$displayLines;
        if (Math.abs(diff) < SETTLE_EPSILON) {
            phaze$displayLines = scrolledLines;
            return;
        }

        // diff > 0  -> displayLines is BEHIND scrolledLines (user just
        //              scrolled up; lines should appear shifted UP at
        //              first then slide DOWN into their settled rows).
        // diff < 0  -> displayLines is AHEAD (user just scrolled down or
        //              hit reset; lines slide UP into place).
        // Translate Y is negative (upwards) when diff is positive.
        float translateY = -diff * getLineHeight();

        // Flush any earlier-batched HUD draws BEFORE we translate so the
        // hotbar / scoreboard don't inherit our offset, mirroring the
        // pattern in ChatHudMessageSlideMixin.
        ctx.draw();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0.0F, translateY, 0.0F);
        phaze$pushedMatrix = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$renderT(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                               boolean focused, CallbackInfo ci) {
        if (!phaze$pushedMatrix) {
            return;
        }
        // Commit the translated chat batch with the matrix still active,
        // then pop so subsequent HUD passes draw at their normal anchor.
        ctx.draw();
        ctx.getMatrices().pop();
        phaze$pushedMatrix = false;
    }
}
