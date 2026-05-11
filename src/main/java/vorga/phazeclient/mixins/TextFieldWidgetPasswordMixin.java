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
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

/**
 * Visual password masking inside {@link ChatScreen}'s input box. The
 * masking strategy is a swap-on-render, not a permanent text rewrite:
 *
 * <ol>
 *   <li>At the HEAD of {@code TextFieldWidget#renderWidget} we read the
 *       raw text via the {@link Shadow}-ed {@code text} field, run it
 *       through {@link StreamerMode#maskPasswordIfMatching(String)}, and
 *       if the result differs we cache the original and overwrite the
 *       field with the masked copy.</li>
 *   <li>Vanilla's render code then runs unchanged, drawing whatever is
 *       in {@code text} - which is now the masked string.</li>
 *   <li>At the TAIL we restore the raw text from the cache.</li>
 * </ol>
 *
 * Because the mask preserves length and only swaps non-space characters
 * for {@code '*'}, every cursor / selection index that vanilla computes
 * against {@code text} stays valid against the masked variant: the
 * cursor stays on the same logical char, the highlight rect spans the
 * same column count, and {@code firstCharacterIndex} (the horizontal
 * scroll offset) keeps pointing at a real index. Pixel widths drift a
 * little because {@code '*'} is narrower than most letters, but the
 * cursor still lands on the masked column the user sees, which is the
 * UX-relevant invariant.
 *
 * <p>Scope is gated by {@code currentScreen instanceof ChatScreen} so
 * the masking never fires in regular text inputs (server list filter,
 * book editor, sign editor, anvil rename, etc.) where a typed slash
 * command would not actually be a chat command at all.
 */
@Mixin(TextFieldWidget.class)
public class TextFieldWidgetPasswordMixin {

    @Shadow private String text;

    /**
     * Stash for the original text between the HEAD swap and TAIL restore.
     * {@code null} when no swap happened on the current frame, so the
     * TAIL handler short-circuits without touching {@code text}.
     */
    @Unique
    private String phaze$savedText = null;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void phaze$maskPasswordHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        StreamerMode streamer = StreamerMode.getInstance();
        if (streamer == null || !streamer.isHidePasswordsEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || !(mc.currentScreen instanceof ChatScreen)) {
            // Only mask the chat input - other text fields (server name,
            // book editor, sign etc.) never carry chat commands so a slash
            // there is just literal text and must render verbatim.
            return;
        }
        if (this.text == null || this.text.isEmpty()) {
            return;
        }
        String masked = StreamerMode.maskPasswordIfMatching(this.text);
        if (masked == null || masked.equals(this.text)) {
            return;
        }
        phaze$savedText = this.text;
        this.text = masked;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void phaze$maskPasswordTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (phaze$savedText == null) {
            return;
        }
        this.text = phaze$savedText;
        phaze$savedText = null;
    }
}
