package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.Zoom;
import org.lwjgl.glfw.GLFW;

@Mixin(Keyboard.class)
public class KeyboardZoomMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // FreeLook handling
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook != null && freeLook.isEnabled()) {
            freeLook.onBindStateChanged(key, action);
        }

        // Change Hand bind: flips vanilla MainArm on press. The
        // module short-circuits internally when Upon Impact is on or
        // the key is unbound, so we just forward every event here
        // and don't cancel the original key dispatch - typing the
        // bound letter into chat must still work normally.
        ChangeHand changeHand = ChangeHand.getInstance();
        if (changeHand != null && changeHand.isEnabled()) {
            changeHand.onBindStateChanged(key, action);
        }

        // Zoom handling
        int bindKey = Zoom.getInstance().keybind.getKey();
        
        if (bindKey == key && bindKey != GLFW.GLFW_KEY_UNKNOWN) {
            if (action == GLFW.GLFW_PRESS) {
                // Only process if module is enabled
                if (Zoom.getInstance().isEnabled()) {
                    if (Zoom.getInstance().isHold()) {
                        // Hold mode: enable zoom on press
                        Zoom.setZoomActive(true);
                    } else {
                        // Toggle mode: toggle zoom on press
                        Zoom.setZoomActive(!Zoom.isZoomActive());
                    }
                }
                ci.cancel();
            } else if (action == GLFW.GLFW_RELEASE) {
                // Only process if module is enabled and hold mode is on
                if (Zoom.getInstance().isEnabled() && Zoom.getInstance().isHold()) {
                    // Hold mode: disable zoom on release
                    Zoom.setZoomActive(false);
                }
                ci.cancel();
            }
        }
    }
}
