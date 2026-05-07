package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;

/**
 * Hooks the central {@link ChatHud#addMessage(Text, MessageSignatureData, MessageIndicator)}
 * which both the public {@code addMessage(Text)} and the network message path
 * funnel into. When Chat Helper detects the incoming message duplicates the
 * most recent one, we cancel this call and re-add a collapsed
 * {@code "msg (Nx)"} variant via the same path under a bypass flag.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudCollapseMixin {

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$collapseRepeats(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null || !helper.isEnabled() || helper.isBypassActive()) {
            return;
        }

        ChatHud hud = (ChatHud) (Object) this;
        Text replacement = helper.tryCollapse(hud, message);
        if (replacement == null) {
            return;
        }

        ci.cancel();
        helper.runWithBypass(() -> hud.addMessage(replacement));
    }
}
