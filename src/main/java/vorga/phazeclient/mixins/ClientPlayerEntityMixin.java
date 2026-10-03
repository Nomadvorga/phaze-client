package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.base.util.HolyWorldFeatureControlService;
import vorga.phazeclient.base.util.RemoteRulesService;
import vorga.phazeclient.base.util.ServerUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.other.AutoNear;
import vorga.phazeclient.implement.features.modules.other.AutoReissue;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.LockSlot;
import vorga.phazeclient.implement.features.modules.other.MouseClicker;
import vorga.phazeclient.implement.features.modules.other.PotionAuto;
import vorga.phazeclient.implement.features.modules.other.ShiftTap;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    private static String phaze$lastObservedHost = null;

    @Inject(method = "requestRespawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        ComboCounterHud.getInstance().onWorldJoin();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        phaze$detectHostChange();
        phaze$enforceServerLocks();
        ShiftTap.getInstance().onTick();
        AutoNear.getInstance().tick();
        PotionAuto.getInstance().tick();
        FreeLook.getInstance().tick();
        AutoReissue.getInstance().tick();
        MouseClicker.getInstance().onTick();

        TotemTracker.getInstance().pruneStaleEntries();

        vorga.phazeclient.implement.features.modules.other.TrapTimer.getInstance().tick();

        vorga.phazeclient.implement.features.modules.other.FTHelper.getInstance().tickTrackedSnowballs();

        vorga.phazeclient.implement.features.modules.other.Predictions.getInstance().tickTrails();

        net.minecraft.client.MinecraftClient mc =
                net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.world != null) {
            vorga.phazeclient.implement.features.modules.hud.TpsHud.getInstance()
                    .recordSample(mc.world.getTime());
        }
    }

    private static void phaze$detectHostChange() {
        String current = ServerUtil.getCurrentServerHost();
        if (current == null) {
            current = "";
        }
        if (!current.equals(phaze$lastObservedHost)) {
            phaze$lastObservedHost = current;
            RemoteRulesService.getInstance().requestRefresh();
            HolyWorldFeatureControlService.getInstance().onHostChange();
        }
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void phaze$onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LockSlot lockSlot = LockSlot.getInstance();
        if (lockSlot == null || !lockSlot.isEnabled()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        PlayerInventory inventory = self.getInventory();

        if (lockSlot.isHotbarSlotLocked(inventory.getSelectedSlot())) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    private static void phaze$enforceServerLocks() {
        HolyWorldFeatureControlService.enforceServerLocks();
    }
}
