package vorga.phazeclient.mixins;

import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.PotionAuto;

@Mixin(PlayerInventory.class)
public class PlayerInventoryPotionMixin {

    @Shadow
    public int selectedSlot;

    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void phaze$onSetSelectedSlot(int slot, CallbackInfo ci) {
        PotionAuto potionAuto = PotionAuto.getInstance();
        if (potionAuto == null || !potionAuto.isEnabled() || !potionAuto.isDrinking()) {
            return;
        }

        int lockedSlot = potionAuto.getLockedHotbarSlot();
        if (lockedSlot < 0) {
            return;
        }

        if (slot != lockedSlot) {
            this.selectedSlot = lockedSlot;
            ci.cancel();
        }
    }
}
