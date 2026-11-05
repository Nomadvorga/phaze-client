package vorga.phazeclient.api.system.shape.implement;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
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
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.HudBlurPlanner;
import vorga.phazeclient.api.system.hud.Render2DState;
import vorga.phazeclient.api.system.hud.Render2DBuffers;
import vorga.phazeclient.implement.features.modules.client.Theme;

import java.util.List;
import java.util.ArrayList;

public class Blur implements Shape {
    public static final Blur INSTANCE = new Blur();
    private static final float HUD_GAUSSIAN_STRENGTH_MULTIPLIER = 2.5F;
    private static final int MAX_PREPARED_HUD_KAWASE_REGIONS = 32;
    private static final float HUD_FINE_KAWASE_THRESHOLD = 8.0F;
    private static final long MENU_BLUR_REFRESH_INTERVAL_NS = 16_666_667L;
    private static final long NAMETAG_BLUR_REFRESH_INTERVAL_NS = 8_333_333L;
    private static final int MENU_BLUR_CACHE_SLOTS = 4;
    private static final int MAX_HUD_BLUR_STATES = 32;
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
    private Framebuffer immediateInput;
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
    private final HudBlurCache hudBlurCache = new HudBlurCache();
    private final HudBlurPlanner hudBlurPlanner = new HudBlurPlanner();
    private boolean hudGroupsPrepared;
    private long hudHalfInputRevision = -1L;
    private final HudRefreshClock hudRefreshClock = new HudRefreshClock(120);
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
    private int hudBackgroundFps = 120;
    private boolean stableHudCapturePoint = false;
    private Object lastObservedScreen = null;
    private final long[] preparedHudGaussianRegionKeys = new long[MAX_PREPARED_HUD_GAUSSIAN_REGIONS];
    private int preparedHudGaussianRegionCount = 0;
    private final Vector3f scratchScale = new Vector3f();
    private final Vector3f scratchPosition = new Vector3f();
    private final Vector4f scratchRound = new Vector4f();
    private static final Vector4f ZERO_ROUND = new Vector4f();

    public void setHudBackgroundFps(int fps) {
        int clamped = MathHelper.clamp(fps, 10, 360);
        if (hudBackgroundFps == clamped) {
            return;
        }
        hudBackgroundFps = clamped;
        hudRefreshClock.setFps(clamped);
        forceHudRefresh = true;
    }

    public int getHudBackgroundFps() {
        return hudBackgroundFps;
    }

    /** Render-thread lifecycle hook. Resets every cache revision and releases lazily allocated targets. */
    public void cleanup() {
        Framebuffer[] targets = {input, immediateInput, menuInput, hudHalfInput, nametagInput, ping, pong,
                halfA, halfB, quarterA, quarterB, menuOverlayInput};
        input = immediateInput = menuInput = hudHalfInput = nametagInput = ping = pong = null;
        halfA = halfB = quarterA = quarterB = menuOverlayInput = null;
        cachedFramePrepared = hudBatchMode = hudBatchStateApplied = false;
        hudBatchMaskShader = null;
        forceHudRefresh = true;
        hudInputValid = menuOverlayValid = menuBlurCurrentValid = menuBlurPreviousValid = false;
        dualKawasePrepared = worldSpaceFramePrepared = worldSpaceSpeedPrepared = nametagBlurWasActive = false;
        stableHudCapturePoint = false;
        lastObservedScreen = null;
        hudInputRevision = menuInputRevision = menuOverlayRevision = 0L;
        hudRefreshClock.reset();
        hudBlurCache.invalidate();
        hudBlurPlanner.reset();
        hudHalfInputRevision = -1L;
        hudGroupsPrepared = false;
        menuInputLastCaptureNs = menuBlurLastRefreshNs = menuBlurTransitionStartNs = 0L;
        lastNametagBlurRefreshNs = lastSpeedCheckTime = 0L;
        menuBlurRegionKey = Long.MIN_VALUE;
        menuBlurRegion = null;
        menuBlurRadius = lastDualKawaseRadius = -1.0F;
        lastWorldCaptureWidth = lastWorldCaptureHeight = lastDualKawaseWidth = lastDualKawaseHeight = -1;
        lastPlayerX = lastPlayerY = lastPlayerZ = Double.NaN;
        cachedPlayerSpeed = 0;
        preparedHudGaussianRegionCount = 0;
        java.util.Arrays.fill(hudStateInitialized, false);
        java.util.Arrays.fill(hudStateKeys, 0L);
        RuntimeException failure = null;
        for (Framebuffer target : targets) {
            if (target == null) continue;
            try { target.delete(); } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        for (MenuBlurSlot[] slots : new MenuBlurSlot[][]{menuBlurSlots, hudBlurSlots}) {
            for (int i = 0; i < slots.length; i++) {
                MenuBlurSlot slot = slots[i];
                slots[i] = null;
                if (slot == null) continue;
                try { slot.framebuffer.delete(); } catch (RuntimeException e) {
                    if (failure == null) failure = e; else failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) throw failure;
    }

    public void beginCachedFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        Object currentScreen = client == null ? null : client.currentScreen;
        if (currentScreen != lastObservedScreen) {
            lastObservedScreen = currentScreen;
            hudInputValid = false;
            forceHudRefresh = true;
            menuInputLastCaptureNs = 0L;
            menuOverlayValid = false;
            BatchedHudBuffer.INSTANCE.invalidate();
            invalidateHudKawaseCache();
            hudBlurPlanner.reset();
            invalidateMenuBlurCache();
        }
        cachedFramePrepared = false;
        hudBatchMode = true;
        hudBatchStateApplied = false;
        hudBatchMaskShader = null;
        preparedHudGaussianRegionCount = 0;
        hudGroupsPrepared = false;
        hudBlurPlanner.beginFrame();
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
            prepare2DFramebuffers(client, true, false);
        } finally {
            stableHudCapturePoint = false;
        }
    }

    public void endCachedFrame() {
        try {
            if (hudBatchStateApplied) restoreRenderState(true);
        } finally {
            hudBlurPlanner.endFrame();
            hudBatchMode = false;
            hudBatchStateApplied = false;
            hudBatchMaskShader = null;
        }
    }

    public void registerHudBlurState(int slot, long stateKey) {
        if (slot < 0 || slot >= hudStateKeys.length) {
            forceHudRefresh = true;
            return;
        }
        if (!hudStateInitialized[slot] || hudStateKeys[slot] != stateKey) {
            hudStateInitialized[slot] = true;
            hudStateKeys[slot] = stateKey;
            // Prepared results validate the live radius and full sampling region themselves.
            // A resized widget must not invalidate already prepared neighbours from the same source.
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
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) return;
        try (Render2DState state = Render2DState.join()) {
            try {
                renderGaussianInternal(shape, overlaySource);
            } finally {
                restoreRenderState(true);
            }
        }
    }

    private void renderGaussianInternal(ShapeProperties shape, boolean overlaySource) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepare2DFramebuffers(client, false, false)) {
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
        float alpha = RenderSystem.getShaderColor()[3];
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, slot.framebuffer.getColorAttachment());
        RenderSystem.setShaderTexture(1, slot.framebuffer.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        if (shader != null) {
            shader.getUniformOrDefault("Size").set(width, height);
            shader.getUniformOrDefault("Radius").set(round);
            shader.getUniformOrDefault("RectMask").set(maxCornerRadius(round) <= 0.001F ? 1 : 0);
            shader.getUniformOrDefault("Smoothness").set(softness);
            shader.getUniformOrDefault("BlurRadius").set(0.0F);
            shader.getUniformOrDefault("BlurMode").set(0);
            shader.getUniformOrDefault("TintColor").set(0.0F, 0.0F, 0.0F, 0.0F);
            shader.getUniformOrDefault("FrameMix").set(1.0F);
            shader.getUniformOrDefault("BlurScreenSize").set((float) input.textureWidth, (float) input.textureHeight);
            drawShape(shape, matrix4f, softness, color);
        }
        RenderSystem.enableDepthTest();
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
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
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int blurColor = (MathHelper.clamp(Math.round(clampedOpacity * 255.0F), 0, 255) << 24) | 0x00FFFFFF;
        drawEngine.quad(matrix, buffer, x, y, width, height, blurColor);

        RenderSystem.setShaderTexture(0, nametagInput.getColorAttachment());
        RenderSystem.setShaderTexture(1, nametagInput.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        if (shader != null) {
            Theme theme = Theme.getInstance();
            int blurMode = theme.getHudBlurMode();
            shader.getUniformOrDefault("Size").set(Math.max(1.0f, width), Math.max(1.0f, height));
            shader.getUniformOrDefault("Radius").set(ZERO_ROUND);
            shader.getUniformOrDefault("RectMask").set(1);
            shader.getUniformOrDefault("Smoothness").set(0.001f);
            shader.getUniformOrDefault("BlurRadius").set(Math.max(0.0f, quality) * theme.getHudBlurRadiusMultiplier());
            shader.getUniformOrDefault("BlurMode").set(blurMode);
            shader.getUniformOrDefault("FrameMix").set(1.0F);
            shader.getUniformOrDefault("BlurScreenSize").set((float) nametagInput.textureWidth, (float) nametagInput.textureHeight);
            setTintUniform(shader, tintColor);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } else {
            buffer.end();
        }

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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        drawWorldFallbackRectContents(matrix, x, y, width, height, tintColor);
        restoreRenderState(true);
    }

    private void drawWorldFallbackRectContents(Matrix4f matrix, float x, float y, float width, float height, int tintColor) {
        if (((tintColor >>> 24) & 0xFF) == 0 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        BufferBuilder fallback = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(matrix, fallback, x, y, width, height, tintColor);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferRenderer.drawWithGlobalProgram(fallback.end());
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
        if (shapes == null || shapes.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) return;
        try (Render2DState state = Render2DState.join()) {
            boolean complete = false;
            try {
                renderCachedBatchInternal(shapes);
                complete = true;
            } finally {
                if (!complete || !hudBatchMode) {
                    restoreRenderState(true);
                    hudBatchStateApplied = false;
                    hudBatchMaskShader = null;
                }
            }
        }
    }

    private void renderCachedBatchInternal(List<ShapeProperties> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepare2DFramebuffers(client, true, true)) {
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

        PreparedBlurState batchState = resolvePreparedBatchBlurState(client, shapes);
        ShaderProgram shader = null;
        PreparedBlurState activeState = null;
        for (ShapeProperties shape : shapes) {
            if (shape == null) {
                continue;
            }
            if (batchState != null && computeHudGaussianRegion(client, shape, 0.0f) == null) {
                continue;
            }
            PreparedBlurState preparedState = batchState != null
                    ? batchState
                    : resolvePreparedBlurState(client, shape, true);
            if (preparedState == null) {
                continue;
            }
            if (!preparedState.matches(activeState)) {
                RenderSystem.setShaderTexture(0, preparedState.sourceTexture());
                RenderSystem.setShaderTexture(1, preparedState.blurMode() == 5 ? input.getColorAttachment() : preparedState.sourceTexture());
                shader = RenderSystem.setShader(MASK_SHADER_KEY);
                hudBatchMaskShader = shader;
                if (shader == null) {
                    if (!useHudBatch) {
                        restoreRenderState(true);
                    }
                    return;
                }
                activeState = preparedState;
                setPreparedMaskUniforms(shader, preparedState);
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
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);

        shader.getUniformOrDefault("Size").set(width, height);
        shader.getUniformOrDefault("Radius").set(round);
        shader.getUniformOrDefault("RectMask").set(maxCornerRadius(round) <= 0.001F ? 1 : 0);
        shader.getUniformOrDefault("Smoothness").set(softness);
        drawShape(shape, matrix4f, softness, color);
    }

    private void render(ShapeProperties shape, boolean cacheFrame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) return;
        try (Render2DState state = Render2DState.join()) {
            boolean complete = false;
            try {
                render2DInternal(shape, cacheFrame);
                complete = true;
            } finally {
                if (!complete || !cacheFrame || !hudBatchMode) {
                    restoreRenderState(true);
                    hudBatchStateApplied = false;
                    hudBatchMaskShader = null;
                }
            }
        }
    }

    private void render2DInternal(ShapeProperties shape, boolean cacheFrame) {
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

        if (!prepare2DFramebuffers(client, cacheFrame, true)) {
            return;
        }

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

        if (!renderPreparedShape(shape, cacheFrame)) {
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
        float alpha = RenderSystem.getShaderColor()[3];
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f size = matrix4f.getScale(scratchScale).mul(scale);
        Vector4f round = scratchRound.set(shape.getRound()).mul(size.y);
        float softness = Math.max(0.001F, shape.getSoftness());
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        int color = ColorUtil.multAlpha(shape.getColor().x, alpha);

        RenderSystem.setShaderTexture(0, preparedState.sourceTexture());
        RenderSystem.setShaderTexture(1, preparedState.blurMode() == 5 ? input.getColorAttachment() : preparedState.sourceTexture());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        hudBatchMaskShader = shader;
        if (shader == null) {
            return false;
        }
        shader.getUniformOrDefault("Size").set(width, height);
        shader.getUniformOrDefault("Radius").set(round);
        shader.getUniformOrDefault("RectMask").set(maxCornerRadius(round) <= 0.001F ? 1 : 0);
        shader.getUniformOrDefault("Smoothness").set(softness);
        setPreparedMaskUniforms(shader, preparedState);
        drawShape(shape, matrix4f, softness, color);
        return true;
    }

    // Only shared inside an uninterrupted explicit batch, never cached across HUD/menu/world draws.
    private void setPreparedMaskUniforms(ShaderProgram shader, PreparedBlurState state) {
        shader.getUniformOrDefault("BlurRadius").set(state.blurRadius());
        shader.getUniformOrDefault("BlurMode").set(state.blurMode());
        shader.getUniformOrDefault("TintColor").set(0.0F, 0.0F, 0.0F, 0.0F);
        shader.getUniformOrDefault("FrameMix").set(1.0F);
        shader.getUniformOrDefault("BlurScreenSize").set((float) input.textureWidth, (float) input.textureHeight);
    }

    /** Reserve tessellator memory only once the shader/uniforms are ready. */
    private void drawShape(ShapeProperties shape, Matrix4f matrix, float softness, int color) {
        // Offscreen filters and preceding rectangles may have changed these since the batch began.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION_COLOR);
        try {
            drawEngine.quad(matrix, builder, shape.getX() - softness / 2, shape.getY() - softness / 2,
                    shape.getWidth() + softness, shape.getHeight() + softness, color);
            try (BuiltBuffer built = builder.end()) {
                BufferRenderer.drawWithGlobalProgram(built);
            }
        } catch (RuntimeException | Error failure) {
            Render2DBuffers.discardImmediate();
            throw failure;
        }
    }

    private boolean prepare2DFramebuffers(MinecraftClient client, boolean cache, boolean refresh) {
        if (input == null || input.textureWidth != Math.max(1, client.getWindow().getFramebufferWidth())
                || input.textureHeight != Math.max(1, client.getWindow().getFramebufferHeight())) {
            try (Render2DState state = Render2DState.capture()) {
                return prepareFramebuffers(client, cache, refresh);
            } catch (RuntimeException | Error failure) {
                cleanup();
                throw failure;
            }
        }
        return prepareFramebuffers(client, cache, refresh);
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
        Framebuffer sourceInput = useHudCache ? input : immediateInput;
        if (sourceInput == null) return null;
        int sourceTexture = sourceInput.getColorAttachment();

        BlurRegion visibleRegion = computeHudGaussianRegion(client, shape, 0.0f);
        if (visibleRegion == null) {
            return null;
        }

        if (useHudCache && blurMode == 2 && blurRadius > 0.0f && cacheFrameReadyForHud()) {
            BlurRegion blurRegion = visibleRegion.expand(hudGaussianRadius,
                    input.textureWidth, input.textureHeight);
            hudBlurPlanner.remember(Math.min(hudGaussianRadius, 24.0F), plannerRect(blurRegion));
            // Keep fine taps within one pixel: wide isolated taps look like shifted copies.
            if (hudGaussianRadius <= HUD_FINE_KAWASE_THRESHOLD) {
                return new PreparedBlurState(sourceTexture, 4, hudGaussianRadius / HUD_FINE_KAWASE_THRESHOLD);
            }
            prepareHudBlurGroups();
            Framebuffer prepared = applyOptimizedHudKawaseBlur(client, hudGaussianRadius, blurRegion);
            if (prepared != null) {
                sourceTexture = prepared.getColorAttachment();
                effectiveBlurMode = hudGaussianRadius < 12.0F ? 5 : 0;
                effectiveBlurRadius = effectiveBlurMode == 5 ? hudGaussianRadius : 0.0F;
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
        long regionArea = 0L;
        for (ShapeProperties shape : shapes) {
            if (shape == null) continue;
            float radius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
            // A clean/zero-radius shape must not inherit its neighbours' prepared blur.
            if (radius <= 0.0f) return null;
            if (sharedRadius < 0.0f) {
                sharedRadius = radius;
            } else if (Math.abs(sharedRadius - radius) >= 0.01f) {
                return null;
            }
            BlurRegion region = computeHudGaussianRegion(client, shape, radius * HUD_GAUSSIAN_STRENGTH_MULTIPLIER);
            if (region != null) {
                union = union == null ? region : unionBlurRegions(union, region);
                regionArea += (long) region.width * region.height;
            }
        }
        if (sharedRadius <= 0.0f || union == null) {
            return null;
        }
        // Sparse HUDs can make a shared bounding box far more expensive than separate scissored passes.
        if ((long) union.width * union.height > regionArea) return null;

        float hudRadius = sharedRadius * HUD_GAUSSIAN_STRENGTH_MULTIPLIER;
        hudBlurPlanner.remember(Math.min(hudRadius, 24.0F), plannerRect(union));
        if (hudRadius <= HUD_FINE_KAWASE_THRESHOLD) {
            return new PreparedBlurState(input.getColorAttachment(), 4, hudRadius / HUD_FINE_KAWASE_THRESHOLD);
        }
        prepareHudBlurGroups();
        Framebuffer prepared = applyOptimizedHudKawaseBlur(client, hudRadius, union);
        if (prepared == null) {
            return null;
        }
        return new PreparedBlurState(prepared.getColorAttachment(), hudRadius < 12.0F ? 5 : 0,
                hudRadius < 12.0F ? hudRadius : 0.0F);
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
            input = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
            input.setTexFilter(GL11C.GL_LINEAR);
            menuInput = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
            menuInput.setTexFilter(GL11C.GL_LINEAR);
            hudHalfInput = new SimpleFramebuffer(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2), false);
            hudHalfInput.setTexFilter(GL11C.GL_LINEAR);
            nametagInput = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
            nametagInput.setTexFilter(GL11C.GL_LINEAR);
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
            // The menu / HUD cache slots are NOT allocated here - see
            // ensureBlurSlots() / acquireHudBlurSlot(). Menu slots remain full resolution;
            // HUD results are now half resolution. Previously both groups reserved 8 full-size targets
            // (~66 MB at 1080p, ~118 MB at 1440p, ~265 MB at 4K). Reserving
            // both up front charged that to every session, including ones
            // that only ever use HUD blur or only ever open the menu.
            resized = true;
        } else if (input.textureWidth != framebufferWidth || input.textureHeight != framebufferHeight) {
            input.resize(framebufferWidth, framebufferHeight);
            input.setTexFilter(GL11C.GL_LINEAR);
            if (immediateInput != null) {
                immediateInput.resize(framebufferWidth, framebufferHeight);
                immediateInput.setTexFilter(GL11C.GL_LINEAR);
            }
            menuInput.resize(framebufferWidth, framebufferHeight);
            menuInput.setTexFilter(GL11C.GL_LINEAR);
            hudHalfInput.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
            hudHalfInput.setTexFilter(GL11C.GL_LINEAR);
            nametagInput.resize(framebufferWidth, framebufferHeight);
            nametagInput.setTexFilter(GL11C.GL_LINEAR);
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
            // Unused slots are allocated at the new size on demand.
            for (MenuBlurSlot slot : menuBlurSlots) {
                if (slot == null) {
                    continue;
                }
                slot.framebuffer.resize(framebufferWidth, framebufferHeight);
                slot.framebuffer.setTexFilter(GL11C.GL_LINEAR);
                slot.valid = false;
            }
            for (MenuBlurSlot slot : hudBlurSlots) {
                if (slot == null) {
                    continue;
                }
                slot.framebuffer.resize(Math.max(1, framebufferWidth / 2), Math.max(1, framebufferHeight / 2));
                slot.framebuffer.setTexFilter(GL11C.GL_LINEAR);
                slot.valid = false;
            }
            menuBlurCurrentValid = false;
            menuBlurPreviousValid = false;
            menuBlurRegion = null;
            // Popup snapshot is resized lazily in captureMenuOverlayFrame();
            // just mark it stale so nothing blurs a mismatched copy.
            menuOverlayValid = false;
            hudInputValid = false;
            hudHalfInputRevision = -1L;
            hudBlurPlanner.reset();
            invalidateHudKawaseCache();
            resized = true;
        }

        if (input == null || menuInput == null || hudHalfInput == null || nametagInput == null || ping == null || pong == null) {
            return false;
        }
        // menuBlurSlots / hudBlurSlots are deliberately not checked here:
        // they are created lazily at the point of use.
        if (halfA == null || halfB == null || quarterA == null || quarterB == null) {
            return false;
        }

        if (cacheFrame) {
            if (cachedFramePrepared && !resized) {
                // Widget state can change after the clean pre-HUD capture. Invalidate its results,
                // but never re-capture already composited Phaze HUD pixels in this same frame.
                if (forceHudRefresh) {
                    invalidateHudKawaseCache();
                    forceHudRefresh = false;
                }
                return true;
            }
            long now = System.nanoTime();
            // Opening a GUI can temporarily leave the main framebuffer in a
            // cleared/intermediate state. Re-capturing it makes every HUD
            // blur mask flash black for one frame. Keep the last valid world
            // snapshot while a GUI is open; it is both stable and cheaper.
            boolean guiOpen = client.currentScreen != null;
            // Camera/world ticks cannot identify unchanged pixels: entities, particles and animated
            // textures keep moving with a stationary camera. Refresh on a phase-preserving clock.
            boolean needsCapture = hudRefreshClock.shouldCapture(hudInputValid, resized,
                    guiOpen, stableHudCapturePoint, now);
            if (needsCapture) {
                captureHudInput(client, framebufferWidth, framebufferHeight);
                hudRefreshClock.captured(now);
            }
            if (forceHudRefresh) {
                if (!needsCapture) invalidateHudKawaseCache();
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
        // Immediate icon/panel blur samples previously submitted GUI pixels. It must never replace
        // the clean cached HUD source: fine-kernel misses and non-Kawase modes still sample that source.
        if (immediateInput == null) {
            try (Render2DState state = Render2DState.capture()) {
                immediateInput = new SimpleFramebuffer(framebufferWidth, framebufferHeight, false);
                immediateInput.setTexFilter(GL11C.GL_LINEAR);
                captureFramebufferInput(client, immediateInput, framebufferWidth, framebufferHeight, GL11C.GL_NEAREST, null);
            }
        } else {
            captureFramebufferInput(client, immediateInput, framebufferWidth, framebufferHeight, GL11C.GL_NEAREST, null);
        }
        dualKawasePrepared = false;
        preparedHudGaussianRegionCount = 0;
    }

    private void captureMenuInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        captureFramebufferInput(client, menuInput, framebufferWidth, framebufferHeight, GL11C.GL_NEAREST, null);
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
        try (Render2DState state = Render2DState.capture()) {
            captureMenuOverlayFrameInternal();
        }
    }

    private void captureMenuOverlayFrameInternal() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        // Menu geometry is queued in the shared BatchedRectangle buffer; it
        // has to land in the framebuffer before we can copy it out.
        vorga.phazeclient.api.system.shape.batched.BatchedRectangle.flushIfBatching();
        if (!prepare2DFramebuffers(client, false, false)) {
            return;
        }

        int width = Math.max(1, client.getWindow().getFramebufferWidth());
        int height = Math.max(1, client.getWindow().getFramebufferHeight());

        // Allocated on demand: only sessions that actually open a popup pay
        // for this full-resolution target.
        if (menuOverlayInput == null) {
            menuOverlayInput = new SimpleFramebuffer(width, height, false);
            menuOverlayInput.setTexFilter(GL11C.GL_LINEAR);
        } else if (menuOverlayInput.textureWidth != width || menuOverlayInput.textureHeight != height) {
            menuOverlayInput.resize(width, height);
            menuOverlayInput.setTexFilter(GL11C.GL_LINEAR);
        }

        captureFramebufferInput(client, menuOverlayInput, width, height, GL11C.GL_NEAREST, null);
        menuOverlayRevision++;
        menuOverlayValid = true;
    }

    private void captureHudInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        // A failed/partial snapshot must not expose old revision metadata over new pixels.
        hudInputValid = false;
        cachedFramePrepared = false;
        invalidateHudKawaseCache();
        dualKawasePrepared = false;
        preparedHudGaussianRegionCount = 0;
        // Fine blur only needs the full-resolution source. Prepare the half image lazily from
        // this clean snapshot, never from a main FBO that may already contain our own HUD.
        captureFramebufferInput(client, input, framebufferWidth, framebufferHeight, GL11C.GL_NEAREST, null);
        hudInputValid = true;
        hudInputRevision++;
    }

    private void captureNametagInput(MinecraftClient client, int framebufferWidth, int framebufferHeight) {
        captureFramebufferInput(client, nametagInput, framebufferWidth, framebufferHeight, GL11C.GL_NEAREST, null);
    }

    private void captureFramebufferInput(
            MinecraftClient client,
            Framebuffer target,
            int framebufferWidth,
            int framebufferHeight,
            int filter,
            Framebuffer halfTarget
    ) {
        // When a HUD batch capture is active, mc.getFramebuffer() is redirected
        // to the HUD FBO by MinecraftClientFramebufferMixin. We need the REAL
        // main framebuffer here to read the world content for the blur backdrop.
        Framebuffer framebuffer = HudBuffer.activeCaptureTarget >= 0
                ? vorga.phazeclient.api.system.hud.BatchedHudBuffer.INSTANCE.getRealMainFramebuffer()
                : client.getFramebuffer();
        if (framebuffer == null) {
            framebuffer = client.getFramebuffer();
        }
        int read = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        // glBlitFramebuffer is scissor-gated; the world snapshot must be complete.
        try {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target.fbo);
        GL30C.glBlitFramebuffer(
                0,
                0,
                framebufferWidth,
                framebufferHeight,
                0,
                0,
                target.textureWidth,
                target.textureHeight,
                GL30C.GL_COLOR_BUFFER_BIT,
                filter
        );
        if (halfTarget != null) {
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, halfTarget.fbo);
            GL30C.glBlitFramebuffer(0, 0, framebufferWidth, framebufferHeight,
                    0, 0, halfTarget.textureWidth, halfTarget.textureHeight,
                    GL30C.GL_COLOR_BUFFER_BIT, GL11C.GL_LINEAR);
        }
        } finally {
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            if (scissor) GlStateManager._enableScissorTest();
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
        try (Render2DState state = Render2DState.capture()) {
        int w = sourceInput.textureWidth;
        int h = sourceInput.textureHeight;
        // Keep radius continuous to avoid abrupt jumps on the HUD slider.
        float quantizedRadius = MathHelper.clamp(blurRadius, 0.0f, 24.0f);
        ShaderProgram shader = RenderSystem.setShader(DUAL_KAWASE_SHADER_KEY);
        if (shader == null) {
            return false;
        }

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
        blitColorRegion(halfB, output, region, GL11C.GL_LINEAR);

        dualKawasePrepared = true;
        lastDualKawaseRadius = quantizedRadius;
        lastDualKawaseWidth = w;
        lastDualKawaseHeight = h;
        return true;
        }
    }

    private void blitColorRegion(Framebuffer source, Framebuffer target, BlurRegion region, int filter) {
        BlurRegion sourceRegion = scaleBlurRegion(region, source.textureWidth, source.textureHeight);
        BlurRegion targetRegion = scaleBlurRegion(region, target.textureWidth, target.textureHeight);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, source.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target.fbo);
        GL30C.glBlitFramebuffer(
                sourceRegion.x,
                sourceRegion.y,
                sourceRegion.x + sourceRegion.width,
                sourceRegion.y + sourceRegion.height,
                targetRegion.x,
                targetRegion.y,
                targetRegion.x + targetRegion.width,
                targetRegion.y + targetRegion.height,
                GL30C.GL_COLOR_BUFFER_BIT,
                filter
        );
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
        if (hudHalfInput == null || quarterA == null || quarterB == null || region == null) {
            return null;
        }

        float radius = MathHelper.clamp(blurRadius, 0.0f, 24.0f);
        BlurRegion writeRegion = hudWriteRegion(region);
        int slotIndex = hudBlurCache.select(2, radius, region, writeRegion, hudInputRevision);
        MenuBlurSlot slot = acquireHudBlurSlot(slotIndex);
        if (hudBlurCache.selectedPrepared) {
            return slot.framebuffer;
        }
        try (Render2DState state = Render2DState.capture()) {
        ShaderProgram shader = RenderSystem.setShader(DUAL_KAWASE_SHADER_KEY);
        if (shader == null) {
            return null;
        }
        ensureHudHalfInput();
        // Drop affected metadata before any write, so a partially failed refresh cannot leave a hit.
        hudBlurCache.discardOverlapping(slotIndex, writeRegion);
        runHudKawasePasses(shader, slot.framebuffer, radius, List.of(region));
        slot.valid = false;
        hudBlurCache.remember(slotIndex, 2, radius, region, writeRegion);
        return slot.framebuffer;
        }
    }

    /** Prepare known layout groups once, before their masks; hints never bypass live cache validation. */
    private void prepareHudBlurGroups() {
        if (hudGroupsPrepared) return;
        hudGroupsPrepared = true;
        List<HudBlurPlanner.Group> groups = hudBlurPlanner.groups();
        if (groups.stream().noneMatch(group -> group.regions().size() > 1)) return;
        try (Render2DState state = Render2DState.capture()) {
            ShaderProgram shader = RenderSystem.setShader(DUAL_KAWASE_SHADER_KEY);
            if (shader == null) return;
            for (HudBlurPlanner.Group group : groups) {
                if (group.regions().size() <= 1) continue;
                List<BlurRegion> regions = new ArrayList<>(group.regions().size());
                BlurRegion union = null;
                BlurRegion writeUnion = null;
                boolean allPrepared = true;
                for (HudBlurPlanner.Rect rect : group.regions()) {
                    BlurRegion region = blurRegion(rect);
                    regions.add(region);
                    union = union == null ? region : unionBlurRegions(union, region);
                    BlurRegion write = hudWriteRegion(region);
                    writeUnion = writeUnion == null ? write : unionBlurRegions(writeUnion, write);
                    allPrepared &= hudBlurCache.hasPrepared(2, group.radius(), region, hudInputRevision);
                }
                if (allPrepared) continue;
                int index = hudBlurCache.select(2, group.radius(), union, writeUnion, hudInputRevision);
                MenuBlurSlot slot = acquireHudBlurSlot(index);
                ensureHudHalfInput();
                // The bounding box is used only for conservative slot conflict detection, NOT drawing.
                hudBlurCache.discardOverlapping(index, writeUnion);
                runHudKawasePasses(shader, slot.framebuffer, group.radius(), regions);
                for (BlurRegion region : regions) {
                    hudBlurCache.remember(index, 2, group.radius(), region, hudWriteRegion(region));
                }
            }
        }
    }

    private void ensureHudHalfInput() {
        if (hudHalfInputRevision == hudInputRevision) return;
        RenderSystem.disableScissor();
        blitColorRegion(input, hudHalfInput, null, GL11C.GL_LINEAR);
        hudHalfInputRevision = hudInputRevision;
    }

    private BlurRegion hudWriteRegion(BlurRegion region) {
        HudBlurPlanner.Rect half = HudBlurPlanner.scale(plannerRect(region), input.textureWidth,
                input.textureHeight, hudHalfInput.textureWidth, hudHalfInput.textureHeight);
        // Track the actual write guard, not only the widget's visible pixels.
        return blurRegion(HudBlurPlanner.scale(half, hudHalfInput.textureWidth, hudHalfInput.textureHeight,
                input.textureWidth, input.textureHeight));
    }

    private static HudBlurPlanner.Rect plannerRect(BlurRegion region) {
        return new HudBlurPlanner.Rect(region.x, region.y, region.width, region.height);
    }

    private static BlurRegion blurRegion(HudBlurPlanner.Rect rect) {
        return new BlurRegion(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private void runHudKawasePasses(ShaderProgram shader, Framebuffer output, float radius, List<BlurRegion> regions) {
        int passes = radius > 16.0F ? 2 : 1;
        float normalized = MathHelper.clamp(radius / 24.0F, 0.0F, 1.0F);
        float[] offsets = new float[passes];
        for (int i = 0; i < passes; i++) {
            // An extra same-size pass starts as an identity, instead of abruptly
            // adding a wide sampling ring at radius 16. At 24 the old kernel is retained.
            offsets[i] = i == 0 ? radius * 0.18F
                    : radius * (0.18F + 0.025F + normalized * 0.015F) * smoothHudRamp(radius, 16.0F, 24.0F);
        }
        List<HudBlurPlanner.Rect> outputRegions = new ArrayList<>(regions.size());
        for (BlurRegion region : regions) {
            outputRegions.add(HudBlurPlanner.scale(plannerRect(region), input.textureWidth, input.textureHeight,
                    output.textureWidth, output.textureHeight));
        }
        List<List<HudBlurPlanner.Rect>> quarterRegions = new ArrayList<>(passes + 1);
        for (int i = 0; i <= passes; i++) quarterRegions.add(null);
        quarterRegions.set(passes, requiredHudSource(outputRegions, output, quarterA, radius * 0.10F, false));
        for (int i = passes - 1; i >= 0; i--) {
            quarterRegions.set(i, requiredHudSource(quarterRegions.get(i + 1), quarterA, quarterA, offsets[i], true));
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        GlStateManager._colorMask(true, true, true, true);
        runHudKawasePass(shader, hudHalfInput, quarterA, radius * 0.14F, true, quarterRegions.get(0));
        Framebuffer src = quarterA;
        Framebuffer dst = quarterB;
        for (int i = 0; i < passes; i++) {
            runHudKawasePass(shader, src, dst, offsets[i], true, quarterRegions.get(i + 1));
            Framebuffer swap = src;
            src = dst;
            dst = swap;
        }
        // Produce the cached half-resolution result directly. Mask sampling performs the final linear upscale.
        runHudKawasePass(shader, src, output, radius * 0.10F, false, outputRegions);
    }

    private static float smoothHudRamp(float radius, float start, float end) {
        float t = MathHelper.clamp((radius - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static List<HudBlurPlanner.Rect> requiredHudSource(List<HudBlurPlanner.Rect> regions,
            Framebuffer target, Framebuffer source, float offset, boolean downsample) {
        List<HudBlurPlanner.Rect> required = new ArrayList<>(regions.size());
        for (HudBlurPlanner.Rect region : regions) {
            required.add(HudBlurPlanner.requiredSource(region, target.textureWidth, target.textureHeight,
                    source.textureWidth, source.textureHeight, offset, downsample));
        }
        return required;
    }

    private void runHudKawasePass(ShaderProgram shader, Framebuffer source, Framebuffer target,
            float offset, boolean downsample, List<HudBlurPlanner.Rect> regions) {
        // No endWrite()/bind-to-zero between stages: the enclosing state scope restores the caller once.
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target.fbo);
        RenderSystem.viewport(0, 0, target.textureWidth, target.textureHeight);
        RenderSystem.setShaderTexture(0, source.getColorAttachment());
        shader.getUniformOrDefault("TexelSize").set(1.0F / source.textureWidth, 1.0F / source.textureHeight);
        shader.getUniformOrDefault("Offset").set(offset);
        shader.getUniformOrDefault("Downsample").set(downsample ? 1 : 0);
        BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION);
        try {
            for (HudBlurPlanner.Rect region : regions) {
                float left = region.x() * 2.0F / target.textureWidth - 1.0F;
                float bottom = region.y() * 2.0F / target.textureHeight - 1.0F;
                float right = (region.x() + region.width()) * 2.0F / target.textureWidth - 1.0F;
                float top = (region.y() + region.height()) * 2.0F / target.textureHeight - 1.0F;
                builder.vertex(left, bottom, 0);
                builder.vertex(right, bottom, 0);
                builder.vertex(right, top, 0);
                builder.vertex(left, top, 0);
            }
            try (BuiltBuffer built = builder.end()) {
                BufferRenderer.drawWithGlobalProgram(built);
            }
        } catch (RuntimeException | Error failure) {
            Render2DBuffers.discardImmediate();
            throw failure;
        }
    }

    private void invalidateHudKawaseCache() {
        hudBlurCache.invalidate();
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
     * Creates the full-resolution menu slot group on first use.
     *
     * <p>Menu acquisition creates its group here. HUD slots instead allocate
     * individually at half resolution in acquireHudBlurSlot(), so HUD-only
     * sessions do not pay for unused full-resolution menu targets.
     *
     * <p>Groups are never freed once created: reclaiming them would mean
     * re-allocating (and re-warming the cache) the next time the user opens
     * the menu, which is exactly the kind of hitch the cache exists to avoid.
     */
    private void ensureBlurSlots(MenuBlurSlot[] slots) {
        boolean complete = true;
        for (MenuBlurSlot slot : slots) complete &= slot != null;
        if (complete) return;

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

        try (Render2DState state = Render2DState.capture()) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) continue;
            SimpleFramebuffer framebufferCache = new SimpleFramebuffer(width, height, false);
            slots[i] = new MenuBlurSlot(framebufferCache);
            framebufferCache.setTexFilter(GL11C.GL_LINEAR);
        }
        }
    }

    private MenuBlurSlot acquireHudBlurSlot(int index) {
        // Disjoint radii can coexist in one half-resolution texture. Only allocate another target
        // when overlapping output footprints require it, keeping the existing four-target ceiling.
        MenuBlurSlot slot = hudBlurSlots[index];
        if (slot != null) return slot;
        try (Render2DState state = Render2DState.capture()) {
            SimpleFramebuffer framebufferCache = new SimpleFramebuffer(hudHalfInput.textureWidth, hudHalfInput.textureHeight, false);
            slot = new MenuBlurSlot(framebufferCache);
            hudBlurSlots[index] = slot;
            framebufferCache.setTexFilter(GL11C.GL_LINEAR);
        }
        return slot;
    }

    private static BlurRegion unionBlurRegions(BlurRegion a, BlurRegion b) {
        int left = Math.min(a.x, b.x);
        int bottom = Math.min(a.y, b.y);
        int right = Math.max(a.x + a.width, b.x + b.width);
        int top = Math.max(a.y + a.height, b.y + b.height);
        return new BlurRegion(left, bottom, right - left, top - bottom);
    }

    private void runDualKawasePass(
            ShaderProgram shader,
            Framebuffer source,
            Framebuffer target,
            float offset,
            boolean downsample,
            BlurRegion region
    ) {
        target.beginWrite(false);
        try {
        RenderSystem.viewport(0, 0, target.textureWidth, target.textureHeight);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, source.getColorAttachment());
        shader.getUniformOrDefault("TexelSize").set(1.0F / source.textureWidth, 1.0F / source.textureHeight);
        shader.getUniformOrDefault("Offset").set(offset);
        shader.getUniformOrDefault("Downsample").set(downsample ? 1 : 0);
        BlurRegion targetRegion = scaleBlurRegion(region, target.textureWidth, target.textureHeight);
        RenderSystem.enableScissor(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height);
        drawBlurFullscreenQuad();
        } finally {
            RenderSystem.disableScissor();
            target.endWrite();
        }
    }

    private void drawBlurFullscreenQuad() {
        BufferBuilder builder = Render2DBuffers.begin(VertexFormats.POSITION);
        try {
            builder.vertex(-1, -1, 0);
            builder.vertex(1, -1, 0);
            builder.vertex(1, 1, 0);
            builder.vertex(-1, 1, 0);
            try (BuiltBuffer built = builder.end()) {
                BufferRenderer.drawWithGlobalProgram(built);
            }
        } catch (RuntimeException | Error failure) {
            Render2DBuffers.discardImmediate();
            throw failure;
        }
    }

    public float getPlayerSpeed(MinecraftClient client) {
        if (client == null || client.player == null) {
            return 0.0f;
        }
        if (worldSpaceSpeedPrepared) {
            return cachedPlayerSpeed;
        }
        worldSpaceSpeedPrepared = true;

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
        private boolean contains(BlurRegion other) {
            return other.x >= x && other.y >= y
                    && other.x + other.width <= x + width && other.y + other.height <= y + height;
        }

        private boolean intersects(BlurRegion other) {
            return x < other.x + other.width && other.x < x + width
                    && y < other.y + other.height && other.y < y + height;
        }

        private BlurRegion expand(float radius, int framebufferWidth, int framebufferHeight) {
            // The visible region already includes the two-pixel softness guard. Expanding its
            // clipped bounds matches recomputing the transform with the additional blur margin.
            int margin = Math.max(0, (int) Math.ceil(radius));
            int left = Math.max(0, x - margin);
            int bottom = Math.max(0, y - margin);
            int right = Math.min(framebufferWidth, x + width + margin);
            int top = Math.min(framebufferHeight, y + height + margin);
            return new BlurRegion(left, bottom, right - left, top - bottom);
        }
    }

    /** Pure CPU metadata; no GL resources, world ticks, or per-widget wall-clock queries. */
    private static final class HudBlurCache {
        private final BlurRegion[][] regions = new BlurRegion[MENU_BLUR_CACHE_SLOTS][MAX_PREPARED_HUD_KAWASE_REGIONS];
        private final BlurRegion[][] writes = new BlurRegion[MENU_BLUR_CACHE_SLOTS][MAX_PREPARED_HUD_KAWASE_REGIONS];
        private final int[][] modes = new int[MENU_BLUR_CACHE_SLOTS][MAX_PREPARED_HUD_KAWASE_REGIONS];
        private final float[][] radii = new float[MENU_BLUR_CACHE_SLOTS][MAX_PREPARED_HUD_KAWASE_REGIONS];
        private final int[] counts = new int[MENU_BLUR_CACHE_SLOTS];
        private final long[] lastUse = new long[MENU_BLUR_CACHE_SLOTS];
        private long revision = Long.MIN_VALUE;
        private long sequence;
        private boolean selectedPrepared;

        private void invalidate() {
            revision = Long.MIN_VALUE;
            selectedPrepared = false;
            java.util.Arrays.fill(counts, 0);
            for (BlurRegion[] slot : regions) java.util.Arrays.fill(slot, null);
            for (BlurRegion[] slot : writes) java.util.Arrays.fill(slot, null);
        }

        private boolean contains(int slot, int mode, float radius, BlurRegion region) {
            for (int i = 0; i < counts[slot]; i++) {
                if (modes[slot][i] == mode && Float.floatToIntBits(radii[slot][i]) == Float.floatToIntBits(radius)
                        && regions[slot][i].contains(region)) return true;
            }
            return false;
        }

        private boolean hasPrepared(int mode, float radius, BlurRegion region, long sourceRevision) {
            if (revision != sourceRevision) return false;
            for (int slot = 0; slot < regions.length; slot++) {
                if (contains(slot, mode, radius, region)) return true;
            }
            return false;
        }

        private int select(int mode, float radius, BlurRegion region, BlurRegion write, long sourceRevision) {
            selectedPrepared = false;
            if (revision != sourceRevision) {
                invalidate();
                revision = sourceRevision;
            }
            int best = 0;
            int bestOverlaps = Integer.MAX_VALUE;
            for (int slot = 0; slot < regions.length; slot++) {
                if (contains(slot, mode, radius, region)) {
                    selectedPrepared = true;
                    lastUse[slot] = ++sequence;
                    return slot;
                }
                int overlaps = 0;
                for (int i = 0; i < counts[slot]; i++) {
                    if (write.intersects(writes[slot][i])) overlaps++;
                }
                if (overlaps == 0 && counts[slot] == regions[slot].length) overlaps = 1;
                // Pack non-conflicting results before opening an unused full-screen target.
                if (overlaps < bestOverlaps || (overlaps == bestOverlaps
                        && (overlaps == 0 ? counts[slot] > counts[best] : lastUse[slot] < lastUse[best]))) {
                    best = slot;
                    bestOverlaps = overlaps;
                }
            }
            lastUse[best] = ++sequence;
            return best;
        }

        private void discardOverlapping(int slot, BlurRegion write) {
            int kept = 0;
            for (int i = 0; i < counts[slot]; i++) {
                // Include the actual full-resolution write guard, not just the requested rectangle.
                // Even same-radius overlapping passes can have different intermediate edge pixels.
                if (write.intersects(writes[slot][i])) continue;
                regions[slot][kept] = regions[slot][i];
                writes[slot][kept] = writes[slot][i];
                modes[slot][kept] = modes[slot][i];
                radii[slot][kept++] = radii[slot][i];
            }
            if (kept < counts[slot]) {
                java.util.Arrays.fill(regions[slot], kept, counts[slot], null);
                java.util.Arrays.fill(writes[slot], kept, counts[slot], null);
            }
            counts[slot] = kept;
        }

        private void remember(int slot, int mode, float radius, BlurRegion region, BlurRegion write) {
            // Invalidation happens BEFORE drawing. Results within one prepared radius group
            // may overlap safely: all stages have freshly computed tap coverage.
            int kept = counts[slot];
            if (kept == regions[slot].length) {
                // Bounded metadata overflow evicts one oldest region, not all 32 prepared regions.
                System.arraycopy(regions[slot], 1, regions[slot], 0, --kept);
                System.arraycopy(writes[slot], 1, writes[slot], 0, kept);
                System.arraycopy(modes[slot], 1, modes[slot], 0, kept);
                System.arraycopy(radii[slot], 1, radii[slot], 0, kept);
            }
            regions[slot][kept] = region;
            writes[slot][kept] = write;
            modes[slot][kept] = mode;
            radii[slot][kept++] = radius;
            counts[slot] = kept;
        }
    }

    /** Phase-preserving throttle: 120 Hz does not degrade to 72 Hz on a 144 FPS render loop. */
    private static final class HudRefreshClock {
        private long interval;
        private long next;
        private boolean initialized;

        private HudRefreshClock(int fps) { setFps(fps); }

        private void setFps(int fps) {
            interval = 1_000_000_000L / Math.max(10, Math.min(360, fps));
            reset();
        }

        private void reset() { initialized = false; }

        private boolean shouldCapture(boolean valid, boolean resized, boolean guiOpen, boolean stable, long now) {
            return !valid || resized || ((!guiOpen || stable) && (!initialized || now - next >= 0L));
        }

        private void captured(long now) {
            if (!initialized || now - next < 0L) next = now + interval;
            else next = now + interval - (now - next) % interval;
            initialized = true;
        }
    }

    private static final class MenuBlurSlot {
        private final Framebuffer framebuffer;
        private long regionKey = Long.MIN_VALUE;
        /** Revision of the snapshot this slot's blur was produced from. */
        private long sourceRevision = Long.MIN_VALUE;
        private long lastRefreshNs = 0L;
        private long lastUseNs = 0L;
        private float blurRadius = -1.0f;
        private boolean valid = false;

        private MenuBlurSlot(Framebuffer framebuffer) {
            this.framebuffer = framebuffer;
        }
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

    private static void setTintUniform(ShaderProgram shader, int argb) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0f;
        float red = ((argb >>> 16) & 0xFF) / 255.0f;
        float green = ((argb >>> 8) & 0xFF) / 255.0f;
        float blue = (argb & 0xFF) / 255.0f;
        shader.getUniformOrDefault("TintColor").set(red, green, blue, alpha);
    }

    private static float maxCornerRadius(Vector4f radius) {
        return Math.max(Math.max(radius.x, radius.y), Math.max(radius.z, radius.w));
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
