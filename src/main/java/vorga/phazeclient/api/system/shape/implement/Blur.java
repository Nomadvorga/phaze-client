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
    private static final float HUD_GAUSSIAN_STRENGTH_MULTIPLIER = 2.5F;
    private static final int MAX_PREPARED_HUD_KAWASE_REGIONS = 32;
    private static final float HUD_FINE_KAWASE_THRESHOLD = 8.0F;
    private static final long MAX_HUD_BLUR_REFRESH_INTERVAL_NS = 33_333_334L;
    private static final long MIN_HUD_BLUR_REFRESH_INTERVAL_NS = 16_666_667L;
    private static final long MENU_BLUR_REFRESH_INTERVAL_NS = 16_666_667L;
    private static final long NAMETAG_BLUR_REFRESH_INTERVAL_NS = 8_333_333L;
    private static final int MENU_BLUR_CACHE_SLOTS = 4;
    private static final int MAX_HUD_BLUR_STATES = 32;
    private static final int MAX_PREPARED_HUD_GAUSSIAN_REGIONS = 32;

    /**
     * Replacement for {@code glBlitFramebuffer}.
     *
     * <p>1.21.11 removed the read/draw framebuffer bindings this class used to
     * blit through, and {@code CommandEncoder.copyTextureToTexture} is not a
     * drop-in: it hardcodes {@code GL_NEAREST} and reuses one rectangle for
     * both source and destination, so it can neither scale nor filter. Every
     * copy that changes resolution (full -> half for {@code hudHalfInput}, the
     * half-res Kawase result back up to a full-res cache slot) therefore
     * becomes a full-screen quad pass sampling the source colour attachment.
     *
     * <p>Same vanilla shaders as {@code ScreenBlit}, but blending is left OFF
     * so the copy replaces the destination exactly like the old blit did
     * instead of compositing onto it. The quad is synthesised from
     * {@code gl_VertexID} inside {@code core/screenquad}, hence the empty
     * vertex format and {@code draw(0, 3)}.
     */
    private static final RenderPipeline COPY_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("phaze", "pipeline/blur_copy"))
            .withVertexShader(Identifier.of("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.of("minecraft", "core/blit_screen"))
            .withSampler("InSampler")
            // Explicit: a copy must REPLACE the destination. Leaving blending
            // to the builder default would be a silent behaviour change the
            // day that default moves.
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withColorWrite(true, true)
            // Builder defaults cull to true; a screen quad renders nothing with
            // culling on.
            .withCull(false)
            .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
            .build();

    /**
     * Dual-Kawase down/upsample pass.
     *
     * <p>Like {@link #COPY_PIPELINE} this is a bufferless fullscreen triangle;
     * the only additions are the sampler and the std140 block that replaced the
     * pass' loose uniforms. Blending stays off because each pass fully replaces
     * its target - these render into Phaze's own half/quarter FBOs, never onto
     * the screen.
     */
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

    /** Separable Gaussian pass; same shape as {@link #DUAL_KAWASE_PIPELINE}. */
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

    /**
     * The composite: paints an already-blurred surface into a rounded GUI rect.
     *
     * <p>Unlike the two pass pipelines this one has real geometry (the rect's
     * quad, POSITION_COLOR) and two samplers - the blurred surface plus the
     * previous frame, which the temporal mix reads when {@code FrameMix < 1}.
     */
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

    private static final int DUAL_KAWASE_UBO_SIZE = 16;
    private static final int GAUSSIAN_UBO_SIZE = 32;
    private static final int COMPOSITE_UBO_SIZE = 64;

    private GpuBuffer compositeUbo;

    /**
     * One UBO per pass kind, reused across the whole chain.
     *
     * <p>A blur is six to eight passes per frame, each with different offsets.
     * Allocating a buffer per pass would churn GPU memory every frame; instead
     * the contents are rewritten before each pass, which is a 16- or 32-byte
     * host-to-device copy. Created lazily because no GpuDevice exists yet when
     * this class initialises.
     */
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
    /**
     * Bumped every time {@link #captureMenuInput} replaces the pre-menu
     * snapshot. Slots record the revision they were blurred from, so a new
     * capture forces every region to re-blur instead of letting some regions
     * keep a backdrop derived from an older snapshot than their neighbours.
     */
    private long menuInputRevision = 0L;
    /**
     * Snapshot of the menu AFTER its own content is drawn but BEFORE any
     * window / popup is drawn. Popups blur from this instead of the pre-menu
     * image, so a color picker's backdrop continues the menu's blur rather
     * than punching a hole straight through to the world behind it. Taken
     * before the popups themselves are painted, so there is still no
     * self-feedback.
     */
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
        // InGameHud calls this after vanilla HUD rendering has finished but
        // before Phaze HUDs or the current Screen are drawn. At this point the
        // main framebuffer is guaranteed to contain a valid world frame, so a
        // GUI transition must refresh from here instead of letting the first
        // blur widget lazily capture an intermediate/cleared framebuffer.
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

    /** Renders the menu backdrop with cached Dual Kawase and temporal blending. */
    public void renderGaussian(ShapeProperties shape) {
        renderGaussian(shape, false);
    }

    /**
     * Menu backdrop for windows / popups (color picker, group window).
     *
     * <p>Identical to {@link #renderGaussian(ShapeProperties)} except that it
     * blurs the post-menu snapshot taken by {@link #captureMenuOverlayFrame()},
     * so the popup's backdrop continues the menu's own blur instead of
     * showing the world straight through it.
     */
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

        // The pre-menu snapshot is refreshed HERE, before the per-slot refresh
        // decision - not inside it.
        //
        // It used to sit inside `if (refresh)`, which meant the capture point
        // drifted through the frame: on a frame where the backdrop's slot was
        // still fresh but a popup's slot was stale, the popup performed the
        // capture, and by then the main framebuffer already held the fully
        // drawn menu. So `menuInput` alternated between "clean world" and
        // "world + menu", and every backdrop derived from it flickered. Doing
        // it up-front means the capture always lands on the first blur region
        // of the frame, i.e. the menu backdrop, while the framebuffer is
        // still clean.
        boolean useOverlay = overlaySource && menuOverlayValid && menuOverlayInput != null;
        if (!useOverlay && now - menuInputLastCaptureNs >= MENU_BLUR_REFRESH_INTERVAL_NS) {
            // Only a non-overlay caller may take this snapshot, and the only
            // non-overlay caller is the menu backdrop, which draws first.
            // That pins the capture to a point where the framebuffer still
            // holds the pre-menu image.
            captureMenuInput(client, menuInput.textureWidth, menuInput.textureHeight);
            menuInputLastCaptureNs = now;
            menuInputRevision++;
        }

        Framebuffer source = useOverlay ? menuOverlayInput : menuInput;
        long sourceRevision = useOverlay ? menuOverlayRevision : menuInputRevision;

        long regionKey = blurRegion == null
                ? 0x6A09E667F3BCC909L
                : computeMenuBlurRegionKey(blurRegion);
        // Menu and popup blur regions must not share a single result: that
        // made their cached framebuffer alternate every refresh and caused
        // a visible flicker around color pickers. They now also read from
        // different sources, so the salt keeps their slots distinct even
        // when the two regions happen to line up geometrically.
        if (useOverlay) {
            regionKey ^= 0x9E3779B97F4A7C15L;
        }
        MenuBlurSlot slot = acquireMenuBlurSlot(regionKey, now);
        // Keyed on the source snapshot rather than a wall-clock interval, so
        // every region in a frame is derived from the same pixels. A slot
        // whose snapshot has not changed needs no re-blur at all.
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
        // This path blurs into a per-region slot framebuffer, so that slot's
        // colour attachment is what the composite samples.
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
        render(shape, true);
    }

    /**
     * Captures the clean world image used by world-space nametag blur.
     * Callers may invoke this after flushing entity geometry but before
     * submitting the nametag's see-through text, preventing the label from
     * being sampled into its own blur.
     */
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

        // Sneaking labels have no vanilla SEE_THROUGH background, so they
        // request the selected-color through-wall fallback here. Normal labels
        // already queued that same fallback in their existing text pass.
        if (drawFallback) {
            drawWorldFallbackRectContents(matrix, x, y, width, height, tintColor);
        }

        if (clampedOpacity <= 0.001F) {
            restoreRenderState(true);
            return;
        }

        // Respect both block and entity depth. EntityRendererMixin flushes the
        // current model before this draw, so the player's own geometry also
        // participates instead of the blur being stamped over it.
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int blurColor = (MathHelper.clamp(Math.round(clampedOpacity * 255.0F), 0, 255) << 24) | 0x00FFFFFF;
        drawEngine.quad(matrix, buffer, x, y, width, height, blurColor);

        Theme theme = Theme.getInstance();
        // RectMask = true: world-space nametag backdrops are plain rectangles
        // and must bypass the rounded SDF, exactly as the shader comment on
        // RectMask describes.
        drawComposite(
                buffer.end(),
                matrix,
                nametagInput != null ? nametagInput.getColorAttachmentView() : null,
                null,
                width, height,
                scratchRound.set(0.0F, 0.0F, 0.0F, 0.0F),
                0.001F,
                0.0F,
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
        // Capture lazily on the first visible nametag. This avoids paying for
        // a full-screen copy in frames where the world has entities but none
        // of them actually renders a label.
        if (!worldSpaceFramePrepared
                || framebufferWidth != lastWorldCaptureWidth
                || framebufferHeight != lastWorldCaptureHeight) {
            long now = System.nanoTime();
            boolean refresh = !nametagBlurWasActive
                    || framebufferWidth != lastWorldCaptureWidth
                    || framebufferHeight != lastWorldCaptureHeight
                    || now - lastNametagBlurRefreshNs >= NAMETAG_BLUR_REFRESH_INTERVAL_NS;
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

    /**
     * Captures the world framebuffer once per rendered frame for world-space
     * blur consumers (nametag backdrop). This prevents mid-frame recaptures
     * while labels are being drawn, which can cause visible flicker.
     */
    public void beginWorldSpaceFrame(boolean enabled) {
        worldSpaceFramePrepared = false;
        worldSpaceSpeedPrepared = false;
        if (!enabled) {
            nametagBlurWasActive = false;
        }
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
                // Was an early return on a null ShaderProgram, which killed the
                // entire HUD batch. Each shape now carries its own uniform
                // upload, so a state change is just a bookkeeping update.
                activeState = preparedState;
            }
            renderPreparedShapeWithBoundShader(shape, shader, preparedState);
        }

        // 1.21.11: nothing to rebind. The draw framebuffer is no longer global
        // state - each render pass names its own colour attachment - so the old
        // "point subsequent draws back at the HUD capture / main FBO" step has
        // no equivalent and nothing to do.
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

        // 1.21.11: the draw-framebuffer rebind that used to run here is gone -
        // see the same note in renderCachedBatch.
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
        // 1.21.11: Framebuffer no longer exposes a raw GL texture id. Samplers
        // are bound from a GpuTextureView, so the prepared state carries the
        // view itself instead of an int handle.
        GpuTextureView sourceTexture = input.getColorAttachmentView();

        BlurRegion visibleRegion = computeHudGaussianRegion(client, shape, 0.0f);
        if (visibleRegion == null) {
            return null;
        }

        if (useHudCache && blurMode == 2 && blurRadius > 0.0f && cacheFrameReadyForHud()) {
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
            // 1.21.11: SimpleFramebuffer takes a debug name as its FIRST arg.
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
            // The menu / HUD cache slots are NOT allocated here - see
            // ensureBlurSlots(). Each slot owns a full-resolution
            // framebuffer, and the two groups together are 8 of them
            // (~66 MB at 1080p, ~118 MB at 1440p, ~265 MB at 4K). Reserving
            // both up front charged that to every session, including ones
            // that only ever use HUD blur or only ever open the menu.
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
            // Null slots are groups that were never used this session; they
            // get created at the new size by ensureBlurSlots() on demand.
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
            // Popup snapshot is resized lazily in captureMenuOverlayFrame();
            // just mark it stale so nothing blurs a mismatched copy.
            menuOverlayValid = false;
            hudInputValid = false;
            invalidateHudKawaseCache();
            resized = true;
        }

        if (input == null || menuInput == null || hudHalfInput == null || nametagInput == null || ping == null || pong == null) {
            return false;
        }
        // menuBlurSlots / hudBlurSlots are deliberately not checked here:
        // they are created lazily by ensureBlurSlots() at the point of use.
        if (halfA == null || halfB == null || quarterA == null || quarterB == null) {
            return false;
        }

        if (cacheFrame) {
            if (cachedFramePrepared && !resized && !forceHudRefresh) {
                return true;
            }
            long now = System.nanoTime();
            long backgroundStateKey = computeHudBackgroundStateKey(client);
            int targetFps = vorga.phazeclient.api.system.hud.BatchedHudBuffer.INSTANCE.getTargetFps();
            long refreshIntervalNs = MathHelper.clamp(
                    1_000_000_000L / Math.max(1, targetFps),
                    MIN_HUD_BLUR_REFRESH_INTERVAL_NS,
                    MAX_HUD_BLUR_REFRESH_INTERVAL_NS
            );
            boolean backgroundChanged = backgroundStateKey != lastHudBackgroundStateKey;
            boolean refreshDue = now - lastHudInputRefreshNs >= refreshIntervalNs;
            // Opening a GUI can temporarily leave the main framebuffer in a
            // cleared/intermediate state. Re-capturing it makes every HUD
            // blur mask flash black for one frame. Keep the last valid world
            // snapshot while a GUI is open; it is both stable and cheaper.
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

    /**
     * Snapshots the menu with its own content already drawn, for popups to
     * blur from.
     *
     * <p>Call this from the window / popup render pass, before any window is
     * painted. Everything drawn up to this point (menu backdrop, panels,
     * cards, text) ends up in the snapshot; the windows themselves do not, so
     * a popup still cannot blur its own output.
     *
     * <p>No-ops when the menu blur pipeline isn't up yet, in which case
     * popups transparently fall back to the pre-menu snapshot - i.e. the
     * previous behaviour.
     */
    public void captureMenuOverlayFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        // Menu geometry is queued in the shared BatchedRectangle buffer; it
        // has to land in the framebuffer before we can copy it out.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepareFramebuffers(client, false, false)) {
            return;
        }

        int width = Math.max(1, client.getWindow().getFramebufferWidth());
        int height = Math.max(1, client.getWindow().getFramebufferHeight());

        // Allocated on demand: only sessions that actually open a popup pay
        // for this full-resolution target.
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

    /**
     * Copies the live main framebuffer into {@code target}.
     *
     * <p>{@code framebufferWidth} / {@code framebufferHeight} are kept for the
     * callers' sake but are no longer used: the source rectangle was always the
     * whole window framebuffer, and {@link #blitFramebuffer} copies whole
     * surfaces.
     */
    private void captureFramebufferInput(
            MinecraftClient client,
            Framebuffer target,
            int framebufferWidth,
            int framebufferHeight,
            FilterMode filter
    ) {
        // When a HUD batch capture is active, mc.getFramebuffer() is redirected
        // to the HUD FBO by MinecraftClientFramebufferMixin. We need the REAL
        // main framebuffer here to read the world content for the blur backdrop.
        //
        // Asking BatchedHudBuffer whether a capture is live (instead of reading
        // HudBuffer.activeCaptureTarget directly) keeps this independent of how
        // that flag ends up being represented once the capture hook moves to
        // GuiRenderer - it is no longer a GL framebuffer id in 1.21.11.
        Framebuffer framebuffer = BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer() != null
                ? BatchedHudBuffer.INSTANCE.getRealMainFramebuffer()
                : client.getFramebuffer();
        if (framebuffer == null) {
            framebuffer = client.getFramebuffer();
        }
        if (framebuffer == null || target == null) {
            return;
        }
        // 1.21.11: no read/draw framebuffer bindings to save and restore - the
        // render target is chosen per render pass, so the GlStateManager dance
        // that used to bracket this blit has nothing to do.
        blitFramebuffer(framebuffer, target, filter, null);
    }

    /**
     * {@code glBlitFramebuffer} replacement: copies {@code source}'s colour
     * attachment over {@code target}'s, optionally restricted to
     * {@code targetScissor} (in {@code target} pixels, GL bottom-left origin).
     *
     * <p>A 1:1 unfiltered whole-surface copy goes through
     * {@code copyTextureToTexture}, which is the cheap native path. Anything
     * that scales or wants LINEAR filtering has to go through
     * {@link #COPY_PIPELINE} instead - {@code copyTextureToTexture} hardcodes
     * NEAREST and one shared rectangle.
     */
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
                OptionalInt.empty())) {   // empty = preserve, do not clear
            pass.setPipeline(COPY_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            if (targetScissor != null) {
                pass.enableScissor(targetScissor.x, targetScissor.y, targetScissor.width, targetScissor.height);
            }
            // A mistyped sampler name is silently skipped and renders black,
            // never throws - "InSampler" must match core/blit_screen.fsh.
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
        // Keep radius continuous to avoid abrupt jumps on the HUD slider.
        float quantizedRadius = MathHelper.clamp(blurRadius, 0.0f, 24.0f);
        ShaderProgram shader = null; // unused: passes bind their own pipeline

        // Keep the HUD slider visually progressive: very low radii should
        // start almost clean instead of jumping straight into a strong blur.
        float normalized = MathHelper.clamp(quantizedRadius / 8.0f, 0.0f, 1.0f);
        float downOffset1 = quantizedRadius * 0.08f;
        float downOffset2 = quantizedRadius * 0.10f;
        runDualKawasePass(shader, sourceInput, halfA, downOffset1, true, region);
        runDualKawasePass(shader, halfA, quarterA, downOffset2, true, region);

        // Blur on x4 surface using a fixed pass count for smooth slider response
        // (no step-jumps when radius crosses thresholds).
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

        // Keep the blur-producing x4 -> x2 upscale, then let the GPU's
        // fixed-function linear filter perform x2 -> x1. The old final
        // shader pass used eight samples for every full-resolution pixel
        // even though the half-resolution image is already smooth.
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
        // The old code blitted sourceRegion -> targetRegion. Both are the SAME
        // fraction of their respective surfaces (scaleBlurRegion scales the
        // region by target-size / input-size), so a whole-surface quad clipped
        // to targetRegion samples exactly the matching source area and gives
        // the same result without needing a per-rect blit the API no longer
        // offers.
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

        ShaderProgram shader = null; // unused: passes bind their own pipeline

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

    /**
     * Creates a slot group's framebuffers on first use.
     *
     * <p>Each slot owns a full-resolution framebuffer. Allocating both the
     * menu group and the HUD group up front in {@code prepareFramebuffers}
     * meant a session that only uses HUD blur still paid for four unused
     * full-screen targets, and vice versa. Both acquire* methods funnel
     * through here, so the group exists by the time any caller dereferences
     * a slot - the never-null contract those callers rely on is preserved.
     *
     * <p>Groups are never freed once created: reclaiming them would mean
     * re-allocating (and re-warming the cache) the next time the user opens
     * the menu, which is exactly the kind of hitch the cache exists to avoid.
     */
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

        ShaderProgram shader = null; // unused: passes bind their own pipeline

        // No downsample here: offset 0 is visually clean and low slider
        // values increase continuously instead of inheriting a fixed blur
        // floor from the half/quarter-resolution pipeline.
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
        // TexelSize is the source's texel step - the sampling offsets are
        // expressed in source texels, which is what makes one pass scale
        // correctly whether it reads the full-res, half-res or quarter-res FBO.
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

    /**
     * Shared body of both blur passes: sample {@code source}, write
     * {@code target}, with the pass' own std140 block bound.
     *
     * <p>1.21.11 removed {@code Framebuffer.beginWrite}/{@code endWrite} - a
     * draw picks its target when it opens a render pass, so the target is named
     * here rather than bound beforehand. {@code OptionalInt.empty()} means
     * "preserve, do not clear"; each pass covers the whole target anyway, and
     * clearing would only add a redundant full-surface write.
     */
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
                // Scissoring the intermediate passes to the blurred region is
                // the biggest win available here: a HUD blur usually covers a
                // small strip, and without this every pass shades the whole
                // half- or quarter-res surface regardless of how little of it
                // is ever read back. scaleBlurRegion maps the region into this
                // target's resolution and pads it, so samples that reach just
                // outside the rect still find real pixels.
                BlurRegion scaled = scaleBlurRegion(region, target.textureWidth, target.textureHeight);
                pass.enableScissor(scaled.x(), scaled.y(), scaled.width(), scaled.height());
            }
            pass.draw(0, 3);
        }
    }

    /**
     * Draws the blur composite quad.
     *
     * @param blurred  the blurred surface to paint
     * @param previous previous frame for the temporal mix; may be null, in
     *                 which case {@code frameMix} is forced to 1 so the shader
     *                 never samples an unbound texture
     */
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

        // Sampler1 is only read when the mix is active, but it must still be
        // bound - an unbound sampler reads undefined data on some drivers, so
        // it aliases Sampler0 when there is no previous frame.
        GpuTextureView previousView = previous != null ? previous : blurred;

        // GUI space needs the ortho projection installed by hand, exactly like
        // every other immediate Phaze draw: 1.21.11 defers DrawContext into a
        // GuiRenderState and only binds that matrix inside GuiRenderer's own
        // pass, which runs later. Without this the composite quad sits in front
        // of the ortho near plane and is clipped away entirely - a real draw
        // call that produces nothing, which is why the blur was invisible in the
        // menu, behind the HUD and in the colour picker alike.
        //
        // drawEngine.quad already transformed the vertices by `modelView` on the
        // CPU, so the shader must NOT apply it a second time - it gets only the
        // z offset (GUI) or identity (world, where the perspective matrix is
        // already live and correct).
        if (guiSpace) {
            vorga.phazeclient.api.system.draw.GuiProjection.begin();
        }
        try {
            Matrix4f pose = guiSpace
                    ? vorga.phazeclient.api.system.draw.GuiProjection.guiModelView(scratchCompositePose)
                    : scratchCompositePose.identity();
            vorga.phazeclient.api.system.draw.GpuDraw.drawWithUniforms(
                    COMPOSITE_PIPELINE,
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

    /** Scratch for the composite's model-view, render thread only. */
    private final Matrix4f scratchCompositePose = new Matrix4f();

    /** Lazily allocates a uniform buffer that {@code writeToBuffer} will accept. */
    private static GpuBuffer ensureUbo(GpuBuffer existing, String label, int size) {
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        // USAGE_COPY_DST is what makes the buffer a legal destination for
        // writeToBuffer; without it the encoder rejects the write outright.
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

        double deltaTime = (currentTime - lastSpeedCheckTime) / 1000.0; // seconds
        if (deltaTime < 0.05) { // Update every 50ms minimum
            return cachedPlayerSpeed;
        }

        double dx = playerPos.x - lastPlayerX;
        double dy = playerPos.y - lastPlayerY;
        double dz = playerPos.z - lastPlayerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        cachedPlayerSpeed = (float) (distance / deltaTime); // blocks per second

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

        ShaderProgram shader = null; // unused: passes bind their own pipeline

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

        // Sigma drives the falloff; support is the half-width of the kernel.
        // Capping support keeps the inner loop bounded no matter what the
        // radius slider is set to - the visual difference past 3*sigma is
        // below one 8-bit step, so the extra taps would cost fill rate for
        // nothing.
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
        // Ignore sub-two-pixel animation jitter. Radius changes are tracked
        // separately with a tolerance, while real movement/resizing still
        // invalidates the cached backdrop immediately.
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
        /** Revision of the snapshot this slot's blur was produced from. */
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
            // Identity comparison is intentional and still correct: a
            // Framebuffer owns exactly one colour-attachment view and replaces
            // it only on resize, so "same view instance" == "same texture", as
            // the old GL id comparison meant.
            return other != null
                    && sourceTexture == other.sourceTexture
                    && blurMode == other.blurMode
                    && Float.floatToRawIntBits(blurRadius) == Float.floatToRawIntBits(other.blurRadius);
        }
    }

    private void bindMainDrawTarget(MinecraftClient client) {
        // 1.21.11: there is no "current draw framebuffer" to point back at.
        // Framebuffer.beginWrite and the GL draw-buffer binding are both gone -
        // every draw names its colour attachment when it opens its render pass,
        // so re-targeting after an offscreen pass is neither possible nor
        // needed. Kept as a no-op so the offscreen passes still read as
        // "...and now we are done writing offscreen".
    }

    // TODO(1.21.11): loose uniforms are gone - GlUniform is a bare marker
    // interface with no set(). The tint has to become part of the blur
    // pipeline's std140 block (or a per-vertex colour) when that pipeline is
    // built. Left computing nothing so the call site keeps its shape.
    /**
     * ARGB int to the {@code TintColor} vec4 the composite block expects.
     *
     * <p>Replaces the old {@code setTintUniform}, which pushed the same four
     * floats straight into a loose uniform - a route 1.21.11 no longer has.
     */
    private static Vector4f tintVector(int argb, Vector4f dest) {
        return dest.set(
                ((argb >>> 16) & 0xFF) / 255.0F,
                ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F);
    }

    private final Vector4f scratchTint = new Vector4f();

    /**
     * Neutral tint: alpha 0 means the shader's {@code mix} keeps the blurred
     * colour untouched. The HUD composite gets its colour from the shape's own
     * vertex colour, so it wants no additional tint.
     */
    private Vector4f noTint() {
        return scratchTint.set(0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static float maxCornerRadius(Vector4f radius) {
        return Math.max(Math.max(radius.x, radius.y), Math.max(radius.z, radius.w));
    }

    private static void restoreRenderState(boolean enableDepthTest) {
        // 1.21.11: blend / depth / cull are pipeline properties, so no imperative
        // GPU state can leak out of a Phaze draw and there is nothing to restore.
        // Kept as a no-op rather than deleted because the call sites document
        // where a draw finishes.
    }
}
