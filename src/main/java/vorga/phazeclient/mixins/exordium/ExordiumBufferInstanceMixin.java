package vorga.phazeclient.mixins.exordium;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;

/**
 * Optional lifecycle hooks for Exordium's per-component cache.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.exordium.components.BufferInstance", remap = false)
public abstract class ExordiumBufferInstanceMixin {
    @Inject(method = "renderBuffer", at = @At("HEAD"), remap = false)
    private void phaze$beforeRenderBuffer(
            Object state,
            DrawContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ExordiumAnimationBridge.beforeBufferRender(this, context);
    }

    @Inject(method = "renderBuffer", at = @At("RETURN"), remap = false)
    private void phaze$afterRenderBuffer(
            Object state,
            DrawContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ExordiumAnimationBridge.afterBufferRender(this);
    }

    @Inject(method = "postRender", at = @At("RETURN"), remap = false)
    private void phaze$afterPostRender(
            Object state,
            DrawContext context,
            CallbackInfo ci
    ) {
        ExordiumAnimationBridge.afterBufferPostRender(this);
    }
}
