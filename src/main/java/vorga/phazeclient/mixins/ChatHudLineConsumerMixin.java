package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.hud.ChatHudBadgeRenderAccess;
import vorga.phazeclient.api.system.hud.ChatMessageAnimationRenderState;

/** Marks the visible line currently being submitted to ChatHud's 1.21.11 backend. */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$1")
abstract class ChatHudLineConsumerMixin {
    @Inject(method = "accept(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;IF)V", at = @At("HEAD"))
    private void phaze$beginMessageAnimation(ChatHudLine.Visible line, int index, float opacity, CallbackInfo ci) {
        ChatMessageAnimationRenderState.begin(line);
    }

    @Inject(method = "accept(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;IF)V", at = @At("RETURN"))
    private void phaze$endMessageAnimation(ChatHudLine.Visible line, int index, float opacity, CallbackInfo ci) {
        ChatMessageAnimationRenderState.end();
    }

    /**
     * The text backend stores its own pose. Alter that pose around the exact
     * text submission, rather than DrawContext's current matrix, so every
     * glyph (including styled runs) receives the line offset.
     */
    @WrapOperation(
            method = "accept(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHud$Backend;text(IFLnet/minecraft/text/OrderedText;)Z"
            )
    )
    private boolean phaze$offsetAllGlyphs(@Coerce Object backend, int y, float opacity, OrderedText text,
                                          Operation<Boolean> original) {
        boolean translated = ChatMessageAnimationRenderState.active()
                && backend instanceof ChatHudBackendPoseAccess;
        if (translated) {
            ((ChatHudBackendPoseAccess) backend).phaze$updatePose(ChatHudLineConsumerMixin::phaze$translateActive);
        }
        try {
            phaze$drawBadgeIfNeeded(backend, y, opacity);
            return original.call(backend, y, opacity, text);
        } finally {
            if (translated) {
                ((ChatHudBackendPoseAccess) backend).phaze$updatePose(ChatHudLineConsumerMixin::phaze$restoreActive);
            }
        }
    }

    private static void phaze$drawBadgeIfNeeded(Object backend, int y, float opacity) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (!(backend instanceof ChatHudBadgeRenderAccess badgeRenderer)
                || client == null || client.inGameHud == null
                || !(client.inGameHud.getChatHud() instanceof vorga.phazeclient.api.system.hud.ChatAnimationFrameAccess access)) {
            return;
        }
        ChatHudLine.Visible line = ChatMessageAnimationRenderState.line();
        if (access.phaze$shouldDrawChatBadge(line)) {
            badgeRenderer.phaze$drawBadge(y, opacity, access.phaze$isCodeBadge(line));
        }
    }

    private static void phaze$translateActive(org.joml.Matrix3x2f pose) {
        pose.translate(ChatMessageAnimationRenderState.dx(), ChatMessageAnimationRenderState.dy());
    }

    private static void phaze$restoreActive(org.joml.Matrix3x2f pose) {
        pose.translate(-ChatMessageAnimationRenderState.dx(), -ChatMessageAnimationRenderState.dy());
    }
}
