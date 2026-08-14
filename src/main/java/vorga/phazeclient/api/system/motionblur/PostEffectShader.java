package vorga.phazeclient.api.system.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import org.lwjgl.system.MemoryStack;
import vorga.phazeclient.mixins.PostEffectPassAccessor;
import vorga.phazeclient.mixins.PostEffectProcessorAccessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.mixins.GameRendererAccessor;
import vorga.phazeclient.mixins.ShaderLoaderAccessor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thin wrapper around a vanilla {@link PostEffectProcessor}.
 *
 * <h2>1.21.11 port notes</h2>
 * <ul>
 *   <li>{@code RenderSystem.resetTextureMatrix()} is gone - the fixed-function
 *       texture matrix no longer exists, so there is nothing to reset.</li>
 *   <li>{@code Framebuffer.beginWrite(boolean)} is gone. The render target is
 *       chosen per render pass now, and {@code PostEffectProcessor.render}
 *       drives its own frame graph that already outputs to {@code minecraft:main},
 *       so re-binding the main framebuffer afterwards is both impossible and
 *       unnecessary.</li>
 *   <li><b>Loose shader uniforms no longer exist.</b> {@code PostEffectPass}
 *       has no {@code getProgram()} and {@code GlUniform} is a bare marker
 *       interface with no {@code set(...)} of any kind. A pass' uniform values
 *       are baked into private {@code Map<String, GpuBuffer> uniformBuffers}
 *       std140 blocks at construction time, from the {@code uniforms} section
 *       of the post-effect JSON. There is no per-frame setter.
 *       <p>TODO(1.21.11): the real fix is a rewrite (plan item B4): declare a
 *       {@code layout(std140) uniform} block in the GLSL, list it in the
 *       post-effect JSON, and write the {@code GpuBuffer} directly (needs an
 *       accesswidener entry for {@code PostEffectPass.uniformBuffers}) or
 *       rebuild the {@code PostEffectProcessor} through
 *       {@code PostEffectProcessor.parseEffect(PostEffectPipeline, ...)}.
 *       Until then every {@code setUniformValue} call is <em>recorded</em> in
 *       {@link #getRequestedUniforms()} rather than uploaded, so the rewrite
 *       has the exact set of names/types/values it must carry, and callers keep
 *       compiling and keep their change-detection logic intact.</p></li>
 * </ul>
 *
 * <p>Practical consequence today: both Phaze post effects ship GLSL with loose
 * uniforms, which will not link in 1.21.11, so
 * {@code Cache.getOrLoadProcessor} throws, {@link #errored} latches and
 * {@link #render(float)} becomes a no-op. The feature is off rather than
 * rendering with wrong values.
 */
public class PostEffectShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("phaze/PostEffectShader");

    /** Which std140 type a recorded value must be written as by the B4 rewrite. */
    public enum UniformKind { FLOAT, INT, MATRIX4 }

    /** A uniform value Phaze wants applied, kept until the UBO rewrite can consume it. */
    public record RequestedUniform(UniformKind kind, float[] data) {}

    private final Identifier location;
    private final Consumer<PostEffectShader> initCallback;
    private final Map<String, RequestedUniform> requestedUniforms = new LinkedHashMap<>();
    private PostEffectProcessor processor;
    private boolean initialized = false;
    private boolean errored = false;
    private String uniformBlockName;
    private String[] uniformBlockMembers;
    private boolean uniformsDirty = false;

    public PostEffectShader(Identifier location, Consumer<PostEffectShader> initCallback) {
        this.location = location;
        this.initCallback = initCallback;
    }

    public PostEffectShader(Identifier location) {
        this(location, s -> {});
    }

    private void ensureInitialized() {
        if (initialized || errored) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ShaderLoader shaderLoader = client.getShaderLoader();
            ShaderLoader.Cache cache = ((ShaderLoaderAccessor) shaderLoader).getCache();
            this.processor = cache.getOrLoadProcessor(location, DefaultFramebufferSet.MAIN_ONLY);
            this.initialized = true;
            this.initCallback.accept(this);
        } catch (Exception e) {
            this.errored = true;
        }
    }

    public void render(float tickDelta) {
        ensureInitialized();
        if (processor == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        // 1.21.11: no resetTextureMatrix, and no beginWrite afterwards - see the
        // class javadoc. render(Framebuffer, ObjectAllocator) still exists and
        // GameRenderer's pool is now net.minecraft.client.util.memory.ObjectPool,
        // which implements ObjectAllocator - but the overload is @Deprecated;
        // vanilla now drives post effects through
        // render(FrameGraphBuilder, int, int, FramebufferSet). Moving to that is
        // part of the same B4 rewrite as the uniforms.
        // Values must reach the std140 buffer BEFORE the pass reads it, and
        // outside any open render pass - writeToBuffer refuses to run inside
        // one.
        uploadUniforms();

        processor.render(client.getFramebuffer(), ((GameRendererAccessor) client.gameRenderer).getPool());
    }

    public void setUniformValue(String name, float value) {
        record(name, UniformKind.FLOAT, value);
    }

    public void setUniformValue(String name, float v0, float v1) {
        record(name, UniformKind.FLOAT, v0, v1);
    }

    public void setUniformValue(String name, float v0, float v1, float v2) {
        record(name, UniformKind.FLOAT, v0, v1, v2);
    }

    public void setUniformValue(String name, float v0, float v1, float v2, float v3) {
        record(name, UniformKind.FLOAT, v0, v1, v2, v3);
    }

    public void setUniformValue(String name, int value) {
        record(name, UniformKind.INT, value);
    }

    public void setUniformValue(String name, int v0, int v1) {
        record(name, UniformKind.INT, v0, v1);
    }

    public void setUniformValue(String name, Matrix4f value) {
        // Column-major, the layout both GLSL and Std140Builder expect.
        record(name, UniformKind.MATRIX4, value.get(new float[16]));
    }

    public void reload() {
        this.processor = null;
        this.initialized = false;
        this.errored = false;
        // Deliberately NOT clearing requestedUniforms: it is the record of what
        // Phaze wants applied, not of what has been uploaded, and callers reset
        // their own change-detection guards on reload anyway.
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * The latest value requested for every uniform name, in first-set order.
     *
     * <p>Seam for the B4 rewrite: nothing uploads these yet (see class javadoc).
     */
    public Map<String, RequestedUniform> getRequestedUniforms() {
        return Collections.unmodifiableMap(requestedUniforms);
    }

    private void record(String name, UniformKind kind, float... data) {
        ensureInitialized();
        RequestedUniform previous = requestedUniforms.put(name, new RequestedUniform(kind, data));
        if (previous == null || previous.kind() != kind || !java.util.Arrays.equals(previous.data(), data)) {
            uniformsDirty = true;
        }
    }

    /**
     * Declare the std140 block this effect writes, and the order of its members.
     *
     * <p>The order MUST match the {@code uniforms} list in the post-effect JSON
     * and the block declaration in the GLSL, because all three describe the
     * same byte layout and only the JSON one is visible to vanilla. A mismatch
     * does not fail loudly - it silently feeds each value to the wrong member.
     */
    public void declareUniformBlock(String blockName, String... members) {
        this.uniformBlockName = blockName;
        this.uniformBlockMembers = members;
        this.uniformsDirty = true;
    }

    /**
     * Write the recorded values into the pass' std140 buffer.
     *
     * <p>Only runs when something actually changed - the buffer keeps its
     * contents between frames, so re-uploading an unchanged block would be pure
     * bus traffic for no visual difference. That matters here: Color
     * Correction's values only move when the user drags a slider, so in the
     * steady state this costs nothing at all; Motion Blur, whose matrices
     * change every frame, pays one small write.
     */
    private void uploadUniforms() {
        if (uniformBlockName == null || uniformBlockMembers == null) return;
        if (!uniformsDirty || processor == null) return;

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).getPasses();
        if (passes == null) return;

        for (PostEffectPass pass : passes) {
            Map<String, GpuBuffer> buffers = ((PostEffectPassAccessor) pass).phaze$getUniformBuffers();
            if (buffers == null) continue;
            GpuBuffer buffer = buffers.get(uniformBlockName);
            if (buffer == null) continue;

            // PostEffectPass allocates its uniform buffers as USAGE_UNIFORM
            // only - they are written once at construction and never again, so
            // vanilla has no reason to ask for USAGE_COPY_DST. writeToBuffer
            // rejects a destination without that bit ("Buffer needs
            // USAGE_COPY_DST to be a destination for a copy"), so the first
            // upload crashed the game.
            //
            // Swap in an equivalently sized buffer that does allow copies. The
            // pass reads the map every frame, so it picks up the replacement
            // transparently, and closing the original here hands its slot over
            // cleanly rather than leaking it - the pass will close ours in its
            // own close(), which is the same lifetime it gave the original.
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
                final String label = uniformBlockName;
                GpuBuffer writable = RenderSystem.getDevice().createBuffer(
                        () -> "phaze/" + label,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        buffer.size());
                buffers.put(uniformBlockName, writable);
                buffer.close();
                buffer = writable;
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, (int) buffer.size());
                for (String member : uniformBlockMembers) {
                    write(builder, requestedUniforms.get(member));
                }
                RenderSystem.getDevice().createCommandEncoder()
                        .writeToBuffer(buffer.slice(), builder.get());
            }
            uniformsDirty = false;
            return;
        }
    }

    /**
     * A member the module never set still has to advance its slot, or every
     * later member shifts and the whole block decodes wrong. Zero is the right
     * filler: the recorded map is keyed by the same names the JSON declares, so
     * an absent entry means the module genuinely never drives that value.
     */
    private static void write(Std140Builder builder, RequestedUniform uniform) {
        if (uniform == null) {
            builder.putFloat(0.0F);
            return;
        }
        float[] d = uniform.data();
        switch (uniform.kind()) {
            case MATRIX4 -> builder.putMat4f(new Matrix4f().set(d));
            case INT -> {
                if (d.length >= 2) {
                    builder.putIVec2((int) d[0], (int) d[1]);
                } else {
                    builder.putInt((int) d[0]);
                }
            }
            case FLOAT -> {
                switch (d.length) {
                    case 4 -> builder.putVec4(d[0], d[1], d[2], d[3]);
                    case 3 -> builder.putVec3(d[0], d[1], d[2]);
                    case 2 -> builder.putVec2(d[0], d[1]);
                    default -> builder.putFloat(d[0]);
                }
            }
        }
    }
}
