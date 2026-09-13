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

@Mixin(ChatScreen.class)
public abstract class ChatScreenInputFieldMixin {

    @Shadow protected TextFieldWidget chatField;

    @Unique private static final float FADE_TIME = 170.0F;

    @Unique private static final float FADE_OFFSET = 8.0F;

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

        phaze$wasOpenedLastFrame = false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$applyHudEditorCursor(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HudCursorRelay.apply();
    }

    @Unique
    private String phaze$savedChatText = null;

    @Unique
    private TextFieldWidget phaze$maskedField = null;

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

            field.setText(original);
        }
    }

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
