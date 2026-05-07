package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.other.LockSlot;
import vorga.phazeclient.implement.features.modules.other.ShiftTap;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void phaze$recordReach(PlayerEntity player, Entity target, CallbackInfo ci) {
        ReachHud reachHud = ReachHud.getInstance();
        if (reachHud.isEnabled()) {
            reachHud.recordHitDistance(player, target);
        }
        
        ShiftTap shiftTap = ShiftTap.getInstance();
        if (shiftTap.isEnabled()) {
            shiftTap.triggerShiftTap();
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void phaze$onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ShiftTap shiftTap = ShiftTap.getInstance();
        if (shiftTap.isEnabled()) {
            shiftTap.triggerShiftTap();
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void phaze$onClickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (actionType != SlotActionType.THROW) {
            return;
        }

        LockSlot lockSlot = LockSlot.getInstance();
        if (lockSlot == null || !lockSlot.isEnabled()) {
            return;
        }

        // Hotbar slots inside the player screen handler are 36..44 (= hotbar 0..8).
        // Offhand is slot 45 in the player screen handler.
        if (slotId >= 36 && slotId <= 44) {
            int hotbarIndex = slotId - 36;
            if (lockSlot.isHotbarSlotLocked(hotbarIndex)) {
                ci.cancel();
            }
        } else if (slotId == 45) {
            if (lockSlot.isOffhandLocked()) {
                ci.cancel();
            }
        }
    }
}
