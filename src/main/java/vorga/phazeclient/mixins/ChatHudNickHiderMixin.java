package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.implement.features.modules.other.NickHider;

/**
 * Rewrites the {@code message} parameter at the head of
 * {@link ChatHud#addMessage(Text, MessageSignatureData, MessageIndicator)}
 * so any subsequent inject (notably {@link ChatHudCollapseMixin}) and
 * vanilla's own line-wrapping see the username-redacted text. We use
 * {@link ModifyVariable} on argsOnly=true so the substitution lands in the
 * parameter slot before any other HEAD inject reads it.
 *
 * <p>Mutating the variable in place keeps the chat path linear: no
 * cancel-and-recall, no bypass flag, no risk of recursive re-entry. The
 * {@link NickHider#rewrite} call short-circuits to the original reference
 * when the module is disabled or the message doesn't contain the local
 * player's name, so the cost on the hot path is a single
 * {@code String.contains}.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudNickHiderMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text phaze$hideOwnNick(Text original) {
        NickHider hider = NickHider.getInstance();
        if (hider == null) {
            return original;
        }
        return hider.rewrite(original);
    }
}
