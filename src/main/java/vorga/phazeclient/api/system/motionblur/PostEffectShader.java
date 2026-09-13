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

public class PostEffectShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("phaze/PostEffectShader");

    public enum UniformKind { FLOAT, INT, MATRIX4 }

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
    private Identifier copyOutput;

    public void useCopyOutput(Identifier output) {
        if (initialized) throw new IllegalStateException("Output must be configured before initialization");
        copyOutput = output;
    }

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
            this.processor = cache.getOrLoadProcessor(location, copyOutput == null
                    ? DefaultFramebufferSet.MAIN_ONLY : java.util.Set.of(PostEffectProcessor.MAIN, copyOutput));
            this.uniformsDirty = true;
            this.initialized = true;
            this.initCallback.accept(this);
        } catch (Exception e) {
            this.errored = true;
            LOGGER.error("Failed to initialize post effect {}", location, e);
        }
    }

    public void render(float tickDelta) {
        ensureInitialized();
        if (processor == null) return;

        MinecraftClient client = MinecraftClient.getInstance();

        uploadUniforms();

        if (copyOutput == null) {
            processor.render(client.getFramebuffer(), ((GameRendererAccessor) client.gameRenderer).getPool());
        } else {
            renderWithCopyOutput(client);
        }
    }

    private void renderWithCopyOutput(MinecraftClient client) {
        net.minecraft.client.gl.Framebuffer main = client.getFramebuffer();
        int width = main.textureWidth;
        int height = main.textureHeight;
        if (width <= 0 || height <= 0) return;
        net.minecraft.client.render.FrameGraphBuilder graph = new net.minecraft.client.render.FrameGraphBuilder();
        Map<Identifier, net.minecraft.client.util.Handle<net.minecraft.client.gl.Framebuffer>> targets = new LinkedHashMap<>();
        targets.put(PostEffectProcessor.MAIN, graph.createObjectNode("phaze/main", main));
        targets.put(copyOutput, graph.createResourceHandle("phaze/post_output",
                new net.minecraft.client.gl.SimpleFramebufferFactory(width, height, false, 0)));
        PostEffectProcessor.FramebufferSet set = new PostEffectProcessor.FramebufferSet() {
            public void set(Identifier id, net.minecraft.client.util.Handle<net.minecraft.client.gl.Framebuffer> handle) {
                targets.put(id, handle);
            }
            public net.minecraft.client.util.Handle<net.minecraft.client.gl.Framebuffer> get(Identifier id) {
                return targets.get(id);
            }
        };
        processor.render(graph, width, height, set);
        net.minecraft.client.render.FramePass copy = graph.createPass("phaze/copy_post_output");
        var source = set.getOrThrow(copyOutput);
        copy.dependsOn(source);
        var destination = copy.transfer(set.getOrThrow(PostEffectProcessor.MAIN));
        set.set(PostEffectProcessor.MAIN, destination);
        copy.setRenderer(() -> RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                source.get().getColorAttachment(), destination.get().getColorAttachment(),
                0, 0, 0, 0, 0, width, height));
        graph.run(((GameRendererAccessor) client.gameRenderer).getPool());
    }

    public void setUniformValue(String name, float value) {
        RequestedUniform previous = requestedUniforms.get(name);
        if (previous != null && previous.kind() == UniformKind.FLOAT
                && previous.data().length == 1 && previous.data()[0] == value) return;
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
        RequestedUniform previous = requestedUniforms.get(name);
        if (previous != null && previous.kind() == UniformKind.INT
                && previous.data().length == 1 && previous.data()[0] == value) return;
        record(name, UniformKind.INT, value);
    }

    public void setUniformValue(String name, int v0, int v1) {
        record(name, UniformKind.INT, v0, v1);
    }

    public void setUniformValue(String name, Matrix4f value) {

        record(name, UniformKind.MATRIX4, value.get(new float[16]));
    }

    public void reload() {
        this.processor = null;
        this.initialized = false;
        this.errored = false;
        this.uniformsDirty = true;

    }

    public boolean isInitialized() {
        return initialized;
    }

    public Map<String, RequestedUniform> getRequestedUniforms() {
        return Collections.unmodifiableMap(requestedUniforms);
    }

    private void record(String name, UniformKind kind, float... data) {
        ensureInitialized();
        RequestedUniform previous = requestedUniforms.get(name);
        if (previous == null || previous.kind() != kind || !java.util.Arrays.equals(previous.data(), data)) {
            requestedUniforms.put(name, new RequestedUniform(kind, data));
            uniformsDirty = true;
        }
    }

    public void declareUniformBlock(String blockName, String... members) {
        this.uniformBlockName = blockName;
        this.uniformBlockMembers = members;
        this.uniformsDirty = true;
    }

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
