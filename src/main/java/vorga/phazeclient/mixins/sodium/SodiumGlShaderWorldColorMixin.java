package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;
import vorga.phazeclient.base.util.shader.WorldColorChunkShaderPatcher;

/** Injects live block/fluid correction into Sodium's final GLSL source. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlShader", remap = false)
public abstract class SodiumGlShaderWorldColorMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds;safeShaderSource(ILjava/lang/CharSequence;)V",
                    remap = false
            ),
            index = 1,
            remap = false,
            require = 1
    )
    private CharSequence phaze$patchTerrainShader(CharSequence source) {
        if (source == null) {
            return null;
        }
        String patched = ChunkAnimatorShaderPatcher.patch(source.toString());
        return WorldColorChunkShaderPatcher.patch(patched);
    }
}
