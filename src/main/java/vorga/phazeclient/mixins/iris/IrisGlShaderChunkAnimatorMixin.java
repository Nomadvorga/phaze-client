package vorga.phazeclient.mixins.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;

/**
 * Per-section ChunkAnimator support under Iris+shaders, GLSL half.
 *
 * <p>When Iris is loaded with an active shader pack, chunk shaders
 * are compiled from the shader pack source instead of going through
 * Sodium's {@code ShaderParser.parseShader}. The Sodium-side
 * mixin's {@code @ModifyReturnValue} hook is therefore never called,
 * the patched uniforms aren't injected, and the per-section path
 * Java-side falls back to region-level animation via
 * {@code setRegionOffset}.
 *
 * <p>This mixin closes that gap by hooking Iris's own shader
 * compilation funnel: every shader Iris compiles flows through
 * {@code GlShader.<init>(ShaderType, String name, String source)}
 * which in turn calls a private static {@code createShader(ShaderType,
 * String, String)} that uploads the source to GL. We
 * {@code @ModifyArg} on the source argument of that
 * {@code createShader} INVOKE site - patching the post-Iris-
 * transformation source <em>before</em> it's compiled, without
 * touching the shader pack files on disk.
 *
 * <p><b>Why this works without modifying the shader pack:</b>
 * Iris-with-Sodium bridges the legacy {@code gl_Vertex} pipeline
 * to Sodium's vertex format - the transformed shader still defines
 * {@code _vert_position} and {@code _draw_id}, and constructs
 * {@code gl_Vertex} from them. Adding a per-section offset to
 * {@code _vert_position} right after its initial assignment
 * propagates through Iris's bridge into {@code gl_Vertex}, then
 * through the shader pack's own MVP transform into
 * {@code gl_Position} - producing the same per-chunk drop-in
 * effect the Sodium-native path achieves, with zero shader-pack
 * source modifications.
 *
 * <p><b>String target:</b> the {@code @Mixin} target is a string
 * because we don't want to take a compile-time dependency on Iris.
 * Modrinth ships several Iris jars per Minecraft version with
 * different APIs; binding to one would break the moment the user
 * updates. Instead we resolve the class only at runtime via
 * {@link vorga.phazeclient.mixins.PhazeMixinPlugin}, which gates
 * this mixin on {@code FabricLoader.isModLoaded("iris")}.
 *
 * <p>{@code remap = false} on every annotation: Iris classes are
 * not in the yarn mappings (they're third-party), so loom's refmap
 * generator must not try to remap them.
 */
@Mixin(targets = "net.irisshaders.iris.gl.shader.GlShader", remap = false)
public abstract class IrisGlShaderChunkAnimatorMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gl/shader/GlShader;createShader(Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;Ljava/lang/String;)I",
                    remap = false
            ),
            index = 2,
            remap = false
    )
    private static String phaze$injectIrisChunkAnim(String source) {
        // Same patcher Sodium's ShaderParser hook uses. Source
        // sniffing inside catches non-chunk shaders and unusual
        // pipelines, returning them unchanged - so this mixin is
        // safe to fire on every shader Iris compiles.
        return ChunkAnimatorShaderPatcher.patch(source);
    }
}
