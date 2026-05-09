package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.RemoteRulesService;

/**
 * Draws the live "Phaze: N online" counter in the top-left corner of
 * the main menu.
 *
 * <p>Renders at TAIL of {@link TitleScreen#render} so it sits on top
 * of every vanilla element (background, buttons, splash text). The
 * counter reads from {@link RemoteRulesService#getOnlineCount()},
 * which is updated as a side-effect of the same /api/module-rules
 * polling the rules system already does - no extra network traffic
 * is added by this overlay.
 *
 * <h3>State semantics</h3>
 * <ul>
 *   <li>{@code count < 0} - we haven't received a successful poll
 *       yet (cold start, or the server is unreachable). Renders a
 *       placeholder so the user sees something instead of "0".</li>
 *   <li>{@code count >= 0} - real number, formatted as e.g.
 *       "Phaze: 42 online".</li>
 * </ul>
 *
 * <p>Position is hard-coded at (4, 4) screen pixels which is the same
 * top-left padding vanilla uses for its FPS overlay - matching it
 * keeps the HUD visually anchored to a familiar corner.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenOnlineCounterMixin {

    /** Top-left padding in GUI-scaled pixels. Same as the vanilla F3 overlay. */
    private static final int PADDING = 4;

    /** Argb white-on-shadow; matches the vanilla debug overlay tone. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawOnlineCounter(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        int count = RemoteRulesService.getInstance().getOnlineCount();
        String text = count < 0
                ? "Phaze: connecting\u2026"   // U+2026 horizontal ellipsis
                : "Phaze: " + count + " online";

        context.drawTextWithShadow(client.textRenderer, text, PADDING, PADDING, TEXT_COLOR);
    }
}
