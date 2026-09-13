package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.RemoteRulesService;
import vorga.phazeclient.implement.menu.MainMenuScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenOnlineCounterMixin {

    private static final int PADDING = 4;

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawOnlineCounter(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        vorga.phazeclient.implement.menu.AnnouncementOverlay.render(context);
        if (client.currentScreen instanceof MainMenuScreen) {
            return;
        }

        int count = RemoteRulesService.getInstance().getOnlineCount();
        String text = count < 0
                ? "Phaze: connecting\u2026"
                : "Phaze: " + count + " online";
        context.drawTextWithShadow(client.textRenderer, text, PADDING, PADDING, TEXT_COLOR);
    }
}
