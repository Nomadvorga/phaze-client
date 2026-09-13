package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.base.util.shader.SodiumChunkAnimatorUniforms;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class DefaultChunkRendererChunkAnimatorMixin {
    @Inject(
            method = "setModelMatrixUniforms",
            at = @At("TAIL"),
            remap = false
    )
    private static void phaze$uploadChunkColumnAnimation(
            net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface shader,
            RenderRegion region,
            net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform camera,
            net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer chunkData,
            CallbackInfo ci) {
        SodiumChunkAnimatorUniforms.upload(region);
    }
}
