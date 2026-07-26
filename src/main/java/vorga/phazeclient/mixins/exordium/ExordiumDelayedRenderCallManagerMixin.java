package vorga.phazeclient.mixins.exordium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;

/**
 * Draws Phaze's live transforms after Exordium has submitted the unchanged
 * cached HUD batch.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.exordium.util.DelayedRenderCallManager", remap = false)
public abstract class ExordiumDelayedRenderCallManagerMixin {
    @Inject(
            method = "renderComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/tr7zw/exordium/util/rendersystem/MultiStateHolder;apply()V",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void phaze$renderAnimatedCachedTextures(CallbackInfo ci) {
        ExordiumAnimationBridge.renderDeferredComponents();
    }
}
