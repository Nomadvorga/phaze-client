package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class BlockRendererWorldColorMixin {

    @Inject(method = "processQuad", at = @At("TAIL"))
    private void phaze$applyBlockWorldColor(MutableQuadViewImpl quad, CallbackInfo ci) {
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            quad.color(
                    vertexIndex,
                    WorldColorCorrectionController.applyToArgb(
                            WorldColorCorrectionController.Target.BLOCKS,
                            quad.color(vertexIndex)
                    )
            );
        }
    }
}
