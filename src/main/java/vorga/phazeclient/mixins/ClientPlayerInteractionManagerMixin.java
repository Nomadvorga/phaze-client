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
import vorga.phazeclient.implement.features.modules.other.AutoGG;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;
import vorga.phazeclient.implement.features.modules.other.HealthIndicator;
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

        ShiftTap.getInstance().triggerShiftTapIfPlayerTarget(target, player);

        if (target instanceof PlayerEntity victim && !victim.equals(player)) {
            AutoGG autoGG = AutoGG.getInstance();
            if (autoGG != null) {
                autoGG.recordAttack(victim);
            }

            HealthIndicator healthIndicator = HealthIndicator.getInstance();
            if (healthIndicator != null) {
                healthIndicator.recordAttack(victim);
            }
        }

        ChangeHand changeHand = ChangeHand.getInstance();
        if (changeHand != null) {
            changeHand.onAttackEntity();
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void phaze$onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {

        vorga.phazeclient.implement.features.modules.other.TrapTimer trapTimer =
                vorga.phazeclient.implement.features.modules.other.TrapTimer.getInstance();
        if (trapTimer != null) {
            trapTimer.onItemUse(player, hand);
        }
    }

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void phaze$onAttackBlock(net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        vorga.phazeclient.implement.features.modules.other.PickaxeNotifier notifications =
                vorga.phazeclient.implement.features.modules.other.PickaxeNotifier.getInstance();
        if (notifications != null) {
            notifications.onAttackBlock();
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
