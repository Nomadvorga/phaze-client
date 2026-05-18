package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.BetterF3;
import vorga.phazeclient.implement.features.modules.other.BetterF3Renderer;

/**
 * Replaces vanilla {@link DebugHud#render(DrawContext)} with the
 * {@link BetterF3} layout when the module is enabled. Cancels the
 * vanilla call entirely so vanilla's left/right column dump never
 * runs - which is the whole point of the module (less clutter).
 *
 * <h3>Why HEAD inject + cancel</h3>
 * Vanilla's render method draws into the same context immediately.
 * Cancelling at HEAD lets us draw our compact layout without
 * worrying about partial vanilla output mixing with ours.
 * {@code shouldShowDebugHud()} (the F3 toggle gate) is checked by
 * the InGameHud caller, so this mixin only runs when the user has
 * F3 actively held.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudBetterF3Mixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$replaceDebugHud(DrawContext context, CallbackInfo ci) {
        BetterF3 module = BetterF3.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        BetterF3Renderer.render(context);
        ci.cancel();
    }
}
