package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.menu.MainMenuScreen;
import vorga.phazeclient.implement.menu.MenuUiSettings;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void phaze$applyCustomGuiFpsLimit(CallbackInfoReturnable<Integer> cir) {
        if (this.client == null || this.client.getWindow() == null || this.client.getWindow().isMinimized()) {
            return;
        }

        if (!(this.client.currentScreen instanceof MainMenuScreen)) {
            return;
        }

        cir.setReturnValue(MenuUiSettings.getInstance().getGuiFpsLimit());
    }
}
