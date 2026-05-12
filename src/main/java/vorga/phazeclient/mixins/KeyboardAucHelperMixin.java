package vorga.phazeclient.mixins;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.AucHelper;

/**
 * Routes raw GLFW key events into {@link AucHelper#onBindPressed(int, int)}.
 * Kept as a tiny dedicated mixin (rather than folded into a shared
 * keyboard handler) because each PvP-helper module owns its own
 * bind dispatch path and we don't want one module's logic to leak
 * into another's class loader if it gets disabled / removed.
 *
 * <p>The handler is NOT cancellable - {@code /ah search} is a one-shot
 * side effect, not a key-input replacement, so vanilla still gets
 * the event afterwards for normal keymap processing.
 */
@Mixin(Keyboard.class)
public class KeyboardAucHelperMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void phaze$onKeyAucHelper(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        AucHelper.getInstance().onBindPressed(key, action);
    }
}
