package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Translator;

/**
 * Forwards every incoming {@link ChatHud#addMessage(Text,
 * MessageSignatureData, MessageIndicator)} call to the
 * {@link Translator} module so it can fire off an async translation.
 *
 * <h3>Why this overload</h3>
 * Both the public {@code addMessage(Text)} (used by the client to push
 * its own status messages) and the network-driven message path funnel
 * into this 3-arg overload. Hooking it once captures everything that
 * appears in the chat HUD - same target as
 * {@link ChatHudCollapseMixin} and {@link ChatHudNickHiderMixin}.
 *
 * <h3>Why HEAD inject and not @ModifyVariable</h3>
 * Translation is asynchronous (HTTP round-trip to Google Translate or
 * Apify takes hundreds of ms at minimum). We can't return the
 * translated {@link Text} synchronously, so instead the original
 * message passes through unchanged and {@link Translator#onIncomingChat}
 * schedules a background fetch that, on completion, posts a SECOND
 * chat row containing the translation. This avoids any need to delay
 * or rewrite vanilla's chat path.
 *
 * <h3>Recursion guard</h3>
 * The translation row is itself added via {@code chatHud.addMessage},
 * so this mixin would naively try to translate our own translation.
 * {@link Translator#isBypassActive()} is set during the re-add and
 * checked here to short-circuit; matches the proven pattern in
 * {@link ChatHudCollapseMixin}.
 *
 * <h3>Mixin order vs sibling chat mixins</h3>
 * <ul>
 *   <li>{@link ChatHudNickHiderMixin}: {@code @ModifyVariable} at HEAD,
 *       {@code argsOnly=true} - rewrites the {@code message} arg
 *       <em>before</em> any HEAD inject sees it. We therefore observe
 *       the (possibly nick-redacted) text, which is exactly what we
 *       want - we don't want to leak the player's real name to the
 *       translation API when NickHider is on.</li>
 *   <li>{@link ChatHudCollapseMixin}: also a HEAD inject. Mixin order
 *       between two HEAD injects is undefined by SpongePowered Mixin,
 *       but it doesn't matter for us - we never cancel, never modify
 *       state visible to other mixins, and our async work runs after
 *       both HEAD-inject mixins have returned.</li>
 * </ul>
 */
@Mixin(ChatHud.class)
public abstract class ChatHudTranslatorMixin {

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD")
    )
    private void phaze$translateIncoming(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        Translator translator = Translator.getInstance();
        if (translator == null || !translator.isEnabled() || translator.isBypassActive()) {
            return;
        }
        // Forward signature so the module can implement the "Only
        // Players" toggle (signature != null means the message was
        // cryptographically signed by a real player; null means it's
        // either a server broadcast or a client-internal note like
        // "Screenshot saved" / F3+B toggles).
        translator.onIncomingChat(message, signature);
    }
}
