package vorga.phazeclient.implement.cosmetics;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Rasterises and caches imported Blockbench cosmetics for catalog cards. */
public final class CosmeticGuiElementRenderer
        extends SpecialGuiElementRenderer<CosmeticGuiElementState> {
    private static final int EVICT_AFTER_FRAMES = 600;
    private static CosmeticGuiElementRenderer instance;

    private final Map<String, Thumbnail> thumbnails = new HashMap<>();
    private final ProjectionMatrix2 projection =
            new ProjectionMatrix2("PIP - phaze cosmetic", -1000.0F, 1000.0F, true);
    private long frame;
    private long rasterFrameId = Long.MIN_VALUE;

    public CosmeticGuiElementRenderer(VertexConsumerProvider.Immediate vertexConsumers) {
        super(vertexConsumers);
        instance = this;
    }

    public static boolean hasCachedThumbnail(String selection) {
        CosmeticGuiElementRenderer renderer = instance;
        if (renderer == null || selection == null) return false;
        String prefix = selection.toLowerCase(Locale.ROOT) + "@";
        for (Map.Entry<String, Thumbnail> entry : renderer.thumbnails.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().populated) return true;
        }
        return false;
    }

    @Override
    public Class<CosmeticGuiElementState> getElementClass() {
        return CosmeticGuiElementState.class;
    }

    @Override
    public void render(CosmeticGuiElementState state, GuiRenderState guiState, int guiScale) {
        int width = (state.x2() - state.x1()) * guiScale;
        int height = (state.y2() - state.y1()) * guiScale;
        if (width <= 0 || height <= 0) return;

        frame++;
        evictStale();
        String key = state.selection().toLowerCase(Locale.ROOT) + "@" + width + "x" + height;
        Thumbnail thumbnail = thumbnails.get(key);
        if (thumbnail == null) {
            // CPU parsing happens on the cosmetic preloader. Limit the final
            // GPU allocation/raster step to one entry per menu frame.
            if (rasterFrameId == state.frameId()) return;
            BlockbenchWingModel model = CosmeticsRenderer.thumbnailModel(state.selection());
            if (model == null) return;
            rasterFrameId = state.frameId();
            thumbnail = new Thumbnail(width, height);
            thumbnails.put(key, thumbnail);
            rasterise(state, thumbnail, width, height);
        }
        thumbnail.lastUsedFrame = frame;
        if (!thumbnail.populated || state.alpha() <= 0.0F) return;

        guiState.addSimpleElementToCurrentLayer(new TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.of(thumbnail.colorView,
                        RenderSystem.getSamplerCache().getRepeated(FilterMode.NEAREST)),
                state.pose(), state.x1(), state.y1(), state.x2(), state.y2(),
                0.0F, 1.0F, 1.0F, 0.0F,
                premultipliedWhite(state.alpha()), state.scissorArea()));
    }

    private void rasterise(CosmeticGuiElementState state, Thumbnail thumbnail,
                           int width, int height) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                thumbnail.color, 0, thumbnail.depth, 1.0D);
        RenderSystem.setProjectionMatrix(projection.set(width, height), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.outputColorTextureOverride = thumbnail.colorView;
        RenderSystem.outputDepthTextureOverride = thumbnail.depthView;
        try {
            MinecraftClient.getInstance().gameRenderer.getDiffuseLighting()
                    .setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);
            MatrixStack matrices = new MatrixStack();
            matrices.translate(width / 2.0F, height / 2.0F, 0.0F);
            renderModel(state, matrices, width, height);
            vertexConsumers.draw();
            thumbnail.populated = true;
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
        }
    }

    @Override
    protected void render(CosmeticGuiElementState state, MatrixStack matrices) {
        renderModel(state, matrices,
                state.x2() - state.x1(), state.y2() - state.y1());
    }

    private void renderModel(CosmeticGuiElementState state, MatrixStack matrices,
                             float width, float height) {
        BlockbenchWingModel model = CosmeticsRenderer.thumbnailModel(state.selection());
        if (model == null) return;

        BlockbenchWingModel.Bounds bounds = model.bounds();
        boolean frontIsXAxis = bounds.frontIsXAxis();
        float projectedWidth = frontIsXAxis ? bounds.depth() : bounds.width();
        float fit = Math.min(width * 0.84F / projectedWidth,
                height * 0.84F / bounds.height());

        matrices.scale(fit, -fit, fit);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frontIsXAxis ? 90.0F : 180.0F));
        String lower = state.selection().toLowerCase(Locale.ROOT);
        if (lower.contains("ally") || lower.contains("birb")) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
        } else if (lower.contains("turtle")) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        }
        if (CosmeticsState.isWing(state.selection())
                && !CosmeticsState.WIMGS.equalsIgnoreCase(state.selection())
                && !CosmeticsState.FLUFFY_WINGS.equalsIgnoreCase(state.selection())) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        }
        matrices.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
        model.renderCatalog(matrices, vertexConsumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, matrices.peek());
    }

    private static int premultipliedWhite(float alpha) {
        int channel = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (channel << 24) | (channel << 16) | (channel << 8) | channel;
    }

    private void evictStale() {
        if ((frame & 63L) != 0L) return;
        Iterator<Map.Entry<String, Thumbnail>> iterator = thumbnails.entrySet().iterator();
        while (iterator.hasNext()) {
            Thumbnail thumbnail = iterator.next().getValue();
            if (frame - thumbnail.lastUsedFrame < EVICT_AFTER_FRAMES) continue;
            thumbnail.close();
            iterator.remove();
        }
    }

    @Override
    protected String getName() {
        return "phaze cosmetic";
    }

    @Override
    public void close() {
        thumbnails.values().forEach(Thumbnail::close);
        thumbnails.clear();
        projection.close();
        if (instance == this) instance = null;
        super.close();
    }

    private static final class Thumbnail {
        private final GpuTexture color;
        private final GpuTextureView colorView;
        private final GpuTexture depth;
        private final GpuTextureView depthView;
        private boolean populated;
        private long lastUsedFrame;

        private Thumbnail(int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            color = device.createTexture(() -> "Phaze cosmetic thumbnail", 13,
                    TextureFormat.RGBA8, width, height, 1, 1);
            colorView = device.createTextureView(color);
            depth = device.createTexture(() -> "Phaze cosmetic thumbnail depth", 9,
                    TextureFormat.DEPTH32, width, height, 1, 1);
            depthView = device.createTextureView(depth);
        }

        private void close() {
            colorView.close();
            color.close();
            depthView.close();
            depth.close();
        }
    }
}
