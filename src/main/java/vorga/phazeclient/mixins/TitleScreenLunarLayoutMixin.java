package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.menu.LunarMainMenuScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenLunarLayoutMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void phaze$redirectToCustomMenu(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && !(client.currentScreen instanceof LunarMainMenuScreen)) {
            client.setScreen(new LunarMainMenuScreen());
        }
        ci.cancel();
    }
}
