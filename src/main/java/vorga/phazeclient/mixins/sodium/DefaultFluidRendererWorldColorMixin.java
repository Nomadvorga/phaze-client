package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tags Sodium fluid vertices without rebuilding chunks when settings move. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class DefaultFluidRendererWorldColorMixin {
    @Shadow(remap = false) @Final
    private int[] quadColors;

    @Inject(method = "writeQuad", at = @At("HEAD"), remap = false, require = 1)
    private void phaze$markFluidQuad(CallbackInfo ci) {
        for (int i = 0; i < Math.min(quadColors.length, 4); i++) {
            // Alpha 254 is decoded by the patched chunk vertex shader and
            // immediately restored to 1.0 before rasterization.
            quadColors[i] = (quadColors[i] & 0x00FFFFFF) | 0xFE000000;
        }
    }
}
