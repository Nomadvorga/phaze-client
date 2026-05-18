package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;

/**
 * Funnels every outgoing chat message through
 * {@link ChatHelper#maybeAntiCaps(String)} so the Anti-Caps toggle
 * can lowercase the body before it reaches the network layer.
 *
 * <h3>Why this hook point</h3>
 * {@link ClientPlayNetworkHandler#sendChatMessage(String)} is the
 * single funnel that both the vanilla {@code ChatScreen.sendMessage}
 * path and any modded chat-sender (Auto GG, command macros, etc.)
 * call into. Hooking it here covers every send path with one mixin
 * and avoids touching the chat-screen input box (which would also
 * affect the visible draft text and history scrubbing - we want
 * neither).
 *
 * <h3>Commands are intentionally untouched</h3>
 * Slash-prefixed input goes through a sibling
 * {@code sendChatCommand(String)} method, not this one. Even if a
 * command somehow funneled in, the Anti-Caps logic in
 * {@link ChatHelper#maybeAntiCaps(String)} short-circuits on a
 * leading slash, so command argument case is preserved.
 *
 * <h3>Why ModifyVariable, not Inject + cancel</h3>
 * The vanilla method has side effects (signature management,
 * dedupe with the input history, etc.) that we want to keep
 * verbatim. Rewriting the {@code String} argument slot in place
 * via {@code argsOnly = true} lets vanilla complete the rest of
 * the pipeline as if the user typed the lowercased text directly,
 * including pushing the (now lowercased) entry to the chat-screen
 * input history so the next Up arrow recalls what was actually
 * sent.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerAntiCapsMixin {

    @ModifyVariable(
            method = "sendChatMessage(Ljava/lang/String;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String phaze$lowercaseOutgoing(String content) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null) {
            return content;
        }
        return helper.maybeAntiCaps(content);
    }
}
