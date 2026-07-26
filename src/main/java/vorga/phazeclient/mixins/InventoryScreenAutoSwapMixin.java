package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.ServerUtil;
import vorga.phazeclient.implement.features.modules.other.AutoSwap;

@Mixin(InventoryScreen.class)
public class InventoryScreenAutoSwapMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderInventory(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Cancel rendering if AutoSwap is active on FunTime or FunTrainer
        if (AutoSwap.getInstance().isSwapping()) {
            if (ServerUtil.isFunTimeServer() || ServerUtil.isFunTrainerServer()) {
                ci.cancel();
            }
        }
    }
}
