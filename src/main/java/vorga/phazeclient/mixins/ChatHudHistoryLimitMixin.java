package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;

/**
 * Raises the vanilla 100-line cap on {@link ChatHud}'s three buffers
 * (visible-message ring, logical-message ring, and recent-input
 * history) so users can scroll farther back and recall older sent
 * messages.
 *
 * <h3>Why three call sites</h3>
 * Vanilla hard-codes {@code 100} as a {@code while (... > 100)}
 * trim guard inside three different methods of {@code ChatHud}:
 * <ul>
 *   <li>{@code addVisibleMessage} - the wrapped/rendered lines that
 *       drive the on-screen rolling window.</li>
 *   <li>{@code addMessage(ChatHudLine)} (private) - the logical
 *       messages list, which is what {@link ChatHud#getMessages}
 *       exposes for scrollback.</li>
 *   <li>{@code addToMessageHistory} - the previously-sent input
 *       history surfaced in the chat screen via Up/Down arrows.</li>
 * </ul>
 * Each occurrence shows up as a {@code BIPUSH 100} bytecode constant
 * load (or {@code SIPUSH 100} on some javac versions); MixinExtras's
 * {@link ModifyExpressionValue @ModifyExpressionValue} on
 * {@code @At(value = "CONSTANT", args = "intValue=100")} captures the
 * three uses cleanly without per-method targeting and without
 * fragile {@code @Redirect}s on the underlying list-size calls.
 *
 * <h3>Why one mixin instead of three</h3>
 * The three constant uses share an identical replacement value
 * derived from the same {@link ChatHelper#getChatHistoryLimit()}
 * accessor, and there is no cross-method state to keep distinct.
 * A single mixin with a multi-method {@code method} array applies
 * the same handler to all three call sites, which is the same
 * pattern the upstream
 * <a href="https://github.com/xBackpack/InfChatHistory">InfChatHistory</a>
 * mod uses on the Forge {@code ChatComponent}.
 *
 * <h3>Disabled-fast-path</h3>
 * {@code getChatHistoryLimit()} returns the vanilla {@code 100} when
 * the module or the toggle is off, so flipping either back instantly
 * restores the stock cap. The hook is therefore a per-message
 * accessor call plus an {@code int} compare - cheap enough to leave
 * always-active.
 *
 * <h3>Attribution</h3>
 * Adapted from {@code me.xbackpack.infchathistory.mixin.client.ChatMixin}
 * (CC0 1.0 Universal). See {@code THIRD_PARTY_LICENSES.md} at the
 * project root for the upstream notice.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudHistoryLimitMixin {

    @ModifyExpressionValue(
            method = {
                    "addVisibleMessage",
                    "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
                    "addToMessageHistory"
            },
            at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int phaze$expandHistoryLimit(int original) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null) {
            return original;
        }
        return helper.getChatHistoryLimit();
    }
}
