package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.other.AutoNear;
import vorga.phazeclient.implement.features.modules.other.AutoReissue;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.LockSlot;
import vorga.phazeclient.implement.features.modules.other.MouseClicker;
import vorga.phazeclient.implement.features.modules.other.PotionAuto;
import vorga.phazeclient.implement.features.modules.other.ShiftTap;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "requestRespawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        ComboCounterHud.getInstance().onWorldJoin();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        phaze$enforceServerLocks();
        ShiftTap.getInstance().onTick();
        AutoNear.getInstance().tick();
        PotionAuto.getInstance().tick();
        FreeLook.getInstance().tick();
        AutoReissue.getInstance().tick();
        MouseClicker.getInstance().onTick();
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void phaze$onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LockSlot lockSlot = LockSlot.getInstance();
        if (lockSlot == null || !lockSlot.isEnabled()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        PlayerInventory inventory = self.getInventory();
        if (lockSlot.isHotbarSlotLocked(inventory.selectedSlot)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    private static void phaze$enforceServerLocks() {
        Main main = Main.getInstance();
        if (main == null || main.getModuleProvider() == null) {
            return;
        }

        for (Module module : main.getModuleProvider().getModules()) {
            if (module.isShowEnable() && module.isState() && module.isServerLocked()) {
                module.setState(false);
            }
        }
    }
}
