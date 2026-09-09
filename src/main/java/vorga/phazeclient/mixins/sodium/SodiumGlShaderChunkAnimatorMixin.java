package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;

/**
 * Per-section ChunkAnimator support at Sodium's lowest shader
 * entry point: {@code GlShader.<init>(ShaderType, Identifier, String)}.
 *
 * <p>Why hook here instead of {@code ShaderParser.parseShader}:
 * with Iris loaded and a shader pack active, Iris's
 * {@code SodiumPrograms#createGlShaders} bypasses
 * {@code ShaderParser.parseShader} entirely and constructs a
 * Sodium {@code GlShader} directly from its own
 * {@code glsl-transformer}-produced source. The
 * {@link ShaderParserChunkAnimatorMixin @ModifyReturnValue} hook on
 * {@code parseShader} never fires for terrain in that path, so the
 * patched uniforms aren't injected and the Java-side has to fall
 * back to region-level animation. Hooking the constructor catches
 * both Sodium's native compile path (parseShader -> GlShader) and
 * Iris's bypass (transformShaders -> GlShader), giving us
 * per-section animation in both cases.
 *
 * <p>The patcher's content-sniffing guards (presence of
 * {@code _vert_position} and {@code _draw_id} markers) keep this
 * safe to fire on every Sodium-routed shader; non-terrain shaders
 * (clouds, sky, framebuffer blits) return unchanged.
 */
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
