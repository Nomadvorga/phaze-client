package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import vorga.phazeclient.api.system.draw.DrawEngineImpl;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.render.shader.ShaderHelper;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.implement.features.modules.client.Theme;

import java.util.List;

public class Blur implements Shape {
    public static final Blur INSTANCE = new Blur();
    private static final float HUD_GAUSSIAN_STRENGTH_MULTIPLIER = 2.5F;
    private static final int HUD_FAST_BLUR_REGION_AREA_THRESHOLD = 110_000;
    private static final float HUD_FAST_BLUR_RADIUS_MULTIPLIER = 1.9F;
    private static final float HUD_FAST_BLUR_RADIUS_CAP = 16.0F;
    private static final ShaderProgramKey MASK_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("phaze", "core/blur"),
            VertexFormats.POSITION_COLOR,
            Defines.EMPTY
    );
    private static final ShaderProgramKey GAUSSIAN_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("phaze", "core/blur_gaussian"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );
    private static final ShaderProgramKey DUAL_KAWASE_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("phaze", "core/blur_dual_kawase"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );
    private static final int MAX_PREPARED_HUD_GAUSSIAN_REGIONS = 32;

    private final DrawEngineImpl drawEngine = new DrawEngineImpl();
    private Framebuffer input;
    private Framebuffer ping;
    private Framebuffer pong;
    private Framebuffer halfA;
    private Framebuffer halfB;
    private Framebuffer quarterA;
    private Framebuffer quarterB;
    private boolean cachedFramePrepared = false;
    private boolean hudBatchMode = false;
    private boolean hudBatchStateApplied = false;
    private ShaderProgram hudBatchMaskShader = null;
    private boolean forceHudRefresh = true;
    private double lastCameraX = Double.NaN;
    private double lastCameraY = Double.NaN;
    private double lastCameraZ = Double.NaN;
    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;
    private float lastZoomLevel = 1.0f;
    private boolean lastZoomActive = false;
    private int zoomOutAnimationFrames = 0;
    private boolean lastGuiActive = false;
    private double lastPlayerX = Double.NaN;
    private double lastPlayerY = Double.NaN;
    private double lastPlayerZ = Double.NaN;
    private long lastSpeedCheckTime = 0L;
    private final long[] hudStateKeys = new long[8];
    private final boolean[] hudStateInitialized = new boolean[8];
    private static final long WORLD_BLUR_CAPTURE_INTERVAL_NS = 40_000_000L; // ~25 Hz
    private long lastWorldCaptureNs = 0L;
    private int lastWorldCaptureWidth = -1;
    private int lastWorldCaptureHeight = -1;
    private boolean worldCaptureInitialized = false;
    private long worldSpaceFrameStamp = Long.MIN_VALUE;
    private float lastDualKawaseRadius = -1.0f;
    private int lastDualKawaseWidth = -1;
    private int lastDualKawaseHeight = -1;
    private boolean dualKawasePrepared = false;
    private final long[] preparedHudGaussianRegionKeys = new long[MAX_PREPARED_HUD_GAUSSIAN_REGIONS];
    private int preparedHudGaussianRegionCount = 0;

    public void beginCachedFrame() {
        cachedFramePrepared = false;
        hudBatchMode = true;
        hudBatchStateApplied = false;
        hudBatchMaskShader = null;
        dualKawasePrepared = false;
        preparedHudGaussianRegionCount = 0;
    }

    /**
     * Manually trigger the per-frame world-input snapshot used by every
     * cached HUD blur. Intended to be called from the HUD render pipeline
     * BEFORE {@code BatchedHudBuffer.blit()} runs, so that the snapshot
     * captures only world + vanilla HUD pixels and NOT the cached Phaze
     * HUDs that {@code blit} is about to stamp into the main framebuffer.
     *
     * <p>Why this matters: without an explicit pre-blit capture, the first
     * blur HUD in Pass 2 lazily kicks off {@link #captureWorldInput} on
     * demand. By that point the main framebuffer already contains the
     * blitted batched-FBO contents, so the snapshot ends up baking in
     * every non-blur HUD that just got blitted. Any blur HUD whose rect
     * overlaps a cached HUD's position then renders a backdrop that
     * shows a blurred copy of that cached HUD behind itself - and the
     * cache content is one refresh cycle stale, so the user sees the
     * previous HUD value visibly "imprinted" behind the current one
     * even when nothing actually moved.
     *
     * <p>Calling this method up-front sets {@code cachedFramePrepared = true},
     * so the in-Pass-2 lazy capture inside {@link #prepareFramebuffers}
     * short-circuits and every blur HUD reuses the clean pre-blit
     * snapshot.
     */
    public void captureBaseFrameForBlur() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        prepareFramebuffers(client, true, false);
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

    /**
     * Renders a blurred region using the high-quality two-pass separable
     * Gaussian (horizontal + vertical, via {@code blur_gaussian.fsh}).
     * The result visually outperforms the single-pass Kawase path at the
     * cost of two extra FBO blit passes - acceptable for per-frame GUI
     * rendering (once per menu open) but too expensive for per-HUD-widget
     * use. Used exclusively by {@code MenuScreen.renderGuiRegionBlur}.
     */
    public void renderGaussian(ShapeProperties shape) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepareFramebuffers(client, false, true)) {
            return;
        }
        Theme theme = Theme.getInstance();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        applyGaussianBlur(client, blurRadius);
        float scale = (float) client.getWindow().getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(scale);
        Vector4f round = new Vector4f(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
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
        RenderSystem.setShaderTexture(0, pong.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        if (shader != null) {
            shader.getUniformOrDefault("Size").set(width, height);
            shader.getUniformOrDefault("Radius").set(round);
            shader.getUniformOrDefault("Smoothness").set(softness);
            shader.getUniformOrDefault("BlurRadius").set(0.0F);
            shader.getUniformOrDefault("BlurMode").set(0);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } else {
            buffer.end();
        }
        RenderSystem.enableDepthTest();
        restoreRenderState(true);
    }

    public void renderCached(ShapeProperties shape) {
        render(shape, true);
    }

    public void renderWorldRect(Matrix4f matrix, float x, float y, float width, float height, float quality, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        if (!prepareFramebuffers(client, false, false)) {
            return;
        }

        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        // No fallback throttling for nametag blur path: always refresh source
        // from the current world frame to avoid stale-snapshot artifacts.
        captureWorldInput(client, framebufferWidth, framebufferHeight);
        worldCaptureInitialized = true;
        lastWorldCaptureNs = System.nanoTime();
        lastWorldCaptureWidth = framebufferWidth;
        lastWorldCaptureHeight = framebufferHeight;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(matrix, buffer, x, y, width, height, color);

        RenderSystem.setShaderTexture(0, input.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        if (shader != null) {
            Theme theme = Theme.getInstance();
            int blurMode = theme.getHudBlurMode();
            shader.getUniformOrDefault("Size").set(Math.max(1.0f, width), Math.max(1.0f, height));
            shader.getUniformOrDefault("Radius").set(new Vector4f(0.0f));
            shader.getUniformOrDefault("Smoothness").set(0.001f);
            shader.getUniformOrDefault("BlurRadius").set(Math.max(0.0f, quality) * theme.getHudBlurRadiusMultiplier());
            shader.getUniformOrDefault("BlurMode").set(blurMode);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } else {
            buffer.end();
        }

        client.getFramebuffer().beginWrite(false);
        restoreRenderState(true);
    }

    /**
     * Captures the world framebuffer once per rendered frame for world-space
     * blur consumers (nametag backdrop). This prevents mid-frame recaptures
     * while labels are being drawn, which can cause visible flicker.
     */
    public void captureWorldSpaceFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        if (!prepareFramebuffers(client, false, false)) {
            return;
        }
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        long stamp = System.nanoTime();
        // Quantize to ~1 frame at 240 FPS to dedupe duplicate callbacks
        // in the same render frame.
        long bucket = stamp / 4_000_000L;
        if (bucket == worldSpaceFrameStamp
                && framebufferWidth == lastWorldCaptureWidth
                && framebufferHeight == lastWorldCaptureHeight) {
            return;
        }
        worldSpaceFrameStamp = bucket;
        captureWorldInput(client, framebufferWidth, framebufferHeight);
        worldCaptureInitialized = true;
        lastWorldCaptureNs = stamp;
        lastWorldCaptureWidth = framebufferWidth;
        lastWorldCaptureHeight = framebufferHeight;
    }

    public void renderCachedBatch(List<ShapeProperties> shapes) {
        if (shapes == null || shapes.isEmpty()) {
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
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                hudBatchStateApplied = true;
            }
        } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
        }

        ShaderProgram shader = null;
        PreparedBlurState activeState = null;
        for (ShapeProperties shape : shapes) {
            if (shape == null) {
                continue;
            }
            PreparedBlurState preparedState = resolvePreparedBlurState(client, shape);
            if (preparedState == null) {
                continue;
            }
            if (!preparedState.matches(activeState)) {
                RenderSystem.setShaderTexture(0, preparedState.sourceTexture());
                shader = RenderSystem.setShader(MASK_SHADER_KEY);
                hudBatchMaskShader = shader;
                if (shader == null) {
                    if (!useHudBatch) {
                        restoreRenderState(true);
                    }
                    return;
                }
                activeState = preparedState;
            }
            renderPreparedShapeWithBoundShader(shape, shader, preparedState);
        }

        if (useHudBatch) {
            int target = HudBuffer.activeCaptureTarget >= 0 ? HudBuffer.activeCaptureTarget : client.getFramebuffer().fbo;
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
        } else {
            restoreRenderState(true);
        }
    }

    private void renderPreparedShapeWithBoundShader(ShapeProperties shape, ShaderProgram shader, PreparedBlurState preparedState) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || input == null || shader == null || preparedState == null) {
            return;
        }

        float scale = (float) client.getWindow().getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(scale);
        Vector4f round = new Vector4f(shape.getRound()).mul(size.y);
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

        shader.getUniformOrDefault("Size").set(width, height);
        shader.getUniformOrDefault("Radius").set(round);
        shader.getUniformOrDefault("Smoothness").set(softness);
        shader.getUniformOrDefault("BlurRadius").set(preparedState.blurRadius());
        shader.getUniformOrDefault("BlurMode").set(preparedState.blurMode());
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void render(ShapeProperties shape, boolean cacheFrame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }

        // Drain pending batched rects BEFORE the blur shader captures
        // the framebuffer. Blur reads the current main FB color as
        // input - any rects still sitting in the BatchedRectangle
        // BufferBuilder have not actually rasterized yet, so without a
        // flush the blur input would miss them and they would later
        // composite on TOP of the blur (wrong layering: a card's blur
        // backdrop should sample the world AND any earlier-submitted
        // GUI panels behind it, not skip over them).
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
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                hudBatchStateApplied = true;
            }
        } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
        }

        if (!renderPreparedShape(shape)) {
            if (!useHudBatch) {
                restoreRenderState(true);
            }
            return;
        }

        if (useHudBatch) {
            int target = HudBuffer.activeCaptureTarget >= 0 ? HudBuffer.activeCaptureTarget : client.getFramebuffer().fbo;
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
        }

        if (!useHudBatch) {
            restoreRenderState(true);
        }
    }

    private boolean renderPreparedShape(ShapeProperties shape) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || input == null) {
            return false;
        }

        PreparedBlurState preparedState = resolvePreparedBlurState(client, shape);
        if (preparedState == null) {
            return false;
        }

        float scale = (float) client.getWindow().getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(scale);
        Vector4f round = new Vector4f(shape.getRound()).mul(size.y);
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

        RenderSystem.setShaderTexture(0, preparedState.sourceTexture());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        hudBatchMaskShader = shader;
        if (shader == null) {
            return false;
        }
        shader.getUniformOrDefault("Size").set(width, height);
        shader.getUniformOrDefault("Radius").set(round);
        shader.getUniformOrDefault("Smoothness").set(softness);
        shader.getUniformOrDefault("BlurRadius").set(preparedState.blurRadius());
        shader.getUniformOrDefault("BlurMode").set(preparedState.blurMode());
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        return true;
    }

    private PreparedBlurState resolvePreparedBlurState(MinecraftClient client, ShapeProperties shape) {
        if (client == null || shape == null || input == null) {
            return null;
        }

        Theme theme = Theme.getInstance();
        int blurMode = theme.getHudBlurMode();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        float hudGaussianRadius = blurRadius * HUD_GAUSSIAN_STRENGTH_MULTIPLIER;
        int effectiveBlurMode = blurMode;
        float effectiveBlurRadius = blurRadius;
        int sourceTexture = input.getColorAttachment();

        if (blurMode == 2 && blurRadius > 0.0f) {
            BlurRegion blurRegion = computeHudGaussianRegion(client, shape, hudGaussianRadius);
            if (shouldUseFastHudBlur(blurRegion, hudGaussianRadius)) {
                effectiveBlurMode = 2;
                effectiveBlurRadius = Math.min(HUD_FAST_BLUR_RADIUS_CAP, Math.max(blurRadius, blurRadius * HUD_FAST_BLUR_RADIUS_MULTIPLIER));
            } else if (applyOptimizedHudGaussianBlur(client, hudGaussianRadius, blurRegion)) {
                sourceTexture = pong.getColorAttachment();
                effectiveBlurMode = 0;
                effectiveBlurRadius = 0.0f;
            } else {
                effectiveBlurMode = 0;
                effectiveBlurRadius = hudGaussianRadius;
            }
        }

        return new PreparedBlurState(sourceTexture, effectiveBlurMode, effectiveBlurRadius);
    }

    private boolean prepareFramebuffers(MinecraftClient client, boolean cacheFrame, boolean refreshNonCachedInput) {
        Framebuffer framebuffer = client.getFramebuffer();
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        boolean resized = false;

        if (input == null) {
            input = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
            input.setTexFilter(GL11C.GL_LINEAR);
            ping = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
            ping.setTexFilter(GL11C.GL_LINEAR);
            pong = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
            pong.setTexFilter(GL11C.GL_LINEAR);
            halfA = new SimpleFramebuffer(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2), false);
            halfA.setTexFilter(GL11C.GL_LINEAR);
            halfB = new SimpleFramebuffer(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2), false);
            halfB.setTexFilter(GL11C.GL_LINEAR);
            quarterA = new SimpleFramebuffer(Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4), false);
            quarterA.setTexFilter(GL11C.GL_LINEAR);
            quarterB = new SimpleFramebuffer(Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4), false);
            quarterB.setTexFilter(GL11C.GL_LINEAR);
            resized = true;
        } else if (input.textureWidth != framebufferWidth || input.textureHeight != framebufferHeight) {
            input.resize(framebufferWidth, framebufferHeight);
            input.setTexFilter(GL11C.GL_LINEAR);
            ping.resize(framebufferWidth, framebufferHeight);
            ping.setTexFilter(GL11C.GL_LINEAR);
            pong.resize(framebufferWidth, framebufferHeight);
            pong.setTexFilter(GL11C.GL_LINEAR);
            halfA.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
            halfA.setTexFilter(GL11C.GL_LINEAR);
            halfB.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
            halfB.setTexFilter(GL11C.GL_LINEAR);
            quarterA.resize(Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4));
            quarterA.setTexFilter(GL11C.GL_LINEAR);
            quarterB.resize(Math.max(1, framebufferWidth / 4), Math.max(1, framebufferHeight / 4));
            quarterB.setTexFilter(GL11C.GL_LINEAR);
            resized = true;
        }

        if (input == null || ping == null || pong == null) {
            return false;
        }
        if (halfA == null || halfB == null || quarterA == null || quarterB == null) {
            return false;
        }

        if (cacheFrame) {
            // Capture the world framebuffer once per frame for HUD blur. We
            // can't gate this on camera movement: entities (other players,
            // mobs, items, particles) animate even when the camera stands
            // still, so a stale snapshot would show the world *behind* an
            // entity instead of a blurred version of the entity itself.
            // beginCachedFrame() resets cachedFramePrepared to false at the
            // start of every InGameHud.render frame, so this captures exactly
            // once per frame and is reused by subsequent blur HUDs.
            boolean needsCapture = !cachedFramePrepared || resized || forceHudRefresh;
            if (needsCapture) {
                captureWorldInput(client, framebufferWidth, framebufferHeight);
                forceHudRefresh = false;
            }
            // Keep camera tracking up to date so other call sites that still
            // rely on hasCameraMoved() observe consistent deltas.
            hasCameraMoved(client);
            cachedFramePrepared = true;
            return true;
        }

        if (refreshNonCachedInput) {
            captureWorldInput(client, framebufferWidth, framebufferHeight);
        }
        return true;
    }

    private void captureWorldInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        // When a HUD batch capture is active, mc.getFramebuffer() is redirected
        // to the HUD FBO by MinecraftClientFramebufferMixin. We need the REAL
        // main framebuffer here to read the world content for the blur backdrop.
        Framebuffer framebuffer = HudBuffer.activeCaptureTarget >= 0
                ? vorga.phazeclient.api.system.hud.BatchedHudBuffer.INSTANCE.getRealMainFramebuffer()
                : client.getFramebuffer();
        if (framebuffer == null) {
            framebuffer = client.getFramebuffer();
        }
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, input.fbo);
        GL30C.glBlitFramebuffer(
                0,
                0,
                framebufferWidth,
                framebufferHeight,
                0,
                0,
                framebufferWidth,
                framebufferHeight,
                GL30C.GL_COLOR_BUFFER_BIT,
                GL11C.GL_LINEAR
        );
        framebuffer.beginWrite(false);
        if (HudBuffer.activeCaptureTarget >= 0) {
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, HudBuffer.activeCaptureTarget);
        }
        dualKawasePrepared = false;
        preparedHudGaussianRegionCount = 0;
    }

    private boolean applyDualKawaseBlur(MinecraftClient client, float blurRadius) {
        if (input == null || pong == null || halfA == null || halfB == null || quarterA == null || quarterB == null) {
            return false;
        }
        int w = input.textureWidth;
        int h = input.textureHeight;
        // Keep radius continuous to avoid abrupt jumps on the HUD slider.
        float quantizedRadius = MathHelper.clamp(blurRadius, 0.0f, 24.0f);
        if (dualKawasePrepared
                && Math.abs(lastDualKawaseRadius - quantizedRadius) < 0.05f
                && lastDualKawaseWidth == w
                && lastDualKawaseHeight == h) {
            return true;
        }

        ShaderProgram shader = RenderSystem.setShader(DUAL_KAWASE_SHADER_KEY);
        if (shader == null) {
            return false;
        }

        // Keep the HUD slider visually progressive: very low radii should
        // start almost clean instead of jumping straight into a strong blur.
        float normalized = MathHelper.clamp(quantizedRadius / 8.0f, 0.0f, 1.0f);
        float downOffset1 = quantizedRadius * 0.08f;
        float downOffset2 = quantizedRadius * 0.10f;
        runDualKawasePass(shader, input, halfA, downOffset1, true);
        runDualKawasePass(shader, halfA, quarterA, downOffset2, true);

        // Blur on x4 surface using a fixed pass count for smooth slider response
        // (no step-jumps when radius crosses thresholds).
        int passes = 2;
        Framebuffer src = quarterA;
        Framebuffer dst = quarterB;
        for (int i = 0; i < passes; i++) {
            float offset = quantizedRadius * (0.18f + i * (0.02f + normalized * 0.015f));
            runDualKawasePass(shader, src, dst, offset, true);
            Framebuffer tmp = src;
            src = dst;
            dst = tmp;
        }

        // Upsample x4 -> x2 -> x1.
        float upOffset1 = quantizedRadius * 0.09f;
        float upOffset2 = quantizedRadius * 0.05f;
        runDualKawasePass(shader, src, halfB, upOffset1, false);
        runDualKawasePass(shader, halfB, pong, upOffset2, false);

        bindMainDrawTarget(client);
        dualKawasePrepared = true;
        lastDualKawaseRadius = quantizedRadius;
        lastDualKawaseWidth = w;
        lastDualKawaseHeight = h;
        return true;
    }

    private boolean applyOptimizedHudGaussianBlur(MinecraftClient client, float blurRadius, BlurRegion region) {
        if (input == null || ping == null || pong == null || region == null) {
            return false;
        }

        float cachedRadius = MathHelper.clamp(blurRadius, 0.0f, 32.0f);
        long regionKey = computeHudGaussianRegionKey(region, cachedRadius);
        if (hasPreparedHudGaussianRegion(regionKey)) {
            return true;
        }

        ShaderProgram shader = RenderSystem.setShader(GAUSSIAN_SHADER_KEY);
        if (shader == null) {
            return false;
        }

        // Full-resolution separable Gaussian cached once per blur-state.
        // The previous half-resolution prepass softened the cost, but it
        // also visibly reduced backdrop quality on HUDs. We keep the same
        // cached once-per-state flow, just blur directly from the captured
        // full-resolution frame.
        runGaussianPass(shader, input, ping, 1.0F, 0.0F, cachedRadius, region);
        runGaussianPass(shader, ping, pong, 0.0F, 1.0F, cachedRadius, region);

        bindMainDrawTarget(client);
        rememberPreparedHudGaussianRegion(regionKey);
        return true;
    }

    private void runDualKawasePass(ShaderProgram shader, Framebuffer source, Framebuffer target, float offset, boolean downsample) {
        target.beginWrite(false);
        RenderSystem.viewport(0, 0, target.textureWidth, target.textureHeight);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, source.getColorAttachment());
        shader = RenderSystem.setShader(DUAL_KAWASE_SHADER_KEY);
        if (shader == null) {
            target.endWrite();
            return;
        }
        shader.getUniformOrDefault("TexelSize").set(1.0F / source.textureWidth, 1.0F / source.textureHeight);
        shader.getUniformOrDefault("Offset").set(offset);
        shader.getUniformOrDefault("Downsample").set(downsample ? 1 : 0);
        ShaderHelper.drawFullScreenQuad();
        target.endWrite();
    }

    private boolean hasCameraMoved(MinecraftClient client) {
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return false;
        }
        var camera = client.gameRenderer.getCamera();
        var pos = camera.getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        // Check GUI state
        boolean guiActive = client.currentScreen != null;
        boolean guiJustClosed = lastGuiActive && !guiActive;

        // Check zoom state
        boolean zoomActive = false;
        float zoomLevel = 1.0f;
        boolean zoomModuleEnabled = false;
        try {
            vorga.phazeclient.implement.features.modules.other.Zoom zoomModule = 
                vorga.phazeclient.implement.features.modules.other.Zoom.getInstance();
            if (zoomModule != null) {
                zoomModuleEnabled = zoomModule.isEnabled();
                if (zoomModuleEnabled) {
                    zoomActive = vorga.phazeclient.implement.features.modules.other.Zoom.isZoomActive();
                    zoomLevel = zoomModule.getCurrentZoomLevel();
                }
            }
        } catch (Exception e) {
            // Ignore zoom errors
        }

        // Detect zoom out start (transition from active to inactive)
        boolean zoomJustDeactivated = lastZoomActive && !zoomActive;
        if (zoomJustDeactivated) {
            // Fixed duration: always 60 frames (1 second at 60 FPS) regardless of zoom level
            zoomOutAnimationFrames = 60;
        }

        // Decrement animation frame counter
        if (zoomOutAnimationFrames > 0) {
            zoomOutAnimationFrames--;
        }

        // Check if zoom animation just finished (zoom level returned to 1.0)
        boolean zoomAnimationFinished = !zoomActive && lastZoomLevel != 1.0f && Math.abs(zoomLevel - 1.0f) < 0.001f;

        boolean zoomChanged = zoomJustDeactivated ||
                              (zoomActive != lastZoomActive) || 
                              (zoomActive && Math.abs(zoomLevel - lastZoomLevel) > 0.001f) ||
                              (zoomOutAnimationFrames > 0) || // Update during zoom out animation
                              zoomActive || // Always update while zoom is active
                              zoomAnimationFinished; // Update when zoom finishes

        boolean moved = Double.isNaN(lastCameraX)
                || Math.abs(pos.x - lastCameraX) > 1.0E-6
                || Math.abs(pos.y - lastCameraY) > 1.0E-6
                || Math.abs(pos.z - lastCameraZ) > 1.0E-6
                || Math.abs(yaw - lastYaw) > 1.0E-4f
                || Math.abs(pitch - lastPitch) > 1.0E-4f
                || zoomChanged
                || guiJustClosed; // Update when GUI closes

        lastCameraX = pos.x;
        lastCameraY = pos.y;
        lastCameraZ = pos.z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastZoomLevel = zoomLevel;
        lastZoomActive = zoomActive;
        lastGuiActive = guiActive;
        return moved;
    }

    public float getPlayerSpeed(MinecraftClient client) {
        if (client == null || client.player == null) {
            return 0.0f;
        }

        var playerPos = client.player.getPos();
        long currentTime = System.currentTimeMillis();

        if (Double.isNaN(lastPlayerX)) {
            lastPlayerX = playerPos.x;
            lastPlayerY = playerPos.y;
            lastPlayerZ = playerPos.z;
            lastSpeedCheckTime = currentTime;
            return 0.0f;
        }

        double deltaTime = (currentTime - lastSpeedCheckTime) / 1000.0; // seconds
        if (deltaTime < 0.05) { // Update every 50ms minimum
            return 0.0f;
        }

        double dx = playerPos.x - lastPlayerX;
        double dy = playerPos.y - lastPlayerY;
        double dz = playerPos.z - lastPlayerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float speed = (float) (distance / deltaTime); // blocks per second

        lastPlayerX = playerPos.x;
        lastPlayerY = playerPos.y;
        lastPlayerZ = playerPos.z;
        lastSpeedCheckTime = currentTime;

        return speed;
    }

    private boolean applyGaussianBlur(MinecraftClient client, float blurRadius) {
        if (blurRadius <= 0.0F) {
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, input.fbo);
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, pong.fbo);
            GL30C.glBlitFramebuffer(
                    0,
                    0,
                    input.textureWidth,
                    input.textureHeight,
                    0,
                    0,
                    pong.textureWidth,
                    pong.textureHeight,
                    GL30C.GL_COLOR_BUFFER_BIT,
                    GL11C.GL_LINEAR
            );
            bindMainDrawTarget(client);
            return true;
        }

        ShaderProgram shader = RenderSystem.setShader(GAUSSIAN_SHADER_KEY);
        if (shader == null) {
            return false;
        }

        runGaussianPass(shader, input, ping, 1.0F, 0.0F, blurRadius, null);
        runGaussianPass(shader, ping, pong, 0.0F, 1.0F, blurRadius, null);
        bindMainDrawTarget(client);
        return true;
    }

    private void runGaussianPass(ShaderProgram shader, Framebuffer source, Framebuffer target, float directionX, float directionY, float blurRadius, BlurRegion region) {
        target.beginWrite(false);
        RenderSystem.viewport(0, 0, target.textureWidth, target.textureHeight);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, source.getColorAttachment());

        shader = RenderSystem.setShader(GAUSSIAN_SHADER_KEY);
        if (shader == null) {
            target.endWrite();
            return;
        }

        int support = MathHelper.clamp(Math.round(blurRadius), 1, 64);
        float sigma = Math.max(1.0F, blurRadius * 0.55F);

        shader.getUniformOrDefault("Direction").set(directionX, directionY);
        shader.getUniformOrDefault("TexelSize").set(1.0F / source.textureWidth, 1.0F / source.textureHeight);
        shader.getUniformOrDefault("Support").set(support);
        shader.getUniformOrDefault("Sigma").set(sigma);
        shader.getUniformOrDefault("Brightness").set(1.0F);

        if (region != null) {
            RenderSystem.enableScissor(region.x, region.y, region.width, region.height);
        }
        ShaderHelper.drawFullScreenQuad();
        if (region != null) {
            RenderSystem.disableScissor();
        }
        // Ensure we finish writing to the target FBO for this pass
        target.endWrite();
    }

    private BlurRegion computeHudGaussianRegion(MinecraftClient client, ShapeProperties shape, float blurRadius) {
        if (client == null || client.getWindow() == null || shape == null || shape.getMatrix() == null) {
            return null;
        }

        float scale = (float) client.getWindow().getScaleFactor();
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        float softness = Math.max(0.001F, shape.getSoftness());
        Vector3f pos = matrix4f.transformPosition(shape.getX() - softness / 2.0F, shape.getY() - softness / 2.0F, 0.0F, new Vector3f()).mul(scale);
        Vector3f size = matrix4f.getScale(new Vector3f()).mul(scale);
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

    private boolean shouldUseFastHudBlur(BlurRegion region, float blurRadius) {
        if (region == null) {
            return true;
        }

        long area = (long) region.width * (long) region.height;
        return area <= HUD_FAST_BLUR_REGION_AREA_THRESHOLD && blurRadius <= 18.0f;
    }

    private record BlurRegion(int x, int y, int width, int height) {
    }

    private record PreparedBlurState(int sourceTexture, int blurMode, float blurRadius) {
        private boolean matches(PreparedBlurState other) {
            return other != null
                    && sourceTexture == other.sourceTexture
                    && blurMode == other.blurMode
                    && Float.floatToRawIntBits(blurRadius) == Float.floatToRawIntBits(other.blurRadius);
        }
    }

    private void bindMainDrawTarget(MinecraftClient client) {
        if (client == null || client.getFramebuffer() == null) {
            return;
        }
        if (HudBuffer.activeCaptureTarget >= 0) {
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, HudBuffer.activeCaptureTarget);
        } else {
            client.getFramebuffer().beginWrite(false);
        }
    }

    private static void restoreRenderState(boolean enableDepthTest) {
        RenderSystem.depthMask(true);
        if (enableDepthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
