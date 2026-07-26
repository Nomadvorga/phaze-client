package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorRenderHelper;

@Mixin(RenderPhase.class)
public abstract class RenderLayerBlendWorldColorMixin {
    @Inject(method = "startDrawing", at = @At("TAIL"), require = 0)
    private void phaze$enableBlend(CallbackInfo ci) {
        if ((Object) this instanceof RenderLayer layer && WorldColorRenderHelper.shouldForceBlendForLayer(layer)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    @Inject(method = "endDrawing", at = @At("HEAD"), require = 0)
    private void phaze$disableBlend(CallbackInfo ci) {
        if ((Object) this instanceof RenderLayer layer && WorldColorRenderHelper.shouldForceBlendForLayer(layer)) {
            RenderSystem.disableBlend();
        }
    }
}
