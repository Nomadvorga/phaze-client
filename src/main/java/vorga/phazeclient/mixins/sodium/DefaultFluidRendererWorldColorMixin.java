package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class DefaultFluidRendererWorldColorMixin {
    @Shadow @Final private int[] quadColors;

    @Inject(method = "writeQuad", at = @At("HEAD"), require = 0, remap = false)
    private void phaze$markFluidQuadForLiveColorCorrection(CallbackInfo ci) {
        if (quadColors == null) {
            return;
        }

        // Reserve alpha 254 as a fluid marker. The patched chunk vertex shader
        // restores alpha to 1.0 and applies live uniforms, so settings do not
        // require rebuilding already-loaded chunks.
        for (int i = 0; i < Math.min(quadColors.length, 4); i++) {
            quadColors[i] = (quadColors[i] & 0x00FFFFFF) | 0xFE000000;
        }
    }
}
