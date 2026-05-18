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

import vorga.phazeclient.implement.features.modules.hud.BattleInfo;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.other.AutoEat;
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
    }

    /**
     * BattleInfo's combo metric reads from {@link
     * vorga.phazeclient.implement.features.modules.hud.ComboCounterHud}
     * which is incremented inside {@code PlayerEntity.attack} - i.e.
     * <em>during</em> the body of {@code attackEntity}. Injecting at
     * RETURN guarantees the combo counter has already been bumped by
     * the time we read it, so the rolling combo average sees the
     * post-hit value (1 on the first hit, 2 on the second, etc.)
     * instead of the stale pre-hit value an HEAD inject would see.
     * Reach / damage are recorded inside this method too rather than
     * in the HEAD block above so the whole combat-summary record
     * arrives as one atomic snapshot per attack.
     */
    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void phaze$recordBattleInfo(PlayerEntity player, Entity target, CallbackInfo ci) {
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
        // Trap Timer: arm the local trapka cooldown stopwatch when
        // the held item matches the configured (item type + name)
        // pair. Module gates internally so a non-trapka click is a
        // single instanceof + map probe.
        vorga.phazeclient.implement.features.modules.other.TrapTimer trapTimer =
                vorga.phazeclient.implement.features.modules.other.TrapTimer.getInstance();
        if (trapTimer != null) {
            trapTimer.onItemUse(player, hand);
        }
    }

    /**
     * Pickaxe Notifications: hook the player's left-click-on-block
     * dispatch so the durability check fires every swing without us
     * having to touch the block-breaking progress path. The vanilla
     * method runs on a successful left-click that hits a block, which
     * is exactly when we want to evaluate "is this the swing that
     * breaks my pickaxe?".
     */
    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void phaze$onAttackBlock(net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        vorga.phazeclient.implement.features.modules.other.PickaxeNotifier notifications =
                vorga.phazeclient.implement.features.modules.other.PickaxeNotifier.getInstance();
        if (notifications != null) {
            notifications.onAttackBlock();
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
