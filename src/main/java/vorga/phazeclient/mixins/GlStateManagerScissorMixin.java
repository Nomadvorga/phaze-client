package vorga.phazeclient.mixins;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.shape.batched.GuiClipState;
import vorga.phazeclient.api.system.shape.batched.GuiDrawStats;

/** Batched GUI vertices carry the clip active at submission; see {@link GuiClipState}. */
@Mixin(value = GlStateManager.class, remap = false)
public abstract class GlStateManagerScissorMixin {
    @Inject(method = "_enableScissorTest", at = @At("HEAD"))
    private static void phaze$trackScissorOn(CallbackInfo ci) {
        GuiDrawStats.scissorChanges++;
        GuiClipState.onEnable();
    }

    @Inject(method = "_disableScissorTest", at = @At("HEAD"))
    private static void phaze$trackScissorOff(CallbackInfo ci) {
        GuiClipState.onDisable();
    }

    @Inject(method = "_scissorBox", at = @At("HEAD"))
    private static void phaze$trackScissorBox(int x, int y, int width, int height, CallbackInfo ci) {
        GuiClipState.onBox(x, y, width, height);
    }
}
