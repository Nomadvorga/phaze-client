package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ElytraUtility;

@Mixin(Keyboard.class)
public class KeyboardElytraUtilityMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyElytraUtility(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        ElytraUtility module = ElytraUtility.getInstance();
        if (module == null) {
            return;
        }
        module.onBindStateChanged(key, action);
    }
}
