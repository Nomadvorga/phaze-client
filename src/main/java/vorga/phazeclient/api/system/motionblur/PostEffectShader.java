package vorga.phazeclient.api.system.motionblur;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.mixins.GameRendererAccessor;
import vorga.phazeclient.mixins.ShaderLoaderAccessor;

import java.util.Collections;
import java.util.LinkedHashMap;
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
    private boolean warnedAboutUniforms = false;

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
        requestedUniforms.put(name, new RequestedUniform(kind, data));
        if (!warnedAboutUniforms) {
            warnedAboutUniforms = true;
            LOGGER.warn("[Phaze] Post effect {} requested uniform '{}', but 1.21.11 has no "
                    + "per-draw uniform setter - values are recorded only until the std140 "
                    + "rewrite lands. The effect will run with its JSON defaults.", location, name);
        }
    }
}
