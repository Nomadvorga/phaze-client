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
import vorga.phazeclient.implement.features.modules.other.AutoEat;
import vorga.phazeclient.implement.features.modules.other.AutoGG;
import vorga.phazeclient.implement.features.modules.other.BattleInfo;
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
        
        ShiftTap shiftTap = ShiftTap.getInstance();
        if (shiftTap.isEnabled()) {
            shiftTap.triggerShiftTap();
        }

        // Auto GG: stamp the target as recently attacked so the kill
        // detector can credit a follow-up death to the local player.
        // Filtering to PlayerEntity (excluding self) here keeps the
        // tracker map small and avoids mob-kill noise.
        if (target instanceof PlayerEntity victim && !victim.equals(player)) {
            AutoGG autoGG = AutoGG.getInstance();
            if (autoGG != null) {
                autoGG.recordAttack(victim);
            }
            // Health Indicator: stamp the same victim so the HUD
            // widget knows which player's HP to surface for the next
            // {@code targetDelay} seconds. Module gates itself on
            // {@code isEnabled} when the HUD renderer reads back the
            // tracked target, so we don't gate the write here - that
            // way a mid-fight enable immediately surfaces the live
            // target instead of waiting for the next hit.
            HealthIndicator healthIndicator = HealthIndicator.getInstance();
            if (healthIndicator != null) {
                healthIndicator.recordAttack(victim);
            }
        }

        // Change Hand: flip the vanilla MainArm option on every
        // successful attack against any entity (players AND mobs per
        // the user spec - both should drive the alternating-slap
        // tell). The module itself gates on its Upon Impact toggle,
        // so disabled / bind-only mode is a quiet no-op here.
        ChangeHand changeHand = ChangeHand.getInstance();
        if (changeHand != null) {
            changeHand.onAttackEntity();
        }

        // Battle Info: feed reach / damage / combo samples on every
        // outgoing attack. Module is enabled-gated internally so we
        // can call unconditionally without an extra null check.
        BattleInfo battleInfo = BattleInfo.getInstance();
        if (battleInfo != null) {
            battleInfo.recordAttack(player, target);
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"))
    private void phaze$onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ShiftTap shiftTap = ShiftTap.getInstance();
        if (shiftTap.isEnabled()) {
            shiftTap.triggerShiftTap();
        }
    }

    /**
     * Vanilla calls {@code stopUsingItem} every tick that the use-key isn't
     * held while the player has an item active. Auto Eat starts the use
     * programmatically (no key held) so we have to suppress that automatic
     * stop while a bite is in progress; the use will still finish naturally
     * once {@code itemUseTimeLeft} reaches 0 via {@code Item.finishUsing}.
     */
    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void phaze$preventStopWhileAutoEating(PlayerEntity player, CallbackInfo ci) {
        AutoEat autoEat = AutoEat.getInstance();
        if (autoEat != null && autoEat.isAutoEating()) {
            ci.cancel();
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
