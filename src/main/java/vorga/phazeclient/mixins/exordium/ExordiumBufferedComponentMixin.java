package vorga.phazeclient.mixins.exordium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;

/** Defers only animated Exordium components; static components remain batched. */
@Pseudo
@Mixin(targets = "dev.tr7zw.exordium.render.BufferedComponent", remap = false)
public abstract class ExordiumBufferedComponentMixin {
    @Inject(method = "renderBuffer", at = @At("HEAD"), cancellable = true, remap = false)
    private void phaze$deferAnimatedTexture(CallbackInfo ci) {
        if (ExordiumAnimationBridge.deferBufferedComponent(this)) ci.cancel();
    }
}
