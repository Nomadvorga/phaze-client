package vorga.phazeclient.api.system.shape.implement;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import vorga.phazeclient.api.system.draw.DrawEngineImpl;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.implement.features.modules.client.Theme;

import net.minecraft.client.gl.UniformType;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalInt;

public class Blur implements Shape {
    public static final Blur INSTANCE = new Blur();

    public static boolean hudImmediateDrawsSuppressed = false;
    private static final float HUD_GAUSSIAN_STRENGTH_MULTIPLIER = 2.5F;
    private static final int MAX_PREPARED_HUD_KAWASE_REGIONS = 32;
    private static final float HUD_FINE_KAWASE_THRESHOLD = 8.0F;

    private static final long MIN_HUD_BLUR_REFRESH_INTERVAL_NS = 1_000_000_000L / 360L;
    private static final long MAX_HUD_BLUR_REFRESH_INTERVAL_NS = 1_000_000_000L / 10L;
    private static final long MENU_BLUR_REFRESH_INTERVAL_NS = 16_666_667L;

    private static final long NAMETAG_BLUR_REFRESH_INTERVAL_NS = 16_666_667L;
    private static final long NAMETAG_BLUR_MIN_CAPTURE_INTERVAL_NS = 16_666_667L;
    private static final int MENU_BLUR_CACHE_SLOTS = 4;
    private static final int MAX_HUD_BLUR_STATES = 32;
    private static final int MAX_PREPARED_HUD_GAUSSIAN_REGIONS = 32;

    private static final RenderPipeline COPY_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/blur_copy"))
            .withVertexShader(Identifier.of("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.of("minecraft", "core/blit_screen"))
            .withSampler("InSampler")

            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, true)

            .withCull(false)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private static final RenderPipeline DUAL_KAWASE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/blur_dual_kawase"))
            .withVertexShader(Identifier.of("phaze", "core/blur_dual_kawase"))
            .withFragmentShader(Identifier.of("phaze", "core/blur_dual_kawase"))
            .withSampler("Sampler0")
            .withUniform("DualKawaseConfig", UniformType.UNIFORM_BUFFER)
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, true)
            .withCull(false)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private static final RenderPipeline GAUSSIAN_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/blur_gaussian"))
            .withVertexShader(Identifier.of("phaze", "core/blur_gaussian"))
            .withFragmentShader(Identifier.of("phaze", "core/blur_gaussian"))
            .withSampler("Sampler0")
            .withUniform("GaussianConfig", UniformType.UNIFORM_BUFFER)
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, true)
            .withCull(false)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .build();

    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/blur_composite"))
            .withVertexShader(Identifier.of("phaze", "core/blur"))
            .withFragmentShader(Identifier.of("phaze", "core/blur"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("BlurCompositeConfig", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build();

    private static final RenderPipeline WORLD_COMPOSITE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/world_blur_composite"))
            .withVertexShader(Identifier.of("phaze", "core/blur"))
            .withFragmentShader(Identifier.of("phaze", "core/blur"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("BlurCompositeConfig", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build();

    private static final int DUAL_KAWASE_UBO_SIZE = 16;
    private static final int GAUSSIAN_UBO_SIZE = 32;
    private static final int COMPOSITE_UBO_SIZE = 64;

    private GpuBuffer compositeUbo;

    private GpuBuffer dualKawaseUbo;
    private GpuBuffer gaussianUbo;

    private final DrawEngineImpl drawEngine = new DrawEngineImpl();
    private Framebuffer input;
    private Framebuffer menuInput;
    private Framebuffer hudHalfInput;
    private Framebuffer nametagInput;
    private Framebuffer ping;
    private Framebuffer pong;
    private Framebuffer halfA;
    private Framebuffer halfB;
    private Framebuffer quarterA;
    private Framebuffer quarterB;
    private final MenuBlurSlot[] menuBlurSlots = new MenuBlurSlot[MENU_BLUR_CACHE_SLOTS];
    private final MenuBlurSlot[] hudBlurSlots = new MenuBlurSlot[MENU_BLUR_CACHE_SLOTS];
    private boolean cachedFramePrepared = false;
    private boolean hudBatchMode = false;
    private boolean hudBatchStateApplied = false;
    private ShaderProgram hudBatchMaskShader = null;
    private boolean forceHudRefresh = true;
    private double lastPlayerX = Double.NaN;
    private double lastPlayerY = Double.NaN;
    private double lastPlayerZ = Double.NaN;
    private long lastSpeedCheckTime = 0L;
    private float cachedPlayerSpeed = 0.0f;
    private boolean worldSpaceSpeedPrepared = false;
    private final long[] hudStateKeys = new long[MAX_HUD_BLUR_STATES];
    private final boolean[] hudStateInitialized = new boolean[MAX_HUD_BLUR_STATES];
    private int lastWorldCaptureWidth = -1;
    private int lastWorldCaptureHeight = -1;
    private boolean worldSpaceFramePrepared = false;
    private boolean nametagBlurWasActive = false;
    private long lastNametagBlurRefreshNs = 0L;
    private float lastDualKawaseRadius = -1.0f;
    private int lastDualKawaseWidth = -1;
    private int lastDualKawaseHeight = -1;
    private boolean dualKawasePrepared = false;
    private boolean menuBlurCurrentValid = false;
    private boolean menuBlurPreviousValid = false;
    private long menuBlurRegionKey = Long.MIN_VALUE;
    private long menuBlurLastRefreshNs = 0L;
    private long menuBlurTransitionStartNs = 0L;
    private float menuBlurRadius = -1.0F;
    private BlurRegion menuBlurRegion = null;
    private long menuInputLastCaptureNs = 0L;

    private long menuInputRevision = 0L;

    private Framebuffer menuOverlayInput;
    private long menuOverlayRevision = 0L;
    private boolean menuOverlayValid = false;
    private boolean hudInputValid = false;
    private long hudInputRevision = 0L;
    private long lastHudInputRefreshNs = 0L;
    private long lastHudBackgroundStateKey = Long.MIN_VALUE;
    private boolean stableHudCapturePoint = false;
    private Object lastObservedScreen = null;
    private final long[] preparedHudGaussianRegionKeys = new long[MAX_PREPARED_HUD_GAUSSIAN_REGIONS];
    private int preparedHudGaussianRegionCount = 0;
    private final Vector3f scratchScale = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();
    private final Vector4f scratchRound = new Vector4f();
    private static final Vector4f ZERO_ROUND = new Vector4f();

    public void beginCachedFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        Object currentScreen = client == null ? null : client.currentScreen;
        if (currentScreen != lastObservedScreen) {
            lastObservedScreen = currentScreen;
            hudInputValid = false;
            lastHudBackgroundStateKey = Long.MIN_VALUE;
            forceHudRefresh = true;
            menuInputLastCaptureNs = 0L;
            menuOverlayValid = false;
            BatchedHudBuffer.INSTANCE.invalidate();
            invalidateHudKawaseCache();
            invalidateMenuBlurCache();
        }
        cachedFramePrepared = false;
        hudBatchMode = true;
        hudBatchStateApplied = false;
        hudBatchMaskShader = null;
        preparedHudGaussianRegionCount = 0;
    }

    public void captureBaseFrameForBlur() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }

        stableHudCapturePoint = true;
        try {
            prepareFramebuffers(client, true, false);
        } finally {
            stableHudCapturePoint = false;
        }
    }

    public void endCachedFrame() {
        if (hudBatchStateApplied) {
            restoreRenderState(true);
        }
        hudBatchMode = false;
        hudBatchStateApplied = false;
        hudBatchMaskShader = null;
    }

    public void registerHudBlurState(int slot, long stateKey) {
        if (slot < 0 || slot >= hudStateKeys.length) {
            forceHudRefresh = true;
            return;
        }
        if (!hudStateInitialized[slot] || hudStateKeys[slot] != stateKey) {
            hudStateInitialized[slot] = true;
            hudStateKeys[slot] = stateKey;
            forceHudRefresh = true;
        }
    }

    @Override
    public void render(ShapeProperties shape) {
        render(shape, false);
    }

    public void renderGaussian(ShapeProperties shape) {
        renderGaussian(shape, false);
    }

    public void renderGaussianOverlay(ShapeProperties shape) {
        renderGaussian(shape, true);
    }

    private void renderGaussian(ShapeProperties shape, boolean overlaySource) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepareFramebuffers(client, false, false)) {
            return;
        }
        Theme theme = Theme.getInstance();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        BlurRegion blurRegion = computeHudGaussianRegion(client, shape, blurRadius);
        long now = System.nanoTime();

        boolean useOverlay = overlaySource && menuOverlayValid && menuOverlayInput != null;
        if (!useOverlay && now - menuInputLastCaptureNs >= MENU_BLUR_REFRESH_INTERVAL_NS) {

            captureMenuInput(client, menuInput.textureWidth, menuInput.textureHeight);
            menuInputLastCaptureNs = now;
            menuInputRevision++;
        }

        Framebuffer source = useOverlay ? menuOverlayInput : menuInput;
        long sourceRevision = useOverlay ? menuOverlayRevision : menuInputRevision;

        long regionKey = blurRegion == null
                ? 0x6A09E667F3BCC909L
                : computeMenuBlurRegionKey(blurRegion);

        if (useOverlay) {
            regionKey ^= 0x9E3779B97F4A7C15L;
        }
        MenuBlurSlot slot = acquireMenuBlurSlot(regionKey, now);

        boolean refresh = !slot.valid
                || Math.abs(slot.blurRadius - blurRadius) >= 0.05F
                || slot.sourceRevision != sourceRevision;
        if (refresh) {
            if (applyDualKawaseBlur(client, source, blurRadius, blurRegion, slot.framebuffer)) {
                slot.valid = true;
                slot.blurRadius = blurRadius;
                slot.lastRefreshNs = now;
                slot.sourceRevision = sourceRevision;
            }
        }
        if (!slot.valid) {
            restoreRenderState(true);
            return;
        }
        float scale = (float) client.getWindow().getScaleFactor();
        float alpha = vorga.phazeclient.api.system.draw.PhazeAlpha.get();
        Matrix4f matrix4f = GuiMatrix.mat4(shape.getMatrix());
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(
                matrix4f,
                buffer,
                shape.getX() - softness / 2.0F,
                shape.getY() - softness / 2.0F,
                shape.getWidth() + softness,
                shape.getHeight() + softness,
                color
        );

        drawComposite(
                buffer.end(),
                matrix4f,
                slot.framebuffer != null ? slot.framebuffer.getColorAttachmentView() : null,
                null,
                width, height,
                round,
                softness,
                blurRadius,
                Theme.getInstance().getHudBlurMode(),
                noTint(),
                1.0F,
                false,
                true);
        restoreRenderState(true);
    }

    public void renderCached(ShapeProperties shape) {
        if (hudImmediateDrawsSuppressed) {
            return;
        }
        render(shape, true);
    }

    public void prepareWorldRectInput() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        if (!prepareFramebuffers(client, false, false)) {
            return;
        }
        prepareNametagInput(client);
    }

    public void renderWorldRect(
            Matrix4f matrix,
            float x,
            float y,
            float width,
            float height,
            float quality,
            int tintColor,
            boolean drawFallback,
            float opacity
    ) {
        float clampedOpacity = MathHelper.clamp(opacity, 0.0F, 1.0F);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            if (drawFallback) {
                drawWorldFallbackRect(matrix, x, y, width, height, tintColor);
            }
            return;
        }
        if (!prepareFramebuffers(client, false, false)) {
            if (drawFallback) {
                drawWorldFallbackRect(matrix, x, y, width, height, tintColor);
            }
            return;
        }

        prepareNametagInput(client);

        if (drawFallback) {
            drawWorldFallbackRectContents(matrix, x, y, width, height, tintColor);
        }

        if (clampedOpacity <= 0.001F) {
            restoreRenderState(true);
            return;
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int blurColor = (MathHelper.clamp(Math.round(clampedOpacity * 255.0F), 0, 255) << 24) | 0x00FFFFFF;
        drawEngine.quad(matrix, buffer, x, y, width, height, blurColor);

        Theme theme = Theme.getInstance();

        float compositeBlurRadius = MathHelper.clamp(quality * 4.0F, 0.0F, 16.0F);
        drawComposite(
                buffer.end(),
                matrix,
                nametagInput != null ? nametagInput.getColorAttachmentView() : null,
                null,
                width, height,
                scratchRound.set(0.0F, 0.0F, 0.0F, 0.0F),
                0.001F,
                compositeBlurRadius,
                theme.getHudBlurMode(),
                tintVector(tintColor, scratchTint),
                1.0F,
                true,
                false);

        restoreRenderState(true);
    }

    private void prepareNametagInput(MinecraftClient client) {
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());

        if (!worldSpaceFramePrepared
                || framebufferWidth != lastWorldCaptureWidth
                || framebufferHeight != lastWorldCaptureHeight) {
            long now = System.nanoTime();
            boolean refresh = !nametagBlurWasActive
                    || framebufferWidth != lastWorldCaptureWidth
                    || framebufferHeight != lastWorldCaptureHeight
                    || (now - lastNametagBlurRefreshNs >= NAMETAG_BLUR_REFRESH_INTERVAL_NS
                    && now - lastNametagBlurRefreshNs >= NAMETAG_BLUR_MIN_CAPTURE_INTERVAL_NS);
            nametagBlurWasActive = true;
            if (refresh) {
                captureNametagInput(client, framebufferWidth, framebufferHeight);
                lastNametagBlurRefreshNs = now;
                lastWorldCaptureWidth = framebufferWidth;
                lastWorldCaptureHeight = framebufferHeight;
            }
            worldSpaceFramePrepared = true;
        }
    }

    private void drawWorldFallbackRect(Matrix4f matrix, float x, float y, float width, float height, int tintColor) {
        drawWorldFallbackRectContents(matrix, x, y, width, height, tintColor);
        restoreRenderState(true);
    }

    private void drawWorldFallbackRectContents(Matrix4f matrix, float x, float y, float width, float height, int tintColor) {
        if (((tintColor >>> 24) & 0xFF) == 0 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        BufferBuilder fallback = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(matrix, fallback, x, y, width, height, tintColor);
        vorga.phazeclient.util.render.PhazeRenderLayers.getHitboxFill().draw(fallback.end());
    }

    public void beginWorldSpaceFrame(boolean enabled) {
        worldSpaceFramePrepared = false;
        worldSpaceSpeedPrepared = false;
        if (!enabled) {
            nametagBlurWasActive = false;
        }
    }

    public void renderCachedBatch(List<ShapeProperties> shapes) {
        if (hudImmediateDrawsSuppressed || shapes == null || shapes.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepareFramebuffers(client, true, true)) {
            return;
        }

        boolean useHudBatch = hudBatchMode;
        if (useHudBatch) {
            if (!hudBatchStateApplied) {
                hudBatchStateApplied = true;
            }
        } else {
        }

        PreparedBlurState batchState = resolvePreparedBatchBlurState(client, shapes);
        ShaderProgram shader = null;
        PreparedBlurState activeState = null;
        for (ShapeProperties shape : shapes) {
            if (shape == null) {
                continue;
            }
            if (computeHudGaussianRegion(client, shape, 0.0f) == null) {
                continue;
            }
            PreparedBlurState preparedState = batchState != null
                    ? batchState
                    : resolvePreparedBlurState(client, shape, true);
            if (preparedState == null) {
                continue;
            }
            if (!preparedState.matches(activeState)) {

                activeState = preparedState;
            }
            renderPreparedShapeWithBoundShader(shape, shader, preparedState);
        }

        if (!useHudBatch) {
            restoreRenderState(true);
        }
    }

    private void renderPreparedShapeWithBoundShader(ShapeProperties shape, ShaderProgram shader, PreparedBlurState preparedState) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || input == null || preparedState == null) {
            return;
        }

        float scale = (float) client.getWindow().getScaleFactor();
        float alpha = vorga.phazeclient.api.system.draw.PhazeAlpha.get();
        Matrix4f matrix4f = GuiMatrix.mat4(shape.getMatrix());
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(
                matrix4f,
                buffer,
                shape.getX() - softness / 2.0F,
                shape.getY() - softness / 2.0F,
                shape.getWidth() + softness,
                shape.getHeight() + softness,
                color
        );

        drawComposite(
                buffer.end(),
                matrix4f,
                preparedState.sourceTexture(),
                null,
                width, height,
                round,
                softness,
                preparedState.blurRadius(),
                preparedState.blurMode(),
                noTint(),
                1.0F,
                false,
                true);
    }

    private void render(ShapeProperties shape, boolean cacheFrame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }

        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();

        if (!prepareFramebuffers(client, cacheFrame, true)) {
            return;
        }

        Theme theme = Theme.getInstance();
        int blurMode = theme.getHudBlurMode();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();

        boolean useHudBatch = cacheFrame && hudBatchMode;
        if (useHudBatch) {
            if (!hudBatchStateApplied) {
                hudBatchStateApplied = true;
            }
        } else {
        }

        if (!renderPreparedShape(shape, cacheFrame)) {
            if (!useHudBatch) {
                restoreRenderState(true);
            }
            return;
        }

        if (!useHudBatch) {
            restoreRenderState(true);
        }
    }

    private boolean renderPreparedShape(ShapeProperties shape, boolean useHudCache) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || input == null) {
            return false;
        }

        PreparedBlurState preparedState = resolvePreparedBlurState(client, shape, useHudCache);
        if (preparedState == null) {
            return false;
        }

        float scale = (float) client.getWindow().getScaleFactor();
        float alpha = vorga.phazeclient.api.system.draw.PhazeAlpha.get();
        Matrix4f matrix4f = GuiMatrix.mat4(shape.getMatrix());
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(
                matrix4f,
                buffer,
                shape.getX() - softness / 2.0F,
                shape.getY() - softness / 2.0F,
                shape.getWidth() + softness,
                shape.getHeight() + softness,
                color
        );

        drawComposite(
                buffer.end(),
                matrix4f,
                preparedState.sourceTexture(),
                null,
                width, height,
                round,
                softness,
                preparedState.blurRadius(),
                preparedState.blurMode(),
                noTint(),
                1.0F,
                false,
                true);
        return true;
    }

    private PreparedBlurState resolvePreparedBlurState(MinecraftClient client, ShapeProperties shape, boolean useHudCache) {
        if (client == null || shape == null || input == null || hudHalfInput == null) {
            return null;
        }

        Theme theme = Theme.getInstance();
        int blurMode = theme.getHudBlurMode();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        float hudGaussianRadius = blurRadius * HUD_GAUSSIAN_STRENGTH_MULTIPLIER;
        int effectiveBlurMode = blurMode;
        float effectiveBlurRadius = blurRadius;

        GpuTextureView sourceTexture = input.getColorAttachmentView();

        BlurRegion visibleRegion = computeHudGaussianRegion(client, shape, 0.0f);
        if (visibleRegion == null) {
            return null;
        }

        boolean optimizerEnabled;
        try {
            vorga.phazeclient.implement.features.modules.other.HudOptimizer optimizer =
                    vorga.phazeclient.implement.features.modules.other.HudOptimizer.getInstance();
            optimizerEnabled = optimizer != null && optimizer.isEnabled();
        } catch (Throwable ignored) {
            optimizerEnabled = false;
        }
        if (useHudCache && optimizerEnabled && blurMode == 2 && blurRadius > 0.0f && cacheFrameReadyForHud()) {
            BlurRegion blurRegion = computeHudGaussianRegion(client, shape, hudGaussianRadius);
            Framebuffer prepared = hudGaussianRadius <= HUD_FINE_KAWASE_THRESHOLD
                    ? applyOptimizedHudFineKawaseBlur(client, hudGaussianRadius, blurRegion)
                    : applyOptimizedHudKawaseBlur(client, hudGaussianRadius, blurRegion);
            if (prepared != null) {
                sourceTexture = prepared.getColorAttachmentView();
                effectiveBlurMode = 0;
                effectiveBlurRadius = 0.0f;
            } else {
                effectiveBlurMode = 2;
                effectiveBlurRadius = blurRadius;
            }
        }

        return new PreparedBlurState(sourceTexture, effectiveBlurMode, effectiveBlurRadius);
    }

    private PreparedBlurState resolvePreparedBatchBlurState(MinecraftClient client, List<ShapeProperties> shapes) {
        if (client == null || shapes == null || shapes.isEmpty() || !cacheFrameReadyForHud()) {
            return null;
        }
        try {
            vorga.phazeclient.implement.features.modules.other.HudOptimizer optimizer =
                    vorga.phazeclient.implement.features.modules.other.HudOptimizer.getInstance();
            if (optimizer == null || !optimizer.isEnabled()) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
        Theme theme = Theme.getInstance();
        if (theme == null || theme.getHudBlurMode() != 2) {
            return null;
        }

        float sharedRadius = -1.0f;
        BlurRegion union = null;
        for (ShapeProperties shape : shapes) {
            if (shape == null) continue;
            float radius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
            if (radius <= 0.0f) continue;
            if (sharedRadius < 0.0f) {
                sharedRadius = radius;
            } else if (Math.abs(sharedRadius - radius) >= 0.01f) {
                return null;
            }
            BlurRegion region = computeHudGaussianRegion(client, shape, radius * HUD_GAUSSIAN_STRENGTH_MULTIPLIER);
            if (region != null) {
                union = union == null ? region : unionBlurRegions(union, region);
            }
        }
        if (sharedRadius <= 0.0f || union == null) {
            return null;
        }

        float hudRadius = sharedRadius * HUD_GAUSSIAN_STRENGTH_MULTIPLIER;
        Framebuffer prepared = hudRadius <= HUD_FINE_KAWASE_THRESHOLD
                ? applyOptimizedHudFineKawaseBlur(client, hudRadius, union)
                : applyOptimizedHudKawaseBlur(client, hudRadius, union);
        if (prepared == null) {
            return null;
        }
        return new PreparedBlurState(prepared.getColorAttachmentView(), 0, 0.0f);
    }

    private boolean cacheFrameReadyForHud() {
        return hudInputValid && hudHalfInput != null && pong != null;
    }

    private boolean prepareFramebuffers(MinecraftClient client, boolean cacheFrame, boolean refreshNonCachedInput) {
        Framebuffer framebuffer = client.getFramebuffer();
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        boolean resized = false;

        if (input == null) {

            input = new SimpleFramebuffer("phaze/blur/input", framebufferWidth, framebufferHeight, false);
            menuInput = new SimpleFramebuffer("phaze/blur/menu_input", framebufferWidth, framebufferHeight, false);
            hudHalfInput = new SimpleFramebuffer("phaze/blur/hud_half_input", Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2), false);
            nametagInput = new SimpleFramebuffer("phaze/blur/nametag_input", framebufferWidth, framebufferHeight, false);
            ping = new SimpleFramebuffer("phaze/blur/ping", framebufferWidth, framebufferHeight, false);
            pong = new SimpleFramebuffer("phaze/blur/pong", framebufferWidth, framebufferHeight, false);
            halfA = new SimpleFramebuffer("phaze/blur/half_a", Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2), false);
            halfB = new SimpleFramebuffer("phaze/blur/half_b", Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2), false);
            quarterA = new SimpleFramebuffer("phaze/blur/quarter_a", Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4), false);
            quarterB = new SimpleFramebuffer("phaze/blur/quarter_b", Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4), false);

            resized = true;
        } else if (input.textureWidth != framebufferWidth || input.textureHeight != framebufferHeight) {
            input.resize(framebufferWidth, framebufferHeight);
            menuInput.resize(framebufferWidth, framebufferHeight);
            hudHalfInput.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
            nametagInput.resize(framebufferWidth, framebufferHeight);
            ping.resize(framebufferWidth, framebufferHeight);
            pong.resize(framebufferWidth, framebufferHeight);
            halfA.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
            halfB.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
            quarterA.resize(Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4));
            quarterB.resize(Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4));

            for (MenuBlurSlot slot : menuBlurSlots) {
                if (slot == null) {
                    continue;
                }
                slot.framebuffer.resize(framebufferWidth, framebufferHeight);
                slot.valid = false;
            }
            for (MenuBlurSlot slot : hudBlurSlots) {
                if (slot == null) {
                    continue;
                }
                slot.framebuffer.resize(framebufferWidth, framebufferHeight);
                slot.valid = false;
                slot.hudInputRevision = -1L;
                slot.hudRegionCount = 0;
            }
            menuBlurCurrentValid = false;
            menuBlurPreviousValid = false;
            menuBlurRegion = null;

            menuOverlayValid = false;
            hudInputValid = false;
            invalidateHudKawaseCache();
            resized = true;
        }

        if (input == null || menuInput == null || hudHalfInput == null || nametagInput == null || ping == null || pong == null) {
            return false;
        }

        if (halfA == null || halfB == null || quarterA == null || quarterB == null) {
            return false;
        }

        if (cacheFrame) {
            if (cachedFramePrepared && !resized && !forceHudRefresh) {
                return true;
            }
            long now = System.nanoTime();
            long backgroundStateKey = computeHudBackgroundStateKey(client);

            boolean optimizerEnabled = false;
            int blurRefreshFps = 60;
            try {
                vorga.phazeclient.implement.features.modules.other.HudOptimizer optimizer =
                        vorga.phazeclient.implement.features.modules.other.HudOptimizer.getInstance();
                if (optimizer != null) {
                    optimizerEnabled = optimizer.isEnabled();
                    blurRefreshFps = Math.max(1, optimizer.blurRefreshRate.getInt());
                }
            } catch (Throwable ignored) {
            }

            long refreshIntervalNs = optimizerEnabled
                    ? MathHelper.clamp(
                            1_000_000_000L / blurRefreshFps,
                            MIN_HUD_BLUR_REFRESH_INTERVAL_NS,
                            MAX_HUD_BLUR_REFRESH_INTERVAL_NS)
                    : 0L;
            boolean backgroundChanged = backgroundStateKey != lastHudBackgroundStateKey;
            boolean refreshDue = !optimizerEnabled || now - lastHudInputRefreshNs >= refreshIntervalNs;

            boolean guiOpen = client.currentScreen != null;
            boolean needsCapture = !hudInputValid
                    || resized
                    || ((!guiOpen || stableHudCapturePoint) && backgroundChanged && refreshDue);
            if (needsCapture) {
                captureHudInput(client, framebufferWidth, framebufferHeight);
                lastHudInputRefreshNs = now;
                lastHudBackgroundStateKey = backgroundStateKey;
            }
            if (forceHudRefresh) {
                invalidateHudKawaseCache();
                forceHudRefresh = false;
            }
            cachedFramePrepared = true;
            return true;
        }

        if (refreshNonCachedInput) {
            captureWorldInput(client, framebufferWidth, framebufferHeight);
        }
        return true;
    }

    private void captureWorldInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        captureFramebufferInput(client, input, framebufferWidth, framebufferHeight, FilterMode.NEAREST);
        dualKawasePrepared = false;
        preparedHudGaussianRegionCount = 0;
    }

    private void captureMenuInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        captureFramebufferInput(client, menuInput, framebufferWidth, framebufferHeight, FilterMode.NEAREST);
    }

    public void captureMenuOverlayFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }

        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepareFramebuffers(client, false, false)) {
            return;
        }

        int width = Math.max(1, client.getWindow().getFramebufferWidth());
        int height = Math.max(1, client.getWindow().getFramebufferHeight());

        if (menuOverlayInput == null) {
            menuOverlayInput = new SimpleFramebuffer("phaze/blur/menu_overlay_input", width, height, false);
        } else if (menuOverlayInput.textureWidth != width || menuOverlayInput.textureHeight != height) {
            menuOverlayInput.resize(width, height);
        }

        captureFramebufferInput(client, menuOverlayInput, width, height, FilterMode.NEAREST);
        menuOverlayRevision++;
        menuOverlayValid = true;
    }

    private void captureHudInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        captureFramebufferInput(client, input, framebufferWidth, framebufferHeight, FilterMode.NEAREST);
        captureFramebufferInput(client, hudHalfInput, framebufferWidth, framebufferHeight, FilterMode.LINEAR);
        hudInputValid = true;
        hudInputRevision++;
        dualKawasePrepared = false;
        preparedHudGaussianRegionCount = 0;
        invalidateHudKawaseCache();
    }

    private void captureNametagInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        captureFramebufferInput(client, nametagInput, framebufferWidth, framebufferHeight, FilterMode.NEAREST);
    }

    private void captureFramebufferInput(
            MinecraftClient client,
            Framebuffer target,
            int framebufferWidth,
            int framebufferHeight,
            FilterMode filter
    ) {

        Framebuffer framebuffer = BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer() != null
                ? BatchedHudBuffer.INSTANCE.getRealMainFramebuffer()
                : client.getFramebuffer();
        if (framebuffer == null) {
            framebuffer = client.getFramebuffer();
        }
        if (framebuffer == null || target == null) {
            return;
        }

        blitFramebuffer(framebuffer, target, filter, null);
    }

    private static void blitFramebuffer(Framebuffer source, Framebuffer target, FilterMode filter, BlurRegion targetScissor) {
        if (source == null || target == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        if (targetScissor == null
                && filter == FilterMode.NEAREST
                && source.textureWidth == target.textureWidth
                && source.textureHeight == target.textureHeight) {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    source.getColorAttachment(),
                    target.getColorAttachment(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    source.textureWidth,
                    source.textureHeight
            );
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "phaze/blur copy",
                target.getColorAttachmentView(),
                OptionalInt.empty())) {
            pass.setPipeline(COPY_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            if (targetScissor != null) {
                pass.enableScissor(targetScissor.x, targetScissor.y, targetScissor.width, targetScissor.height);
            }

            pass.bindTexture("InSampler", source.getColorAttachmentView(), RenderSystem.getSamplerCache().get(filter));
            pass.draw(0, 3);
        }
    }

    private boolean applyDualKawaseBlur(
            MinecraftClient client,
            Framebuffer sourceInput,
            float blurRadius,
            BlurRegion region,
            Framebuffer output
    ) {
        if (sourceInput == null || output == null || halfA == null || halfB == null || quarterA == null || quarterB == null) {
            return false;
        }
        int w = sourceInput.textureWidth;
        int h = sourceInput.textureHeight;

        float quantizedRadius = MathHelper.clamp(blurRadius, 0.0f, 24.0f);
        ShaderProgram shader = null;

        float normalized = MathHelper.clamp(quantizedRadius / 8.0f, 0.0f, 1.0f);
        float downOffset1 = quantizedRadius * 0.08f;
        float downOffset2 = quantizedRadius * 0.10f;
        runDualKawasePass(shader, sourceInput, halfA, downOffset1, true, region);
        runDualKawasePass(shader, halfA, quarterA, downOffset2, true, region);

        int passes = 2;
        Framebuffer src = quarterA;
        Framebuffer dst = quarterB;
        for (int i = 0; i < passes; i++) {
            float offset = quantizedRadius * (0.18f + i * (0.02f + normalized * 0.015f));
            runDualKawasePass(shader, src, dst, offset, true, region);
            Framebuffer tmp = src;
            src = dst;
            dst = tmp;
        }

        float upOffset1 = quantizedRadius * 0.09f;
        runDualKawasePass(shader, src, halfB, upOffset1, false, region);
        blitColorRegion(halfB, output, region, FilterMode.LINEAR);

        bindMainDrawTarget(client);
        dualKawasePrepared = true;
        lastDualKawaseRadius = quantizedRadius;
        lastDualKawaseWidth = w;
        lastDualKawaseHeight = h;
        return true;
    }

    private void blitColorRegion(Framebuffer source, Framebuffer target, BlurRegion region, FilterMode filter) {

        BlurRegion targetRegion = scaleBlurRegion(region, target.textureWidth, target.textureHeight);
        blitFramebuffer(source, target, filter, targetRegion);
    }

    private BlurRegion scaleBlurRegion(BlurRegion region, int targetWidth, int targetHeight) {
        if (region == null || input == null) {
            return new BlurRegion(0, 0, targetWidth, targetHeight);
        }

        float scaleX = targetWidth / (float) input.textureWidth;
        float scaleY = targetHeight / (float) input.textureHeight;
        int left = MathHelper.clamp(MathHelper.floor(region.x * scaleX) - 2, 0, targetWidth);
        int bottom = MathHelper.clamp(MathHelper.floor(region.y * scaleY) - 2, 0, targetHeight);
        int right = MathHelper.clamp(MathHelper.ceil((region.x + region.width) * scaleX) + 2, 0, targetWidth);
        int top = MathHelper.clamp(MathHelper.ceil((region.y + region.height) * scaleY) + 2, 0, targetHeight);
        return new BlurRegion(left, bottom, Math.max(1, right - left), Math.max(1, top - bottom));
    }

    private Framebuffer applyOptimizedHudKawaseBlur(MinecraftClient client, float blurRadius, BlurRegion region) {
        if (hudHalfInput == null || quarterA == null || quarterB == null || halfB == null || region == null) {
            return null;
        }

        int w = client.getWindow().getFramebufferWidth();
        int h = client.getWindow().getFramebufferHeight();
        float radius = MathHelper.clamp(blurRadius, 0.0f, 24.0f);
        MenuBlurSlot slot = acquireHudBlurSlot(2, radius, System.nanoTime());
        if (isHudBlurRegionPrepared(slot, region)) {
            return slot.framebuffer;
        }

        ShaderProgram shader = null;

        float normalized = MathHelper.clamp(radius / 24.0f, 0.0f, 1.0f);
        runDualKawasePass(shader, hudHalfInput, quarterA, radius * 0.14f, true, region);

        int quarterPasses = radius > 16.0f ? 2 : radius > 8.0f ? 1 : 0;
        Framebuffer src = quarterA;
        Framebuffer dst = quarterB;
        for (int i = 0; i < quarterPasses; i++) {
            float offset = radius * (0.18f + i * (0.025f + normalized * 0.015f));
            runDualKawasePass(shader, src, dst, offset, true, region);
            Framebuffer swap = src;
            src = dst;
            dst = swap;
        }

        runDualKawasePass(shader, src, halfB, radius * 0.10f, false, region);
        blitColorRegion(halfB, slot.framebuffer, region, FilterMode.LINEAR);
        bindMainDrawTarget(client);

        slot.valid = false;
        rememberHudBlurRegion(slot, region);
        return slot.framebuffer;
    }

    private boolean isHudBlurRegionPrepared(MenuBlurSlot slot, BlurRegion region) {
        for (int i = 0; i < slot.hudRegionCount; i++) {
            BlurRegion prepared = slot.hudRegions[i];
            if (prepared != null
                    && region.x >= prepared.x
                    && region.y >= prepared.y
                    && region.x + region.width <= prepared.x + prepared.width
                    && region.y + region.height <= prepared.y + prepared.height) {
                return true;
            }
        }
        return false;
    }

    private void rememberHudBlurRegion(MenuBlurSlot slot, BlurRegion region) {
        if (slot.hudRegionCount >= slot.hudRegions.length) {
            slot.hudRegionCount = 0;
        }
        slot.hudRegions[slot.hudRegionCount++] = region;
    }

    public void invalidateHudKawaseCachePublic() {
        invalidateHudKawaseCache();
        forceHudRefresh = true;
    }

    private void invalidateHudKawaseCache() {
        for (MenuBlurSlot slot : hudBlurSlots) {
            if (slot != null) {
                slot.hudInputRevision = -1L;
                slot.hudRegionCount = 0;
            }
        }
    }

    private void invalidateMenuBlurCache() {
        for (MenuBlurSlot slot : menuBlurSlots) {
            if (slot != null) {
                slot.valid = false;
                slot.regionKey = Long.MIN_VALUE;
            }
        }
    }

    private void ensureBlurSlots(MenuBlurSlot[] slots) {
        if (slots[0] != null) {
            return;
        }

        int width;
        int height;
        if (input != null) {
            width = Math.max(1, input.textureWidth);
            height = Math.max(1, input.textureHeight);
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            width = Math.max(1, client.getWindow().getFramebufferWidth());
            height = Math.max(1, client.getWindow().getFramebufferHeight());
        }

        for (int i = 0; i < slots.length; i++) {
            SimpleFramebuffer framebufferCache = new SimpleFramebuffer("phaze/blur/slot_" + i, width, height, false);
            slots[i] = new MenuBlurSlot(framebufferCache);
        }
    }

    private MenuBlurSlot acquireHudBlurSlot(int mode, float radius, long now) {
        ensureBlurSlots(hudBlurSlots);
        MenuBlurSlot oldest = hudBlurSlots[0];
        for (MenuBlurSlot slot : hudBlurSlots) {
            if (slot.hudInputRevision == hudInputRevision
                    && slot.hudMode == mode
                    && Math.abs(slot.hudRadius - radius) < 0.05f) {
                slot.hudLastUseNs = now;
                return slot;
            }
            if (slot.hudLastUseNs < oldest.hudLastUseNs) {
                oldest = slot;
            }
        }

        oldest.hudInputRevision = hudInputRevision;
        oldest.hudMode = mode;
        oldest.hudRadius = radius;
        oldest.hudLastUseNs = now;
        oldest.hudRegionCount = 0;
        oldest.valid = false;
        return oldest;
    }

    private static BlurRegion unionBlurRegions(BlurRegion a, BlurRegion b) {
        int left = Math.min(a.x, b.x);
        int bottom = Math.min(a.y, b.y);
        int right = Math.max(a.x + a.width, b.x + b.width);
        int top = Math.max(a.y + a.height, b.y + b.height);
        return new BlurRegion(left, bottom, right - left, top - bottom);
    }

    private long computeHudBackgroundStateKey(MinecraftClient client) {
        long key = client.world == null ? 0L : client.world.getTime();
        if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            var camera = client.gameRenderer.getCamera();
            var pos = camera.getCameraPos();
            var rotation = camera.getRotation();
            key = mixStateKey(key, Double.doubleToRawLongBits(pos.x));
            key = mixStateKey(key, Double.doubleToRawLongBits(pos.y));
            key = mixStateKey(key, Double.doubleToRawLongBits(pos.z));
            key = mixStateKey(key, Float.floatToRawIntBits(rotation.x));
            key = mixStateKey(key, Float.floatToRawIntBits(rotation.y));
            key = mixStateKey(key, Float.floatToRawIntBits(rotation.z));
            key = mixStateKey(key, Float.floatToRawIntBits(rotation.w));
        }
        key = mixStateKey(key, client.currentScreen == null ? 0L : client.currentScreen.getClass().hashCode());
        return key;
    }

    private static long mixStateKey(long current, long value) {
        return (current ^ value) * 0x9E3779B97F4A7C15L;
    }

    private Framebuffer applyOptimizedHudFineKawaseBlur(MinecraftClient client, float blurRadius, BlurRegion region) {
        if (input == null || region == null) {
            return null;
        }

        float radius = MathHelper.clamp(blurRadius, 0.0f, HUD_FINE_KAWASE_THRESHOLD);
        MenuBlurSlot slot = acquireHudBlurSlot(1, radius, System.nanoTime());
        if (isHudBlurRegionPrepared(slot, region)) {
            return slot.framebuffer;
        }

        ShaderProgram shader = null;

        runDualKawasePass(shader, input, slot.framebuffer, radius * 0.35f, true, region);

        bindMainDrawTarget(client);
        slot.valid = false;
        rememberHudBlurRegion(slot, region);
        return slot.framebuffer;
    }

    private void runDualKawasePass(
            ShaderProgram shader,
            Framebuffer source,
            Framebuffer target,
            float offset,
            boolean downsample,
            BlurRegion region
    ) {
        if (source == null || target == null) {
            return;
        }

        float texelX = 1.0F / Math.max(1, source.textureWidth);
        float texelY = 1.0F / Math.max(1, source.textureHeight);

        dualKawaseUbo = ensureUbo(dualKawaseUbo, "phaze/blur dual kawase", DUAL_KAWASE_UBO_SIZE);
        if (dualKawaseUbo == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, DUAL_KAWASE_UBO_SIZE)
                    .putVec2(texelX, texelY)
                    .putFloat(offset)
                    .putInt(downsample ? 1 : 0)
                    .get();
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(dualKawaseUbo.slice(), data);
        }

        runFullscreenPass(DUAL_KAWASE_PIPELINE, "phaze/blur dual kawase",
                source, target, dualKawaseUbo, "DualKawaseConfig", region);
    }

    private void runFullscreenPass(
            RenderPipeline pipeline,
            String label,
            Framebuffer source,
            Framebuffer target,
            GpuBuffer ubo,
            String uniformName,
            BlurRegion region
    ) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label,
                target.getColorAttachmentView(),
                OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(uniformName, ubo.slice());
            pass.bindTexture("Sampler0", source.getColorAttachmentView(),
                    RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            if (region != null) {

                BlurRegion scaled = scaleBlurRegion(region, target.textureWidth, target.textureHeight);
                pass.enableScissor(scaled.x(), scaled.y(), scaled.width(), scaled.height());
            }
            pass.draw(0, 3);
        }
    }

    private void drawComposite(
            BuiltBuffer built,
            Matrix4f modelView,
            GpuTextureView blurred,
            GpuTextureView previous,
            float sizeX, float sizeY,
            Vector4f radius,
            float smoothness,
            float blurRadius,
            int blurMode,
            Vector4f tintColor,
            float frameMix,
            boolean rectMask,
            boolean guiSpace
    ) {
        if (built == null) {
            return;
        }
        if (blurred == null) {
            built.close();
            return;
        }

        float effectiveMix = previous == null ? 1.0F : MathHelper.clamp(frameMix, 0.0F, 1.0F);

        compositeUbo = ensureUbo(compositeUbo, "phaze/blur composite", COMPOSITE_UBO_SIZE);
        if (compositeUbo == null) {
            built.close();
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, COMPOSITE_UBO_SIZE)
                    .putVec4(radius.x, radius.y, radius.z, radius.w)
                    .putVec4(tintColor.x, tintColor.y, tintColor.z, tintColor.w)
                    .putVec2(sizeX, sizeY)
                    .putFloat(smoothness)
                    .putFloat(blurRadius)
                    .putFloat(effectiveMix)
                    .putInt(rectMask ? 1 : 0)
                    .putInt(blurMode)
                    .get();
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(compositeUbo.slice(), data);
        }

        GpuTextureView previousView = previous != null ? previous : blurred;

        if (guiSpace) {
            vorga.phazeclient.api.system.draw.GuiProjection.begin();
        }
        try {
            Matrix4f pose = guiSpace
                    ? vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(scratchCompositePose)
                    : scratchCompositePose.set(RenderSystem.getModelViewMatrix());
            vorga.phazeclient.api.system.draw.GpuDraw.drawWithUniforms(
                    guiSpace ? COMPOSITE_PIPELINE : WORLD_COMPOSITE_PIPELINE,
                    built,
                    "Sampler0", blurred,
                    "Sampler1", previousView,
                    FilterMode.LINEAR,
                    pose,
                    new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                    "BlurCompositeConfig", compositeUbo);
        } finally {
            if (guiSpace) {
                vorga.phazeclient.api.system.draw.GuiProjection.end();
            }
            built.close();
        }
    }

    private final Matrix4f scratchCompositePose = new Matrix4f();

    private static GpuBuffer ensureUbo(GpuBuffer existing, String label, int size) {
        if (existing != null && !existing.isClosed()) {
            return existing;
        }

        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size);
    }

    public float getPlayerSpeed(MinecraftClient client) {
        if (client == null || client.player == null) {
            return 0.0f;
        }
        if (worldSpaceSpeedPrepared) {
            return cachedPlayerSpeed;
        }
        worldSpaceSpeedPrepared = true;

        var playerPos = client.player.getEntityPos();
        long currentTime = System.currentTimeMillis();

        if (Double.isNaN(lastPlayerX)) {
            lastPlayerX = playerPos.x;
            lastPlayerY = playerPos.y;
            lastPlayerZ = playerPos.z;
            lastSpeedCheckTime = currentTime;
            return 0.0f;
        }

        double deltaTime = (currentTime - lastSpeedCheckTime) / 1000.0;
        if (deltaTime < 0.05) {
            return cachedPlayerSpeed;
        }

        double dx = playerPos.x - lastPlayerX;
        double dy = playerPos.y - lastPlayerY;
        double dz = playerPos.z - lastPlayerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        cachedPlayerSpeed = (float) (distance / deltaTime);

        lastPlayerX = playerPos.x;
        lastPlayerY = playerPos.y;
        lastPlayerZ = playerPos.z;
        lastSpeedCheckTime = currentTime;

        return cachedPlayerSpeed;
    }

    private boolean applyGaussianBlur(MinecraftClient client, float blurRadius) {
        if (blurRadius <= 0.0F) {
            blitFramebuffer(input, pong, FilterMode.LINEAR, null);
            bindMainDrawTarget(client);
            return true;
        }

        ShaderProgram shader = null;

        runGaussianPass(shader, input, ping, 1.0F, 0.0F, blurRadius, null);
        runGaussianPass(shader, ping, pong, 0.0F, 1.0F, blurRadius, null);
        bindMainDrawTarget(client);
        return true;
    }

    private void runGaussianPass(ShaderProgram shader, Framebuffer source, Framebuffer target, float directionX, float directionY, float blurRadius, BlurRegion region) {
        if (source == null || target == null) {
            return;
        }
        float texelX = 1.0F / Math.max(1, source.textureWidth);
        float texelY = 1.0F / Math.max(1, source.textureHeight);

        float sigma = Math.max(0.1F, blurRadius * 0.5F);
        int support = Math.min(24, Math.max(1, Math.round(sigma * 3.0F)));

        gaussianUbo = ensureUbo(gaussianUbo, "phaze/blur gaussian", GAUSSIAN_UBO_SIZE);
        if (gaussianUbo == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, GAUSSIAN_UBO_SIZE)
                    .putVec2(directionX, directionY)
                    .putVec2(texelX, texelY)
                    .putFloat(sigma)
                    .putFloat(1.0F)
                    .putInt(support)
                    .get();
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(gaussianUbo.slice(), data);
        }

        runFullscreenPass(GAUSSIAN_PIPELINE, "phaze/blur gaussian",
                source, target, gaussianUbo, "GaussianConfig", region);
    }

    private BlurRegion computeHudGaussianRegion(MinecraftClient client, ShapeProperties shape, float blurRadius) {
        if (client == null || client.getWindow() == null || shape == null || shape.getMatrix() == null) {
            return null;
        }

        float scale = (float) client.getWindow().getScaleFactor();
        Matrix4f matrix4f = GuiMatrix.mat4(shape.getMatrix());
        float softness = Math.max(0.001F, shape.getSoftness());
        Vector3f pos = matrix4f.transformPosition(
                shape.getX() - softness / 2.0F,
                shape.getY() - softness / 2.0F,
                0.0F,
                scratchPosition
        ).mul(scale);
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        float width = (shape.getWidth() + softness) * size.x;
        float height = (shape.getHeight() + softness) * size.y;
        int margin = Math.max(2, MathHelper.ceil(blurRadius) + 2);

        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        int left = MathHelper.clamp(MathHelper.floor(pos.x) - margin, 0, framebufferWidth);
        int top = MathHelper.clamp(MathHelper.floor(pos.y) - margin, 0, framebufferHeight);
        int right = MathHelper.clamp(MathHelper.ceil(pos.x + width) + margin, 0, framebufferWidth);
        int bottom = MathHelper.clamp(MathHelper.ceil(pos.y + height) + margin, 0, framebufferHeight);

        if (right <= left || bottom <= top) {
            return null;
        }

        return new BlurRegion(left, framebufferHeight - bottom, right - left, bottom - top);
    }

    private boolean hasPreparedHudGaussianRegion(long key) {
        for (int i = 0; i < preparedHudGaussianRegionCount; i++) {
            if (preparedHudGaussianRegionKeys[i] == key) {
                return true;
            }
        }
        return false;
    }

    private void rememberPreparedHudGaussianRegion(long key) {
        if (preparedHudGaussianRegionCount >= preparedHudGaussianRegionKeys.length) {
            return;
        }
        preparedHudGaussianRegionKeys[preparedHudGaussianRegionCount++] = key;
    }

    private long computeHudGaussianRegionKey(BlurRegion region, float blurRadius) {
        long key = Float.floatToRawIntBits(blurRadius);
        key = key * 31L + region.x;
        key = key * 31L + region.y;
        key = key * 31L + region.width;
        key = key * 31L + region.height;
        return key;
    }

    private long computeMenuBlurRegionKey(BlurRegion region) {

        long key = region.x >> 1;
        key = key * 31L + (region.y >> 1);
        key = key * 31L + (region.width >> 1);
        key = key * 31L + (region.height >> 1);
        return key;
    }

    private MenuBlurSlot acquireMenuBlurSlot(long regionKey, long now) {
        ensureBlurSlots(menuBlurSlots);
        MenuBlurSlot oldest = menuBlurSlots[0];
        for (MenuBlurSlot slot : menuBlurSlots) {
            if (slot.regionKey == regionKey) {
                slot.lastUseNs = now;
                return slot;
            }
            if (!slot.valid || slot.lastUseNs < oldest.lastUseNs) {
                oldest = slot;
            }
        }

        oldest.regionKey = regionKey;
        oldest.lastUseNs = now;
        oldest.valid = false;
        return oldest;
    }
    private record BlurRegion(int x, int y, int width, int height) {
    }

    private static final class MenuBlurSlot {
        private final Framebuffer framebuffer;
        private final BlurRegion[] hudRegions = new BlurRegion[MAX_PREPARED_HUD_KAWASE_REGIONS];
        private long regionKey = Long.MIN_VALUE;

        private long sourceRevision = Long.MIN_VALUE;
        private long lastRefreshNs = 0L;
        private long lastUseNs = 0L;
        private float blurRadius = -1.0f;
        private boolean valid = false;
        private long hudInputRevision = -1L;
        private long hudLastUseNs = 0L;
        private int hudMode = -1;
        private float hudRadius = -1.0f;
        private int hudRegionCount = 0;

        private MenuBlurSlot(Framebuffer framebuffer) {
            this.framebuffer = framebuffer;
        }
    }

    private record PreparedBlurState(GpuTextureView sourceTexture, int blurMode, float blurRadius) {
        private boolean matches(PreparedBlurState other) {

            return other != null
                    && sourceTexture == other.sourceTexture
                    && blurMode == other.blurMode
                    && Float.floatToRawIntBits(blurRadius) == Float.floatToRawIntBits(other.blurRadius);
        }
    }

    private void bindMainDrawTarget(MinecraftClient client) {

    }

    private static Vector4f tintVector(int argb, Vector4f dest) {
        return dest.set(
                ((argb >>> 16) & 0xFF) / 255.0F,
                ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F);
    }

    private final Vector4f scratchTint = new Vector4f();

    private Vector4f noTint() {
        return scratchTint.set(0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static float maxCornerRadius(Vector4f radius) {
        return Math.max(Math.max(radius.x, radius.y), Math.max(radius.z, radius.w));
    }

    private static void restoreRenderState(boolean enableDepthTest) {

    }
}
