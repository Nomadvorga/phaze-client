package vorga.phazeclient.mixins.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;

@Mixin(value = ShaderParser.class, remap = false)
public abstract class ShaderParserChunkAnimatorMixin {

    @ModifyReturnValue(
            method = "parseShader(Ljava/lang/String;Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderConstants;)Ljava/lang/String;",
            at = @At("RETURN"),
            remap = false
    )
    private static String phaze$injectChunkAnim(String original) {
        return ChunkAnimatorShaderPatcher.patch(original);
    }
}
