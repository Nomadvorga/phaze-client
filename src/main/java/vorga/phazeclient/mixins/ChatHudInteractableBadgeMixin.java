package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.MessageIndicator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.hud.ChatHudBadgeRenderAccess;
import vorga.phazeclient.api.system.hud.ChatMessageAnimationRenderState;
import vorga.phazeclient.base.util.PhazeBadgeUtil;

/** Draws Phaze badges through the interactive backend used while chat is open. */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Interactable")
abstract class ChatHudInteractableBadgeMixin implements ChatHudBadgeRenderAccess {
    @Shadow(remap = false) @Final private DrawContext context;

    @Inject(method = "indicator", at = @At("HEAD"))
    private void phaze$translateIndicator(int x1, int y1, int x2, int y2, float opacity,
                                          MessageIndicator indicator, CallbackInfo ci) {
        phaze$pushOffset();
    }

    @Inject(method = "indicator", at = @At("RETURN"))
    private void phaze$restoreIndicator(int x1, int y1, int x2, int y2, float opacity,
                                        MessageIndicator indicator, CallbackInfo ci) {
        phaze$popOffset();
    }

    private void phaze$pushOffset() {
        if (ChatMessageAnimationRenderState.active()) {
            context.getMatrices().translate(ChatMessageAnimationRenderState.dx(), ChatMessageAnimationRenderState.dy());
        }
    }

    private void phaze$popOffset() {
        if (ChatMessageAnimationRenderState.active()) {
            context.getMatrices().translate(-ChatMessageAnimationRenderState.dx(), -ChatMessageAnimationRenderState.dy());
        }
    }

    @Override
    public void phaze$drawBadge(int y, float opacity, boolean codeBadge) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
        int color = (alpha << 24) | 0x00FFFFFF;
        PhazeBadgeUtil.drawChatBadgeAsText(
                context,
                MinecraftClient.getInstance().textRenderer,
                -1.0F,
                y - 1.0F,
                color,
                codeBadge
        );
    }
}
