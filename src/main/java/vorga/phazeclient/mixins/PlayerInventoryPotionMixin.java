package vorga.phazeclient.mixins;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.AutoEat;
import vorga.phazeclient.implement.features.modules.other.LockSlot;
import vorga.phazeclient.implement.features.modules.other.PotionAuto;

@Mixin(PlayerInventory.class)
public class PlayerInventoryPotionMixin {

    // 1.21.11: PlayerInventory.selectedSlot is `private int` (it used to be
    // public). A @Shadow must not declare *lower* visibility than the target,
    // and declaring a *higher* one silently widens the vanilla field, so match
    // the real modifier exactly.
    @Shadow
    private int selectedSlot;

    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void phaze$onSetSelectedSlot(int slot, CallbackInfo ci) {
        PotionAuto potionAuto = PotionAuto.getInstance();
        if (potionAuto != null && potionAuto.isEnabled() && potionAuto.isDrinking()) {
            int lockedSlot = potionAuto.getLockedHotbarSlot();
            if (lockedSlot >= 0 && slot != lockedSlot) {
                this.selectedSlot = lockedSlot;
                ci.cancel();
                return;
            }
        }

        // Auto Eat lock: while we're mid-bite the player isn't allowed to
        // swap hotbar slots (number keys / scroll wheel both end up here).
        // AutoEat's own swaps still go through: on 1.21.11 it can no longer
        // poke the (now private) field directly and routes through
        // setSelectedSlot like everyone else, but both of its calls happen
        // while its `eating` flag is false - it selects the food slot before
        // setting the flag, and clears the flag before restoring the previous
        // slot in finishEating - so isAutoEating() is false for both.
        AutoEat autoEat = AutoEat.getInstance();
        if (autoEat != null && autoEat.isAutoEating()) {
            ci.cancel();
        }
    }

    /**
     * NOTE: {@code PlayerInventory.dropSelectedItem(boolean)} returns
     * {@link ItemStack} (the stack that was removed), NOT {@code boolean} -
     * only the {@code ClientPlayerEntity} override of the same name returns
     * boolean. The callback generic has to match the inventory signature or
     * mixin refuses to apply the injection at launch. Returning
     * {@link ItemStack#EMPTY} is the "nothing was dropped" value vanilla
     * itself uses for an empty selected stack, so the caller
     * ({@code ClientPlayerEntity.dropSelectedItem}) correctly reports false.
     */
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void phaze$onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
        LockSlot lockSlot = LockSlot.getInstance();
        if (lockSlot != null && lockSlot.isHotbarSlotLocked(this.selectedSlot)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
