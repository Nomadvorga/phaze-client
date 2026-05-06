package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.AutoSwap;

@Mixin(Keyboard.class)
public class KeyboardAutoSwapMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKeyPressAutoSwap(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!AutoSwap.getInstance().isEnabled()) {
            return;
        }

        int bindKey = AutoSwap.getInstance().keybind.getKey();
        
        if (bindKey == key && bindKey != GLFW.GLFW_KEY_UNKNOWN && action == GLFW.GLFW_PRESS) {
            AutoSwap.getInstance().activateDirectSwap();
            ci.cancel();
        }
    }
}
