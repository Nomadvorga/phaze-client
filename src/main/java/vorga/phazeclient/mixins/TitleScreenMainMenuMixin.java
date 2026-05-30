package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.menu.MainMenuScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMainMenuMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void phaze$redirectToCustomMenu(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null
                && client.getOverlay() == null
                && !(client.currentScreen instanceof MainMenuScreen)) {
            client.setScreen(new MainMenuScreen());
            ci.cancel();
        }
    }
}
