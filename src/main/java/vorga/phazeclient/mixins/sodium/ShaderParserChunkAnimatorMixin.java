package vorga.phazeclient.mixins.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;

/**
 * Per-section ChunkAnimator support under Sodium - GLSL half.
 *
 * <p>Sodium's chunk vertex shader (assets/sodium/shaders/blocks/
 * block_layer_opaque.vsh) computes each vertex's world-space
 * position as
 * <pre>
 *   vec3 position = _vert_position + (u_RegionOffset + draw_translation);
 * </pre>
 * where {@code _vert_position} is the chunk-local vertex from the
 * vertex format and {@code draw_translation} is the per-section
 * translation derived from the per-vertex {@code _draw_id}
 * attribute via {@code _get_relative_chunk_coord(_draw_id)} (returns
 * the section's relative {@code (X, Y, Z)} within the region in
 * 8 / 4 / 8 cells).
 *
 * <p>Sodium passes <em>one</em> {@code u_RegionOffset} per region
 * call - that's all the per-region renderer can do without re-
 * uploading vertex data, which is why the previous region-level
 * implementation only animated chunks at 128-block boundaries.
 *
 * <p>This mixin patches the parsed shader source to:
 * <ol>
 *   <li>Declare a {@code uniform float u_PhazeChunkAnimY[256]}
 *       array sized to cover every possible {@code _draw_id}
 *       (which is 8 bits = 256 values, see
 *       {@code _get_relative_chunk_coord}'s masks {@code 7, 3, 7}).</li>
 *   <li>Add that uniform's section slot to {@code position.y}
 *       right where the position is computed, so the section
 *       drops in from above smoothly per-section instead of per-
 *       region.</li>
 * </ol>
 *
 * <p>The Java side {@link DefaultChunkRendererChunkAnimatorMixin}
 * uploads the per-section offsets into that uniform array once per
 * region per render layer pass. Together they reproduce the
 * vanilla per-section drop-in animation under Sodium.
 *
 * <p><b>Why {@link ModifyReturnValue} on {@link ShaderParser}:</b>
 * {@code ShaderParser.parseShader(name, constants)} is the single
 * funnel that produces the final GLSL source string for every
 * chunk shader Sodium compiles - it processes {@code #import}
 * directives and applies define constants, returning the
 * fully-resolved source. Patching there means we modify the
 * post-include source once per shader compile, regardless of which
 * Sodium {@code ChunkShaderOptions} variant is being compiled
 * (alpha cutoff, fog mode, etc.). The detector inside the
 * callback is permissive enough to skip non-terrain shaders
 * (clouds, etc.) by sniffing for tokens that only the chunk
 * vertex shader uses.
 */
@Mixin(value = ShaderParser.class, remap = false)
public abstract class ShaderParserChunkAnimatorMixin {

    @ModifyReturnValue(
            method = "parseShader(Ljava/lang/String;Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderConstants;)Ljava/lang/String;",
            at = @At("RETURN"),
            remap = false
    )
    private static String phaze$injectChunkAnim(String original) {
        // Delegate to the shared patcher - the same code path the
        // Iris-side mixin uses, so any tweak (marker change, regex
        // refinement, etc.) lands in both places at once.
        return ChunkAnimatorShaderPatcher.patch(original);
    }
}
