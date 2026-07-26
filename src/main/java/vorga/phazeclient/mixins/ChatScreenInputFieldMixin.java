package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.HudCursorRelay;
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
 * <p>1.21.11 moved the chat input field out of {@code ChatScreen.render}:
 * it is now an {@code addDrawableChild} widget drawn by the
 * {@code super.render} call, so the old "push before the background
 * {@code fill}, pop after {@code TextFieldWidget.render}" pair no longer has
 * a {@code TextFieldWidget.render} call site to hook. The slide is therefore
 * applied as two separate push/pop pairs - one around the background
 * {@code fill}, one around the {@code Screen.render} widget pass - which
 * keeps the exact same coverage (background + input field) while leaving
 * {@code ChatHud.render} in between untranslated, so chat history does not
 * slide with the box. The overlay-rendered {@code ChatInputSuggestor} still
 * stays outside the animation.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenInputFieldMixin {

    @Shadow protected TextFieldWidget chatField;

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

    /** Pair 1: the input-box background {@code fill}. */
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
        // 1.21.11: DrawContext.getMatrices() is a 2D Matrix3x2fStack - translate
        // takes (x, y); the old third argument was the (always 0) GUI z.
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0F, phaze$displacement);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
                    shift = At.Shift.AFTER
            )
    )
    private void phaze$popInputSlide(DrawContext context, int mouseX, int mouseY, float delta,
                                     CallbackInfo ci) {
        if (phaze$displacement == 0.0F) {
            return;
        }
        context.getMatrices().popMatrix();
    }

    /**
     * Pair 2: the {@code super.render} widget pass, which is where 1.21.11
     * draws {@code chatField}. The StreamerMode password mask is applied here
     * too - see {@link #phaze$applyStreamerMask()} - because the old
     * {@code @Redirect} on {@code TextFieldWidget.render} has no call site to
     * redirect anymore.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void phaze$beforeChatFieldRender(DrawContext context, int mouseX, int mouseY, float delta,
                                             CallbackInfo ci) {
        if (phaze$displacement != 0.0F) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0F, phaze$displacement);
        }
        phaze$applyStreamerMask();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void phaze$afterChatFieldRender(DrawContext context, int mouseX, int mouseY, float delta,
                                            CallbackInfo ci) {
        phaze$restoreStreamerMask();
        if (phaze$displacement != 0.0F) {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void phaze$onRemoved(CallbackInfo ci) {
        // Reset so the next open re-triggers the slide.
        phaze$wasOpenedLastFrame = false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$applyHudEditorCursor(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Animations.getInstance().isDynamicCursorEnabled()) {
            HudCursorRelay.apply();
        }
    }

    /**
     * Stash for the original text between the StreamerMode swap and
     * the restore around the widget render pass. {@code null} when no
     * swap happened on the current frame.
     */
    @Unique
    private String phaze$savedChatText = null;

    /** The field instance whose text is currently swapped, or {@code null}. */
    @Unique
    private TextFieldWidget phaze$maskedField = null;

    /**
     * Brackets the chat-input field's draw with a temporary text swap
     * so the StreamerMode password mask actually shows on screen. We
     * bypass {@code setText} (which would fire
     * {@code setChangedListener} and trigger a Brigadier re-parse on
     * the masked text) by writing directly to the {@code private
     * String text} field via reflection - the swap is a single-frame
     * visual rewrite and never touches the onChanged path. Restored to
     * the original text immediately after the render pass so the next
     * frame / suggestor parse sees the user's actual input.
     *
     * <p>1.21.11: this used to be a {@code @Redirect} on
     * {@code TextFieldWidget.render} inside {@code ChatScreen.render}.
     * The field is an {@code addDrawableChild} widget now and is drawn
     * by {@code super.render}, so the swap straddles that call
     * instead. Same single-frame semantics, same blast radius: only
     * {@code ChatScreen}'s own {@code chatField} is touched, because
     * that is the only {@code TextFieldWidget} where a slash-command
     * can carry a password.
     */
    @Unique
    private void phaze$applyStreamerMask() {
        phaze$savedChatText = null;
        phaze$maskedField = null;

        TextFieldWidget field = this.chatField;
        if (field == null) {
            return;
        }
        StreamerMode streamer = StreamerMode.getInstance();
        String original = field.getText();
        boolean swap = streamer != null
                && streamer.isHidePasswordsEnabled()
                && original != null
                && !original.isEmpty()
                && original.charAt(0) == '/';
        if (!swap) {
            return;
        }
        String masked = StreamerMode.maskPasswordIfMatching(original);
        if (masked == null || masked.equals(original)) {
            return;
        }
        try {
            phaze$resolveTextField().set(field, masked);
            phaze$savedChatText = original;
            phaze$maskedField = field;
        } catch (Throwable ignored) {
            phaze$savedChatText = null;
            phaze$maskedField = null;
        }
    }

    @Unique
    private void phaze$restoreStreamerMask() {
        String original = phaze$savedChatText;
        TextFieldWidget field = phaze$maskedField;
        phaze$savedChatText = null;
        phaze$maskedField = null;
        if (original == null || field == null) {
            return;
        }
        try {
            phaze$resolveTextField().set(field, original);
        } catch (Throwable ignored) {
            // Last-ditch: setText restores even if we can't touch the
            // field directly, at the cost of one spurious onChanged
            // callback.
            field.setText(original);
        }
    }

    /**
     * Cached {@code text} field reflection. Resolved lazily on the
     * first masked render so we don't pay the lookup cost on every
     * un-masked frame. Yarn still maps the field name to {@code text}
     * on 1.21.11; mojang-mapped builds carry the same name. We probe
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
