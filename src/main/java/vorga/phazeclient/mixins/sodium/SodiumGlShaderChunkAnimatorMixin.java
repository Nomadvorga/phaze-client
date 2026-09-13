package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlShader", remap = false)
public abstract class SodiumGlShaderChunkAnimatorMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds;safeShaderSource(ILjava/lang/CharSequence;)V",
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private CharSequence phaze$injectSodiumChunkAnim(CharSequence source) {
        if (source == null) {
            return null;
        }
        return ChunkAnimatorShaderPatcher.patch(source.toString());
    }
}
