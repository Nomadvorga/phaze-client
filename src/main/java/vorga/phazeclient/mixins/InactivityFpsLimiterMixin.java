package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.menu.MenuUiSettings;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void phaze$applyCustomGuiFpsLimit(CallbackInfoReturnable<Integer> cir) {
        if (this.client == null || this.client.getWindow() == null || this.client.getWindow().isMinimized()) {
            return;
        }

        // Apply the menu FPS cap to every out-of-world GUI (main menu,
        // singleplayer, multiplayer, mod menu, etc.) but never while a
        // world is loaded, even if the player opens pause / inventory.
        if (this.client.world != null || this.client.currentScreen == null) {
            return;
        }

        cir.setReturnValue(MenuUiSettings.getInstance().getGuiFpsLimit());
    }
}
