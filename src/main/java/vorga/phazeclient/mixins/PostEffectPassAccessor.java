package vorga.phazeclient.mixins;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Access to a pass' std140 uniform buffers so Phaze can rewrite them per frame.
 *
 * <p>1.21.11 has no per-draw uniform setter at all: {@code GlUniform} is an
 * empty marker interface and a {@link PostEffectPass} bakes its values into
 * {@code Map<String, GpuBuffer> uniformBuffers} once, at construction, from the
 * {@code uniforms} section of the post-effect JSON. That is fine for vanilla's
 * effects, whose values are constants, but Phaze's Color Correction and Motion
 * Blur are driven by module settings and camera matrices that change every
 * frame.
 *
 * <p>Writing straight into the buffer is the supported route: the pass hands
 * the same {@code GpuBuffer} to the render pass each frame, so overwriting its
 * contents beforehand is equivalent to having constructed it with those values.
 */
@Mixin(PostEffectPass.class)
public interface PostEffectPassAccessor {
    @Accessor("uniformBuffers")
    Map<String, GpuBuffer> phaze$getUniformBuffers();
}
