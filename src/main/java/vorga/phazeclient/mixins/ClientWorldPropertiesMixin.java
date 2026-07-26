package vorga.phazeclient.mixins;

import net.minecraft.client.world.ClientWorld;
import vorga.phazeclient.implement.features.modules.other.TimeChanger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public class ClientWorldPropertiesMixin {
    @Inject(at = @At("TAIL"), method = "getTimeOfDay", cancellable = true)
    private void getTimeOfDay(CallbackInfoReturnable<Long> ci) {
        if (TimeChanger.getInstance().isTimeOverrideActive()) {
            ci.setReturnValue((long) TimeChanger.getInstance().timeValue.getValue());
        }
    }
}
