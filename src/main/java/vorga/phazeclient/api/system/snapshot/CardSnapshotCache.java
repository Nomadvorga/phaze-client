package vorga.phazeclient.api.system.snapshot;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.draw.GpuDraw;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.UniformType;

public final class CardSnapshotCache {
    private static final Rectangle RECTANGLE = new Rectangle();

    private static final Map<Object, Snapshot> CACHE = new IdentityHashMap<>();

    private static CaptureState active = null;

    private static ProjectionMatrix2 cardProjection;

    private static final Matrix4f BLIT_GUI_POSE = new Matrix4f();

    private CardSnapshotCache() {
    }

    public static final class Snapshot {

        Framebuffer fbo;
        int fbWidth;
        int fbHeight;

        public int hash;
        public boolean populated;
    }

    private static final class CaptureState {
        Snapshot snapshot;
        GpuBufferSlice savedProjection;
        ProjectionType savedProjectionType;
        GpuTextureView savedColorOverride;
        GpuTextureView savedDepthOverride;
        boolean savedScissorEnabled;
        int savedScissorX, savedScissorY, savedScissorW, savedScissorH;
    }

    public static Snapshot getOrCreate(Object key, int fbWidth, int fbHeight) {
        Snapshot snap = CACHE.computeIfAbsent(key, k -> new Snapshot());
        if (snap.fbo == null || snap.fbWidth != fbWidth || snap.fbHeight != fbHeight) {
            if (snap.fbo != null) {
                snap.fbo.delete();
            }

            snap.fbo = new SimpleFramebuffer("phaze/card_snapshot", fbWidth, fbHeight, true);
            snap.fbWidth = fbWidth;
            snap.fbHeight = fbHeight;
            snap.populated = false;
        }
        return snap;
    }

    public static void invalidate(Object key) {
        Snapshot snap = CACHE.remove(key);
        if (snap != null && snap.fbo != null) {
            snap.fbo.delete();
        }
    }

    public static void clearAll() {

        List<Snapshot> snapshots = new ArrayList<>(CACHE.values());
        CACHE.clear();
        for (Snapshot s : snapshots) {
            if (s.fbo != null) {
                s.fbo.delete();
            }
        }
    }

    public static void beginCapture(Snapshot snapshot, float widthGui, float heightGui) {
        if (snapshot == null || snapshot.fbo == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        BatchedRectangle.flushIfBatching();

        CaptureState s = new CaptureState();
        s.snapshot = snapshot;
        s.savedProjection = RenderSystem.getProjectionMatrixBuffer();
        s.savedProjectionType = RenderSystem.getProjectionType();
        s.savedColorOverride = RenderSystem.outputColorTextureOverride;
        s.savedDepthOverride = RenderSystem.outputDepthTextureOverride;

        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        s.savedScissorEnabled = scissor.isEnabled();
        if (s.savedScissorEnabled) {
            s.savedScissorX = scissor.getX();
            s.savedScissorY = scissor.getY();
            s.savedScissorW = scissor.getWidth();
            s.savedScissorH = scissor.getHeight();

            RenderSystem.disableScissorForRenderTypeDraws();
        }

        GpuTexture color = snapshot.fbo.getColorAttachment();
        GpuTexture depth = snapshot.fbo.getDepthAttachment();
        if (depth != null) {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(color, 0x00000000, depth, 1.0);
        } else {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(color, 0x00000000);
        }

        RenderSystem.outputColorTextureOverride = snapshot.fbo.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = snapshot.fbo.useDepthAttachment
                ? snapshot.fbo.getDepthAttachmentView()
                : null;

        if (cardProjection == null) {
            cardProjection = new ProjectionMatrix2("phaze/card snapshot", -1000.0F, 1000.0F, true);
        }
        RenderSystem.setProjectionMatrix(
                cardProjection.set(widthGui, heightGui), ProjectionType.ORTHOGRAPHIC);

        BatchedRectangle.setRenderTargetFbHeight(snapshot.fbHeight);

        active = s;
    }

    public static void endCapture() {
        if (active == null) {
            return;
        }
        CaptureState s = active;
        active = null;

        BatchedRectangle.flushIfBatching();

        BatchedRectangle.clearRenderTargetFbHeight();

        RenderSystem.outputColorTextureOverride = s.savedColorOverride;
        RenderSystem.outputDepthTextureOverride = s.savedDepthOverride;

        if (s.savedProjection != null) {
            RenderSystem.setProjectionMatrix(s.savedProjection, s.savedProjectionType);
        }
        if (s.savedScissorEnabled) {

            RenderSystem.enableScissorForRenderTypeDraws(s.savedScissorX, s.savedScissorY, s.savedScissorW, s.savedScissorH);
        }

        s.snapshot.populated = true;
    }

    private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE, DestFactor.ZERO))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderPipeline DST_ALPHA_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/card_snapshot_rounded"))
            .withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withFragmentShader(Identifier.of("minecraft", "core/position_tex_color"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(new BlendFunction(SourceFactor.DST_ALPHA, DestFactor.ONE_MINUS_DST_ALPHA))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    public static void blit(DrawContext context, Snapshot snapshot, float x, float y, float widthGui, float heightGui, float alpha) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }

        BatchedRectangle.flushIfBatching();

        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());

        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {

            vorga.phazeclient.api.system.draw.GuiProjection.begin();
            try {

                GpuDraw.draw(TRANSLUCENT_PIPELINE, built, "Sampler0",
                        snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR,
                        vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(BLIT_GUI_POSE));
            } finally {
                vorga.phazeclient.api.system.draw.GuiProjection.end();
                built.close();
            }
        }
    }

    public static void blitRounded(
            DrawContext context,
            Snapshot snapshot,
            float x,
            float y,
            float widthGui,
            float heightGui,
            float alpha,
            float round,
            int backgroundColor
    ) {
        if (snapshot == null || snapshot.fbo == null || !snapshot.populated) {
            return;
        }

        BatchedRectangle.flushIfBatching();
        RECTANGLE.render(ShapeProperties.create(context.getMatrices(), x, y, widthGui, heightGui)
                .round(round)
                .color(backgroundColor)
                .build());

        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int color = (alphaByte << 24) | 0x00FFFFFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F).color(color);
        buffer.vertex(matrix, x, y + heightGui, 0.0F).texture(0.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y + heightGui, 0.0F).texture(1.0F, 0.0F).color(color);
        buffer.vertex(matrix, x + widthGui, y, 0.0F).texture(1.0F, 1.0F).color(color);

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {

            vorga.phazeclient.api.system.draw.GuiProjection.begin();
            try {
                GpuDraw.draw(DST_ALPHA_PIPELINE, built, "Sampler0",
                        snapshot.fbo.getColorAttachmentView(), FilterMode.LINEAR,
                        vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(BLIT_GUI_POSE));
            } finally {
                vorga.phazeclient.api.system.draw.GuiProjection.end();
                built.close();
            }
        }
    }

    public static void copyRegionFromFramebuffer(Snapshot snapshot, Framebuffer source, int srcX, int srcY, int srcWidth, int srcHeight) {
        if (snapshot == null || snapshot.fbo == null || source == null || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }

        BatchedRectangle.flushIfBatching();

        GpuTexture src = source.getColorAttachment();
        GpuTexture dst = snapshot.fbo.getColorAttachment();
        if (src == null || dst == null) {
            return;
        }

        int width = Math.min(Math.min(srcWidth, snapshot.fbWidth), Math.max(0, src.getWidth(0) - srcX));
        int height = Math.min(Math.min(srcHeight, snapshot.fbHeight), Math.max(0, src.getHeight(0) - srcY));
        if (width <= 0 || height <= 0) {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(src, dst, 0, 0, 0, srcX, srcY, width, height);
        snapshot.populated = true;
    }
}
