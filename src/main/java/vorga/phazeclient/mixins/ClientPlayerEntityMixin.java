package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "requestRespawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        ComboCounterHud.getInstance().onWorldJoin();
    }
}
