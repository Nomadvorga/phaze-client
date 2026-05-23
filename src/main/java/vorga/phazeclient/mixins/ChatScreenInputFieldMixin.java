package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

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

    /**
     * Stash for the original text between the StreamerMode HEAD swap
     * and TAIL restore on the chat-input render call. {@code null}
     * when no swap happened on the current frame.
     */
    @Unique
    private String phaze$savedChatText = null;

    /**
     * Wraps {@link TextFieldWidget#render} on the chat-input field
     * with a temporary text swap so the StreamerMode password mask
     * actually shows on screen. We bypass {@code setText} (which
     * would fire {@code setChangedListener} and trigger a Brigadier
     * re-parse on the masked text) by writing directly to the
     * {@code private String text} field via reflection - the swap
     * is a single-frame visual rewrite and never touches the
     * onChanged path. Restored to the original text immediately
     * after the render call so the next frame / suggestor parse
     * sees the user's actual input.
     *
     * <p>Only the chat-input goes through this mixin because that
     * is the only {@code TextFieldWidget} where a slash-command can
     * carry a password. Other text fields (server names, book
     * editor, etc.) are untouched.
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"
            )
    )
    private void phaze$maskedChatRender(TextFieldWidget chatField,
                                        DrawContext context, int mouseX, int mouseY, float delta) {
        StreamerMode streamer = StreamerMode.getInstance();
        String original = chatField.getText();
        boolean swap = streamer != null
                && streamer.isHidePasswordsEnabled()
                && original != null
                && !original.isEmpty()
                && original.charAt(0) == '/';
        String masked = swap ? StreamerMode.maskPasswordIfMatching(original) : null;
        boolean didSwap = masked != null && !masked.equals(original);
        java.lang.reflect.Field textField = null;
        if (didSwap) {
            try {
                textField = phaze$resolveTextField();
                textField.set(chatField, masked);
            } catch (Throwable ignored) {
                didSwap = false;
            }
        }
        chatField.render(context, mouseX, mouseY, delta);
        if (didSwap) {
            try {
                textField.set(chatField, original);
            } catch (Throwable ignored) {
                // Last-ditch: setText restores even if we can't
                // touch the field directly, at the cost of one
                // spurious onChanged callback.
                chatField.setText(original);
            }
        }
    }

    /**
     * Cached {@code text} field reflection. Resolved lazily on the
     * first masked render so we don't pay the lookup cost on every
     * un-masked frame. Yarn maps the field name to {@code text} on
     * 1.21.4; mojang-mapped builds carry the same name. We probe
     * both candidates plus the obfuscated {@code field_2092} as a
     * fallback so a future remap doesn't silently disable the mask.
     */
    @Unique
    private static java.lang.reflect.Field phaze$cachedTextField = null;

    @Unique
    private static java.lang.reflect.Field phaze$resolveTextField() throws NoSuchFieldException {
        java.lang.reflect.Field f = phaze$cachedTextField;
        if (f != null) return f;
        Class<?> c = TextFieldWidget.class;
        NoSuchFieldException last = null;
        for (String name : new String[]{"text", "field_2092"}) {
            try {
                java.lang.reflect.Field candidate = c.getDeclaredField(name);
                candidate.setAccessible(true);
                phaze$cachedTextField = candidate;
                return candidate;
            } catch (NoSuchFieldException e) {
                last = e;
            }
        }
        throw last == null ? new NoSuchFieldException("text") : last;
    }
}
