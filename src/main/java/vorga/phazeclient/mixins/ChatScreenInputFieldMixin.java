package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Slides the chat-screen input field (the box at the bottom of the screen
 * when the player presses T / / ) up from {@code FADE_OFFSET} pixels below
 * its rest position whenever the chat is opened. Uses the same back-out
 * cubic curve as the ChatAnimation reference mod (c1=1.70158), giving a
 * subtle spring overshoot near the end. Speed is intentionally not exposed
 * to the user - this is a pure feel-good polish animation, fixed at
 * {@code FADE_TIME=170} ms regardless of the {@code Chat Scroll Speed}
 * slider.
 *
 * <p>The translate is pushed right before vanilla's input-box background
 * {@code fill} call and popped right after the {@code TextFieldWidget.render}
 * call so it covers exactly the input field + its background, without
 * touching the overlay-rendered {@code ChatInputSuggestor} (which has its
 * own Z-translate to 200 to render above) or the parent {@code Screen.render}
 * which draws unrelated buttons.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenInputFieldMixin {

    /** Fade duration, ms. Hardcoded per ChatAnimation reference. */
    @Unique private static final float FADE_TIME = 170.0F;
    /** Vertical travel at scale 1080p, in GUI px. */
    @Unique private static final float FADE_OFFSET = 8.0F;
    /** Back-out cubic coefficient (the standard easing magic number). */
    @Unique private static final float C1 = 1.70158F;
    @Unique private static final float C3 = C1 + 1.0F;

    @Unique private boolean phaze$wasOpenedLastFrame = false;
    @Unique private long phaze$lastOpenNanos = 0L;
    @Unique private float phaze$displacement = 0.0F;

    @Unique
    private float phaze$calculateDisplacement() {
        Animations module = Animations.getInstance();
        if (module == null || !module.isSmoothInputFieldEnabled()) {
            return 0.0F;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0.0F;
        }
        // Stamp the open time on the first render frame after the screen
        // becomes active (or after the last close, see phaze$onRemoved).
        if (!phaze$wasOpenedLastFrame
                && client.player != null
                && !client.player.isSleeping()) {
            phaze$wasOpenedLastFrame = true;
            phaze$lastOpenNanos = System.nanoTime();
        }

        float screenFactor = client.getWindow().getHeight() / 1080.0F;
        float elapsedMs = (System.nanoTime() - phaze$lastOpenNanos) / 1_000_000.0F;
        if (elapsedMs > FADE_TIME) elapsedMs = FADE_TIME;
        float alpha = 1.0F - (elapsedMs / FADE_TIME);

        // Reverse-form back-out cubic: at alpha=1 (t=0) we sit at +1 of
        // FADE_OFFSET; at alpha=0 (t=FADE_TIME) we sit at 0; with a brief
        // negative (overshoot) excursion near the end thanks to the
        // (C3*a^3 - C1*a^2) shape.
        float modifiedAlpha = C3 * alpha * alpha * alpha - C1 * alpha * alpha;
        return modifiedAlpha * FADE_OFFSET * screenFactor;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void phaze$pushInputSlide(DrawContext context, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        phaze$displacement = phaze$calculateDisplacement();
        if (phaze$displacement == 0.0F) {
            return;
        }
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, phaze$displacement, 0.0F);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void phaze$popInputSlide(DrawContext context, int mouseX, int mouseY, float delta,
                                     CallbackInfo ci) {
        if (phaze$displacement == 0.0F) {
            return;
        }
        context.draw();
        context.getMatrices().pop();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void phaze$onRemoved(CallbackInfo ci) {
        // Reset so the next open re-triggers the slide.
        phaze$wasOpenedLastFrame = false;
    }
}
