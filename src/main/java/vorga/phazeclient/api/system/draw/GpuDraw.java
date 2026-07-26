package vorga.phazeclient.api.system.draw;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Low-level draw for the cases {@link net.minecraft.client.render.RenderLayer}
 * cannot express.
 *
 * <p>A {@code RenderLayer} declares its sampler through
 * {@code RenderSetup.texture(String, Identifier)} - it can only bind a
 * registered resource. Phaze also has to sample raw framebuffer colour
 * attachments (HUD cache, card snapshots, blur ping-pong), which have no
 * Identifier, so those draws go through the explicit
 * GpuDevice -> CommandEncoder -> RenderPass path here.
 *
 * <p>Everything below was verified against the remapped 1.21.11 jar,
 * including the argument order of {@code drawIndexed}, which reads
 * {@code (baseVertex, firstIndex, indexCount, instanceCount)} - confirmed
 * from {@code GlCommandEncoder.drawObjectWithRenderPass} emitting
 * {@code glDrawElementsInstancedBaseVertex}.
 */
public final class GpuDraw {

    private GpuDraw() {
    }

    /**
     * Draws {@code built} with {@code pipeline}, binding {@code textureView}
     * as the pipeline's {@code samplerName} sampler.
     *
     * @param textureView may be null when the pipeline declares no sampler.
     *                    Prefer a view the framebuffer owns
     *                    ({@code Framebuffer.getColorAttachmentView()}) -
     *                    {@code GpuDevice.createTextureView} hands back an
     *                    owned AutoCloseable and leaks if made per frame.
     * @param modelView   value for the {@code DynamicTransforms} UBO.
     *                    {@code bindDefaultUniforms} does NOT supply this one.
     * @param tint        colour modulator; {@code (1,1,1,1)} for none.
     */
    public static void draw(RenderPipeline pipeline,
                            BuiltBuffer built,
                            String samplerName,
                            GpuTextureView textureView,
                            FilterMode filter,
                            Matrix4f modelView,
                            Vector4f tint) {
        RenderSystem.assertOnRenderThread();
        if (built == null) {
            return;
        }

        BuiltBuffer.DrawParameters params = built.getDrawParameters();
        if (params.indexCount() == 0) {
            return;
        }

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        GpuTextureView colorView = framebuffer.getColorAttachmentView();
        GpuTextureView depthView = framebuffer.useDepthAttachment
                ? framebuffer.getDepthAttachmentView()
                : null;

        VertexFormat format = pipeline.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(built.getBuffer());

        // getSortedBuffer() is only non-null after an explicit sortQuads().
        ByteBuffer sorted = built.getSortedBuffer();
        GpuBuffer indexBuffer;
        VertexFormat.IndexType indexType;
        if (sorted == null) {
            RenderSystem.ShapeIndexBuffer shape = RenderSystem.getSequentialBuffer(params.mode());
            // Order matters: grow() inside getIndexBuffer writes the index
            // type, so reading it first can yield a stale SHORT on a batch
            // with more than 65535 indices.
            indexBuffer = shape.getIndexBuffer(params.indexCount());
            indexType = shape.getIndexType();
        } else {
            indexBuffer = format.uploadImmediateIndexBuffer(sorted);
            indexType = params.indexType();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(modelView, tint, new Vector3f(), new Matrix4f());

        // Samplers come from the shared cache and must never be closed -
        // SamplerCache owns all of them.
        GpuSampler sampler = textureView != null
                ? RenderSystem.getSamplerCache().get(filter)
                : null;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "phaze/draw",
                colorView,
                OptionalInt.empty(),      // empty = preserve, do not clear
                depthView,
                OptionalDouble.empty())) {

            pass.setPipeline(pipeline);

            ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.isEnabled()) {
                pass.enableScissor(scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
            }

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertexBuffer);
            if (textureView != null) {
                // A mistyped sampler name is silently skipped and shows up as
                // black output, never as an exception.
                pass.bindTexture(samplerName, textureView, sampler);
            }
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, params.indexCount(), 1);
        }
    }

    /** Convenience overload: no tint, identity model-view. */
    public static void draw(RenderPipeline pipeline,
                            BuiltBuffer built,
                            String samplerName,
                            GpuTextureView textureView,
                            FilterMode filter,
                            Matrix4f modelView) {
        draw(pipeline, built, samplerName, textureView, filter, modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
    }
}
