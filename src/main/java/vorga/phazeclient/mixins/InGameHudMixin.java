package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.CoordinatesHud;
import vorga.phazeclient.implement.features.modules.hud.CpsHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.features.modules.hud.DirectionHud;
import vorga.phazeclient.implement.features.modules.hud.FpsHud;
import vorga.phazeclient.implement.features.modules.hud.KeystrokesHud;
import vorga.phazeclient.implement.features.modules.hud.MovementSpeedHud;
import vorga.phazeclient.implement.features.modules.hud.PingHud;
import vorga.phazeclient.implement.features.modules.hud.PotionHud;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardHud;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.SprintHud;
import vorga.phazeclient.implement.features.modules.hud.MemoryHud;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.hud.ServerAddressHud;
import vorga.phazeclient.implement.features.modules.hud.WailaHud;
import vorga.phazeclient.implement.features.modules.other.HealthIndicator;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.hud.TpsHud;
import vorga.phazeclient.implement.features.modules.other.Zoom;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.hud.Cooldowns;
import vorga.phazeclient.implement.features.modules.other.Crosshair;
import vorga.phazeclient.implement.features.modules.other.HealingHelper;
import vorga.phazeclient.implement.features.modules.other.HudOptimizer;
import vorga.phazeclient.implement.features.modules.other.ItemHighlighter;
import vorga.phazeclient.implement.features.modules.other.MaceIndicator;
import vorga.phazeclient.implement.features.modules.other.NickHider;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.features.modules.other.Saturation;
import vorga.phazeclient.implement.features.modules.hud.InventoryHud;
import vorga.phazeclient.implement.features.modules.hud.PlayerModelHud;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;
import vorga.phazeclient.helpers.ColorHelper;
import vorga.phazeclient.helpers.IntPoint;
import vorga.phazeclient.helpers.TextureHelper;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.api.feature.module.Module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    private static final float BASE_WIDTH = 60.0f;
    private static final float BASE_HEIGHT = 20.0f;
    private static final int HUD_TEXT_COLOR = 0xFFFFFFFF;
    private static final float HUD_TEXT_SIZE = 8.0f;
    private static final float HUD_TEXT_RENDER_Z = 1000.0f;
    private static final int HANDLE_COLOR = 0xFF72F7D4;
    private static final float BASE_HOVER_OUTLINE_THICKNESS = 1.0f;
    private static final float HUD_RENDER_Z = 400.0f;
    private static final float HANDLE_RENDER_Z = 450.0f;
    private static final float GUIDE_SNAP_RADIUS = 3.0f;
    private static final float GUIDE_FADE_SPEED = 14.0f;
    private static final int GUIDE_MAX_ALPHA = 140;
    private static final long HUD_TEXT_THROTTLE_MS = 50L;
    private static final Identifier DIRECTION_TRIANGLE_TEXTURE = Identifier.of("phaze", "textures/down_triangle.png");

    private static final int HUD_FPS = 0;
    private static final int HUD_CPS = 1;
    private static final int HUD_REACH = 2;
    private static final int HUD_SPRINT = 3;
    private static final int HUD_COORDINATES = 4;
    private static final int HUD_PING = 5;
    private static final int HUD_KEYSTROKES = 6;
    private static final int HUD_POTION = 7;
    private static final int HUD_DAY_COUNTER = 8;
    private static final int HUD_DIRECTION = 9;
    private static final int HUD_TAB = 10;
    private static final int HUD_NAMETAG = 11;
    private static final int HUD_TIME = 12;
    private static final int HUD_SESSION = 13;
    private static final int HUD_SCOREBOARD = 14;
    private static final int HUD_MEMORY = 15;
    private static final int HUD_COMBO = 16;
    private static final int HUD_SERVER_ADDRESS = 17;
    private static final int HUD_MOVEMENT_SPEED = 18;
    private static final int HUD_WAILA = 19;
    private static final int HUD_HEALTH_INDICATOR = 20;
    private static final int HUD_BATTLE_INFO = 21;
    private static final int HUD_CONSUMABLE = 22;
    private static final int HUD_TPS = 23;
    private static final int RECT_HUD_COUNT = 24;
    private static final int HUD_ARMOR_BLUR_SLOT = 15;
    private static final int KEYSTROKE_W = 0;
    private static final int KEYSTROKE_A = 1;
    private static final int KEYSTROKE_S = 2;
    private static final int KEYSTROKE_D = 3;
    private static final int KEYSTROKE_LMB = 4;
    private static final int KEYSTROKE_RMB = 5;
    private static final int KEYSTROKE_SPACE = 6;

    private static final boolean[] RECT_DRAGGING = new boolean[RECT_HUD_COUNT];
    private static final boolean[] RECT_RESIZING = new boolean[RECT_HUD_COUNT];
    private static final float[] RECT_DRAG_OFFSET_X = new float[RECT_HUD_COUNT];
    private static final float[] RECT_DRAG_OFFSET_Y = new float[RECT_HUD_COUNT];
    private static final float[] RECT_RESIZE_START_WIDTH = new float[RECT_HUD_COUNT];
    private static final float[] RECT_RESIZE_START_MOUSE_X = new float[RECT_HUD_COUNT];
    private static final float[] RECT_RESIZE_START_MOUSE_Y = new float[RECT_HUD_COUNT];
    private static final float[] RECT_HOVER_PROGRESS = new float[RECT_HUD_COUNT];
    private static final int[] RECT_BG_ANIMATED_COLOR = new int[RECT_HUD_COUNT];
    private static final boolean[] RECT_BG_COLOR_INITIALIZED = new boolean[RECT_HUD_COUNT];
    private static final float[] KEYSTROKE_PROGRESS = new float[7];
    private static final String[] HUD_TEXT_CACHE = new String[RECT_HUD_COUNT];
    private static final long[] HUD_TEXT_CACHE_TIME_MS = new long[RECT_HUD_COUNT];
    private static final int HUD_TEXT_WIDTH_CACHE_MAX = 512;
    private static final Map<String, Float> HUD_TEXT_WIDTH_CACHE = new LinkedHashMap<>(HUD_TEXT_WIDTH_CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
            return size() > HUD_TEXT_WIDTH_CACHE_MAX;
        }
    };
    private static List<String> coordinatesLinesCache = new ArrayList<>();
    private static String coordinatesBiomeNameCache = "";
    private static int coordinatesBiomeColorCache = 0xFFFF55;
    private static String coordinatesFacingCache = "";
    private static String coordinatesTopSignCache = "";
    private static String coordinatesBottomSignCache = "";
    private static List<ItemStack> armorStacksCache = new ArrayList<>();
    private static List<String> armorDurabilityTextsCache = new ArrayList<>();
    private static List<StatusEffectInstance> potionEffectsCache = new ArrayList<>();
    private static List<String> potionNamesCache = new ArrayList<>();
    private static List<String> potionDurationsCache = new ArrayList<>();
    private static boolean potionSampleCache = false;
    private static boolean potionCacheInitialized = false;

    private static boolean armorDragging = false;
    private static boolean armorResizing = false;
    private static float armorDragOffsetX = 0.0f;
    private static float armorDragOffsetY = 0.0f;
    private static float armorResizeStartScale = 1.0f;
    private static float armorResizeStartMouseX = 0.0f;
    private static float armorResizeStartMouseY = 0.0f;
    private static float armorHoverProgress = 0.0f;
    private static int armorAnimatedBackgroundColor = 0;
    private static boolean armorBackgroundColorInitialized = false;

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static boolean renderedThisFrame = false;
    private static boolean wasMouseDown = false;
    private static boolean wasLeftMouseDown = false;
    private static boolean wasRightMouseDown = false;
    private static long lastFrameNanos = -1L;
    private static float cachedFrameDeltaSeconds = 0.0f;
    private static float verticalGuideProgress = 0.0f;
    private static float horizontalGuideProgress = 0.0f;
    private static boolean showVerticalGuideThisFrame = false;
    private static boolean showHorizontalGuideThisFrame = false;
    /** When true, blur HUDs are skipped in the current renderHudInternal call (batch FBO pass). */
    private static boolean inBatchPass = false;
    /** When true, only blur HUDs are rendered in the current renderHudInternal call (direct main-FB pass). */
    private static boolean inBlurPass = false;
    /**
     * Last observed {@link RectHudModule#hasActiveBackgroundBlur()} value per
     * HUD instance. Used to detect the exact frame a HUD migrates between the
     * batched-FBO pass and the direct-blur pass (or vice-versa) so we can
     * force-invalidate {@link BatchedHudBuffer} and avoid the 1-frame
     * disappear / black flash the user reported:
     *
     * <ul>
     *   <li>Blur turned ON: the HUD was inside the cached FBO this frame
     *       but is excluded from the next Pass 1 because it now belongs to
     *       Pass 2. Without invalidation the cache keeps blitting the
     *       no-blur version while Pass 2 also draws the blurred version on
     *       top - briefly stamping two copies until the cache naturally
     *       expires (up to {@code 1000/refreshRate} ms).</li>
     *   <li>Blur turned OFF: the HUD just moved from Pass 2 (direct draw)
     *       into Pass 1 (cache). Pass 1 may not run this frame because the
     *       cache is still fresh from before the toggle, so the HUD is
     *       absent from the cache AND skipped by Pass 2 -> 1 frame of
     *       nothing.</li>
     * </ul>
     *
     * <p>Identity-keyed because every HUD module is a process-wide
     * singleton; we never want to coalesce two distinct HUD instances.
     * {@code Object} key (not {@code RectHudModule}) so the same path
     * also catches {@code ArmorHud}, which extends {@code Module}
     * directly but exposes the same {@code hasActiveBackgroundBlur}
     * surface.
     */
    private static final Map<Object, Boolean> PHAZE_LAST_BLUR_STATE = new IdentityHashMap<>();
    private static float directionDisplayYaw = Float.NaN;
    private static final long SESSION_START_MS = System.currentTimeMillis();

    @Unique
    private static void phaze$resetGuiRenderState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }


    @Inject(method = "render", at = @At("HEAD"))
    private void beginHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        renderedThisFrame = false;
        Blur.INSTANCE.beginCachedFrame();
    }

    /**
     * Replace the vanilla top-right status-effect overlay whenever the
     * client's own {@link PotionHud} module is enabled. Without this
     * the user would see two effect lists at once - the new draggable
     * Phaze HUD AND the stock icons baked into the top-right corner -
     * which is confusing and steals corner real estate. Cancelling at
     * HEAD short-circuits both the active-effect iteration and the
     * sprite/text rendering, so this is also slightly cheaper than
     * letting vanilla draw and then occluding on top.
     */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressVanillaStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (PotionHud.getInstance().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressVanillaScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        boolean hideByNoRender = noRender != null && noRender.isEnabled() && noRender.scoreboard.isValue();
        if (ScoreboardHud.getInstance().isEnabled() || hideByNoRender) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void phaze$suppressVanillaBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.bossBar.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCustomHudFallback(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hudHidden = client == null || client.options == null || client.options.hudHidden;

        if (!renderedThisFrame) {
            if (hudHidden) {
                BatchedHudBuffer.INSTANCE.invalidate();
            } else if (!HudOptimizer.getInstance().isEnabled()) {
                BatchedHudBuffer.INSTANCE.invalidate();
                renderHudInternal(context);
            } else {
                BatchedHudBuffer.INSTANCE.setTargetFps(HudOptimizer.getInstance().refreshRate.getInt());
                boolean chatEditing = client.currentScreen instanceof ChatScreen;

                // Pre-scan EVERY HUD's blur state BEFORE we decide whether
                // Pass 1 needs to refresh. The per-renderBufferedHud
                // trackBlurStateChange that lives inside Pass 1 / Pass 2
                // is too late to repair the THIS-frame visual: by the
                // time it invalidates the cache, blit() has already
                // stamped the previous frame's pre-flip HUD onto the
                // main framebuffer, while Pass 2 then redraws the
                // post-flip (blurred) version on top - that's the
                // "imprinted" ghost the user reports across every HUD
                // the first frame after a blur toggle (or any other
                // setting change that crosses the hasActiveBackgroundBlur
                // boundary). Walking the module list once here flips
                // BatchedHudBuffer to dirty BEFORE shouldRefresh is
                // sampled, so the cache is rebuilt on this frame and
                // blit shows fresh content matching what Pass 2 draws.
                phaze$prescanBlurStateFlips();

                boolean shouldRefresh = chatEditing || BatchedHudBuffer.INSTANCE.shouldRefresh(false);

                // Pass 1 — refresh frames only: render NON-blur HUDs into the
                // batched FBO at the throttled refresh rate.
                if (shouldRefresh) {
                    // Flush vanilla's pending deferred draws onto the
                    // REAL main framebuffer before we switch the bound
                    // framebuffer to our FBO. {@code InGameHud.render}
                    // is just {@code layeredDrawer.render}; each layer
                    // (renderChat in particular) queues
                    // {@code drawTextWithShadow} / {@code fill} into the
                    // shared {@code DrawContext} buffer but never
                    // flushes them itself - vanilla relies on a later
                    // implicit flush. Without this explicit flush our
                    // {@code context.draw()} inside the capture phase
                    // (below) would dump ALL of vanilla's accumulated
                    // chat draws into the captured FBO, where they get
                    // stamped onto the throttled cache. On subsequent
                    // frames the {@code BLIT} overlays that frozen chat
                    // state - producing the dark "phantom rows" the
                    // user reported above the live chat and the blink
                    // when the cache rebuilds at the throttle interval
                    // (30 ms by default) while real chat updates every
                    // frame. Flushing here pushes vanilla's chat onto
                    // the main framebuffer where it belongs, and the
                    // FBO ends up containing ONLY our own HUD widgets.
                    context.draw();
                    inBatchPass = true;
                    BatchedHudBuffer.INSTANCE.beginCapture();
                    renderHudInternal(context);
                    // Flush deferred DrawContext draws into the FBO before unbinding,
                    // otherwise vanilla flushes them later into the main framebuffer.
                    context.draw();
                    BatchedHudBuffer.INSTANCE.endCapture();
                    inBatchPass = false;
                }
                BatchedHudBuffer.INSTANCE.blit();

                // Pass 2 — every frame: render BLUR HUDs directly to the main
                // framebuffer so the blur tracks the moving world without
                // throttling, while non-blur HUDs stay batched.
                inBlurPass = true;
                renderHudInternal(context);
                inBlurPass = false;
            }
        }
        Blur.INSTANCE.endCachedFrame();

        // Render zoom level outside of HUD check (always fresh, not batched)
        if (client != null && client.options != null && !client.options.hudHidden) {
            // {@code Window.getScaledWidth/Height} returns the
            // GUI-scale-aware coordinate space that {@code drawText}
            // renders in. The previous version passed the raw
            // framebuffer pixels which only happened to land in the
            // right place at GUI scale 1; on scale 2/3/4 the text
            // drifted off-centre and below the visible area. The
            // user explicitly reported "show current zoom uezzhaet
            // на 2 и более". Using the scaled coords keeps the
            // overlay centred horizontally and 50px above the bottom
            // edge regardless of the active GUI scale.
            float screenWidth = client.getWindow().getScaledWidth();
            float screenHeight = client.getWindow().getScaledHeight();
            renderZoomLevel(context, client, screenWidth, screenHeight);
        }
    }

    private void renderHudInternal(DrawContext context) {
        // First call within the current frame is responsible for global state
        // updates that must run once per frame BEFORE per-HUD logic
        // (delta-time, click tracking, guide reset). A second call (the
        // blur-only pass after the batch blit) reuses the values computed
        // during the first call to avoid double-tracking clicks / halving
        // animation deltas.
        //
        // Last call is responsible for state updates that must run AFTER all
        // HUD drag/resize detection has completed (wasMouseDown edge tracking
        // and guide rendering). Because blur HUDs only run their drag logic in
        // pass 2 (inBlurPass), updating wasMouseDown in pass 1 would short-
        // circuit the press-edge detection for blur HUDs and prevent dragging
        // them while chat is open.
        boolean firstCallThisFrame = !renderedThisFrame;
        boolean lastCallThisFrame = !inBatchPass;
        renderedThisFrame = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null || client.options.hudHidden) {
            return;
        }

        if (!hasAnyHudEnabled()) {
            return;
        }

        float deltaSeconds;
        if (firstCallThisFrame) {
            long now = System.nanoTime();
            if (lastFrameNanos < 0L) {
                lastFrameNanos = now;
            }
            deltaSeconds = MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000.0f, 0.0f, 0.10f);
            lastFrameNanos = now;
            cachedFrameDeltaSeconds = deltaSeconds;
        } else {
            deltaSeconds = cachedFrameDeltaSeconds;
        }

        float guiScale = (float) client.getWindow().getScaleFactor();
        if (guiScale <= 0.0f) {
            guiScale = 1.0f;
        }
        float inverseGuiScale = 1.0f / guiScale;
        float screenWidth = client.getWindow().getWidth();
        float screenHeight = client.getWindow().getHeight();
        if (screenWidth <= 2.0f || screenHeight <= 2.0f) {
            return;
        }
        float screenCenterX = screenWidth * 0.5f;
        float screenCenterY = screenHeight * 0.5f;

        boolean chatEditing = client.currentScreen instanceof ChatScreen;
        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        boolean mouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean gameplayInput = client.currentScreen == null && client.player != null && client.world != null;
        if (firstCallThisFrame) {
            updateClicksPerSecond(mouseDown, rightMouseDown, gameplayInput);
            showVerticalGuideThisFrame = false;
            showHorizontalGuideThisFrame = false;
        }

        String fpsText = getCachedHudText(FpsHud.getInstance(), HUD_FPS, chatEditing, () -> {
            int fps = client.getCurrentFps();
            return FpsHud.getInstance().reverseOrder.isValue() ? fps + " FPS" : "FPS: " + fps;
        });
        final String fpsTextWrapped = wrapTextWithBrackets(fpsText, FpsHud.getInstance());
        renderBufferedHud(context, FpsHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, FpsHud.getInstance(), fpsTextWrapped, HUD_FPS,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(FpsHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        String cpsText = getCachedHudText(CpsHud.getInstance(), HUD_CPS, chatEditing, () -> {
            CpsHud cpsHud = CpsHud.getInstance();
            int leftCps = LEFT_CLICKS.size();
            int rightCps = RIGHT_CLICKS.size();
            
            if (cpsHud.rightClickCps.isValue()) {
                // Show both left and right CPS: *left* | *right*
                if (cpsHud.showCpsText.isValue()) {
                    if (cpsHud.reverseText.isValue()) {
                        return leftCps + " | " + rightCps + " CPS";
                    } else {
                        return "CPS: " + leftCps + " | " + rightCps;
                    }
                } else {
                    return leftCps + " | " + rightCps;
                }
            } else {
                // Show only left CPS
                if (cpsHud.showCpsText.isValue()) {
                    if (cpsHud.reverseText.isValue()) {
                        return leftCps + " CPS";
                    } else {
                        return "CPS: " + leftCps;
                    }
                } else {
                    return String.valueOf(leftCps);
                }
            }
        });
        final String cpsTextWrapped = wrapTextWithBrackets(cpsText, CpsHud.getInstance());
        renderBufferedHud(context, CpsHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, CpsHud.getInstance(), cpsTextWrapped, HUD_CPS,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(CpsHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        String reachText = getCachedHudText(ReachHud.getInstance(), HUD_REACH, chatEditing, () -> ReachHud.getInstance().getFormattedReach());
        final String reachTextWrapped = wrapTextWithBrackets(reachText, ReachHud.getInstance());
        renderBufferedHud(context, ReachHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, ReachHud.getInstance(), reachTextWrapped, HUD_REACH,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(ReachHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                        BASE_WIDTH + 6.0f, BASE_HEIGHT));
        renderBufferedHud(context, ArmorHud.getInstance(), chatEditing, () ->
                renderArmorHud(context, client, ArmorHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(ArmorHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        // InventoryHud routes through the same buffered pipeline so
        // HudOptimizer can park its 27-slot draw inside Pass 1's
        // throttled FBO cache instead of redrawing every frame -
        // the user reported it was the heaviest HUD on their
        // machine, and the icon pass (drawItem x 27 + overlay
        // submit) is exactly what the cache is meant to absorb.
        renderBufferedHud(context, InventoryHud.getInstance(), chatEditing, () ->
                phaze$drawInventoryHudGuts(context));
        String sprintText = getCachedHudText(SprintHud.getInstance(), HUD_SPRINT, chatEditing, () -> getSprintHudText(client));
        final String sprintTextWrapped = wrapTextWithBrackets(sprintText, SprintHud.getInstance());
        renderBufferedHud(context, SprintHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, SprintHud.getInstance(), sprintTextWrapped, HUD_SPRINT,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(SprintHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                        getTextHudBaseWidth(client, sprintTextWrapped), BASE_HEIGHT));
        renderBufferedHud(context, CoordinatesHud.getInstance(), chatEditing, () ->
                renderCoordinatesHud(context, client, CoordinatesHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(CoordinatesHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        renderBufferedHud(context, PingHud.getInstance(), chatEditing, () ->
                renderPingHud(context, client, PingHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(PingHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        renderBufferedHud(context, KeystrokesHud.getInstance(), chatEditing, () ->
                renderKeystrokesHud(context, client, KeystrokesHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(KeystrokesHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        renderBufferedHud(context, PotionHud.getInstance(), chatEditing, () ->
                renderPotionHud(context, client, PotionHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(PotionHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        String dayText = getCachedHudText(DayCounterHud.getInstance(), HUD_DAY_COUNTER, chatEditing, () -> getDayCounterText(client));
        final String dayTextWrapped = wrapTextWithBrackets(dayText, DayCounterHud.getInstance());
        renderBufferedHud(context, DayCounterHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, DayCounterHud.getInstance(), dayTextWrapped, HUD_DAY_COUNTER,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(DayCounterHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                        getTextHudBaseWidth(client, dayTextWrapped), BASE_HEIGHT));
        // Direction HUD temporarily disabled by request.
        // TabHud/NametagHud now affect vanilla TAB list and world nametags directly via dedicated mixins.
        String timeText = getTimeHudText(TimeHud.getInstance(), client);
        final String timeTextWrapped = wrapTextWithBrackets(timeText, TimeHud.getInstance());
        renderBufferedHud(context, TimeHud.getInstance(), chatEditing, () ->
                renderSimpleTextHud(context, client, TimeHud.getInstance(), timeTextWrapped, HUD_TIME, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(TimeHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        String sessionText = getSessionText(SessionTimeHud.getInstance());
        final String sessionTextWrapped = wrapTextWithBrackets(sessionText, SessionTimeHud.getInstance());
        renderBufferedHud(context, SessionTimeHud.getInstance(), chatEditing, () ->
                renderSessionTimeHud(context, client, SessionTimeHud.getInstance(), sessionTextWrapped, HUD_SESSION, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(SessionTimeHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        String memoryText = MemoryHud.getInstance().getMemoryText();
        final String memoryTextWrapped = wrapTextWithBrackets(memoryText, MemoryHud.getInstance());
        renderBufferedHud(context, MemoryHud.getInstance(), chatEditing, () ->
                renderMemoryHud(context, client, MemoryHud.getInstance(), memoryTextWrapped, HUD_MEMORY, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(MemoryHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        String tpsText = TpsHud.getInstance().getFormattedText();
        final String tpsTextWrapped = wrapTextWithBrackets(tpsText, TpsHud.getInstance());
        renderBufferedHud(context, TpsHud.getInstance(), chatEditing, () ->
                renderTpsHud(context, client, TpsHud.getInstance(), tpsTextWrapped, HUD_TPS, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(TpsHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        String comboText = ComboCounterHud.getInstance().getComboText();
        final String comboTextWrapped = wrapTextWithBrackets(comboText, ComboCounterHud.getInstance());
        renderBufferedHud(context, ComboCounterHud.getInstance(), chatEditing, () ->
                renderComboHud(context, client, ComboCounterHud.getInstance(), comboTextWrapped, HUD_COMBO, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(ComboCounterHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        String serverAddressText = ServerAddressHud.getInstance().getServerAddress();
        final String serverAddressTextWrapped = wrapTextWithBrackets(serverAddressText, ServerAddressHud.getInstance());
        renderBufferedHud(context, ServerAddressHud.getInstance(), chatEditing, () ->
                renderServerAddressHud(context, client, ServerAddressHud.getInstance(), serverAddressTextWrapped, HUD_SERVER_ADDRESS, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(ServerAddressHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        String speedText = getCachedHudText(MovementSpeedHud.getInstance(), HUD_MOVEMENT_SPEED, chatEditing, () -> {
            if (client.player == null) return "0.00 m/s";
            
            MovementSpeedHud module = MovementSpeedHud.getInstance();
            
            // Calculate speed using distance between current and previous position (like soup)
            double speed = Math.sqrt(client.player.squaredDistanceTo(new Vec3d(client.player.prevX, client.player.prevY, client.player.prevZ))) * 20.0;
            
            // If using ground speed only, calculate horizontal component
            if (module.onlyUseGroundSpeed.isValue()) {
                double dx = client.player.getX() - client.player.prevX;
                double dz = client.player.getZ() - client.player.prevZ;
                speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
            }
            
            // If standing still, show 0
            if (speed < 0.01) speed = 0.0;
            
            String value = module.getSpeedText(speed) + " m/s";
            // Optional "Speed:" prefix when the user wants the
            // labelled variant. Default OFF preserves the original
            // value-only display.
            return module.reverseOrder.isValue() ? "Speed: " + value : value;
        });
        final String speedTextWrapped = wrapTextWithBrackets(speedText, MovementSpeedHud.getInstance());
        // Pass an explicit baseWidth derived from the actual text - the
        // earlier 12-arg overload defaulted to {@code BASE_WIDTH} which
        // is sized for short numeric HUDs and clipped longer strings
        // such as "Speed: 1.23 m/s" once Reverse Order was enabled.
        renderBufferedHud(context, MovementSpeedHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, MovementSpeedHud.getInstance(), speedTextWrapped, HUD_MOVEMENT_SPEED,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(MovementSpeedHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                        getTextHudBaseWidth(client, speedTextWrapped), BASE_HEIGHT));

        ItemStack wailaIcon = getWailaIcon(client);
        String wailaText = getCachedHudText(WailaHud.getInstance(), HUD_WAILA, chatEditing, () -> {
            if (client.player == null) return "";
            return getWailaText(client);
        });
        final String wailaTextWrapped = wrapTextWithBrackets(wailaText, WailaHud.getInstance());
        renderBufferedHud(context, WailaHud.getInstance(), chatEditing, () ->
                renderWailaHud(context, client, WailaHud.getInstance(), wailaTextWrapped, wailaIcon, HUD_WAILA,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(WailaHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        // Health Indicator: lives in ModuleCategory.OTHER but extends
        // RectHudModule, so it rides the exact same renderRectHud
        // pipeline as every other draggable widget. The render lambda
        // returns early when there's nothing to show so an idle
        // indicator doesn't steal mouse interactions during normal
        // gameplay - in chatEditing mode we substitute the static
        // placeholder "20" so the rect stays visible and grabbable
        // even when the player isn't currently in a fight.
        final HealthIndicator healthIndicator = HealthIndicator.getInstance();
        renderBufferedHud(context, healthIndicator, chatEditing, () -> {
            String hpText;
            if (chatEditing) {
                hpText = healthIndicator.getPlaceholderText();
            } else {
                hpText = healthIndicator.getDisplayText();
                if (hpText.isEmpty()) {
                    return;
                }
            }
            String wrappedHpText = wrapTextWithBrackets(hpText, healthIndicator);
            renderRectHud(context, client, healthIndicator, wrappedHpText, HUD_HEALTH_INDICATOR,
                    chatEditing, mouseX, mouseY, mouseDown, getHudDelta(healthIndicator, chatEditing, deltaSeconds),
                    inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                    getTextHudBaseWidth(client, wrappedHpText), BASE_HEIGHT);
        });

        // Battle Info: same single-line text-rect path as the health
        // indicator. Per-frame {@code getDisplayText()} build is cheap
        // (just stitches a handful of cached String.format outputs)
        // so we don't bother caching at the {@code getCachedHudText}
        // layer; the rect-render lambda short-circuits on empty text
        // when no metric is enabled.
        // BattleInfo HUD removed in a later cleanup. The combo /
        // reach / damage rolling averages it provided are no longer
        // surfaced; standalone HUDs (Combo Counter, Reach, Cps)
        // remain available for users who want individual metrics.

        // Consumable: dedicated render path (icons-not-text) wired
        // through the same buffered pipeline so it gets the cached
        // FBO / direct-blur split for free. The custom path knows
        // how to size itself from the icon grid layout the module
        // computes.
        final vorga.phazeclient.implement.features.modules.hud.Consumable consumable =
                vorga.phazeclient.implement.features.modules.hud.Consumable.getInstance();
        renderBufferedHud(context, consumable, chatEditing, () ->
                renderConsumableHud(context, client, consumable, HUD_CONSUMABLE,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(consumable, chatEditing, deltaSeconds),
                        inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        renderBufferedHud(context, ScoreboardHud.getInstance(), chatEditing, () ->
                renderScoreboardHud(context, client, ScoreboardHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown,
                        deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

        if (lastCallThisFrame) {
            if (chatEditing) {
                verticalGuideProgress = approachExp(verticalGuideProgress, showVerticalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                horizontalGuideProgress = approachExp(horizontalGuideProgress, showHorizontalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                renderHudGuides(context, screenWidth, screenHeight, inverseGuiScale);
            } else {
                verticalGuideProgress = 0.0f;
                horizontalGuideProgress = 0.0f;
            }
            wasMouseDown = mouseDown;
        }
    }

    private void renderBufferedHud(DrawContext context, RectHudModule module, boolean chatEditing, Runnable renderLogic) {
        phaze$trackBlurStateChange(module, module.hasActiveBackgroundBlur());
        if (shouldSkipForCurrentPass(module.hasActiveBackgroundBlur())) return;
        renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), 60, chatEditing, renderLogic);
    }

    /**
     * Drops the batched-HUD cache the first frame a module's blur-state
     * flips. Otherwise the cache from the previous frame still believes
     * the HUD was in / out of Pass 1, and the visual lags by one full
     * refresh cycle - manifesting as the disappear/black-flash reported
     * by the user when they toggled blur on a HUD.
     *
     * <p>Cheap enough to call from every {@code renderBufferedHud}
     * invocation: the map probe is O(1) identity-hash, the put is a
     * single-slot rewrite, and the {@code !=} comparison is two
     * autoboxed-bool reads (the JIT will inline-eliminate the autobox
     * after warm-up).
     */
    private static void phaze$trackBlurStateChange(Object module, boolean current) {
        Boolean prev = PHAZE_LAST_BLUR_STATE.put(module, current);
        if (prev != null && prev != current) {
            BatchedHudBuffer.INSTANCE.invalidate();
        }
    }

    /**
     * Walks every registered HUD module BEFORE Pass 1 decides whether to
     * refresh the batched FBO, comparing the current
     * {@code hasActiveBackgroundBlur()} value against the last value that
     * was recorded in {@link #PHAZE_LAST_BLUR_STATE}. Any HUD whose blur
     * state has crossed the Pass-1/Pass-2 boundary forces an immediate
     * cache invalidation, so the very next {@code shouldRefresh} check
     * resolves to {@code true} and Pass 1 rebuilds the FBO with the
     * post-flip pass split.
     *
     * <p>The per-{@code renderBufferedHud} {@link #phaze$trackBlurStateChange}
     * call still exists as a defence in depth, but it runs AFTER the
     * frame's {@code blit()} - too late to repair this frame's visual.
     * The pre-scan here is what actually prevents the user-visible
     * "imprinted HUD" ghost on the first frame after any blur toggle:
     * without it, the stale cache (rendered with the old pass split)
     * blits to the screen and Pass 2 then redraws the post-flip HUD on
     * top, stamping every affected HUD twice for one frame.
     *
     * <p>Filtering by {@code instanceof} restricts the walk to the two
     * HUD types that surface {@code hasActiveBackgroundBlur}
     * ({@link RectHudModule} + {@link ArmorHud}). Modules without a
     * blur surface (e.g. {@code TabHud}, {@code NametagHud},
     * {@code Animations}) don't participate in the Pass split, so
     * skipping them costs nothing and keeps the per-frame walk cheap.
     */
    private static void phaze$prescanBlurStateFlips() {
        Main main = Main.getInstance();
        if (main == null) {
            return;
        }
        var provider = main.getModuleProvider();
        if (provider == null) {
            return;
        }
        for (Module module : provider.getModules()) {
            boolean current;
            if (module instanceof RectHudModule rectModule) {
                current = rectModule.hasActiveBackgroundBlur();
            } else if (module instanceof ArmorHud armorModule) {
                current = armorModule.hasActiveBackgroundBlur();
            } else {
                continue;
            }
            Boolean prev = PHAZE_LAST_BLUR_STATE.put(module, current);
            if (prev != null && prev != current) {
                BatchedHudBuffer.INSTANCE.invalidate();
            }
        }
    }

    private void renderBufferedHud(DrawContext context, ArmorHud module, boolean chatEditing, Runnable renderLogic) {
        phaze$trackBlurStateChange(module, module.hasActiveBackgroundBlur());
        if (shouldSkipForCurrentPass(module.hasActiveBackgroundBlur())) return;
        renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), 60, chatEditing, renderLogic);
    }

    /**
     * Two-pass filter: blur HUDs are skipped during the batched FBO pass, and
     * non-blur HUDs are skipped during the direct blur-only pass. In the
     * normal (un-batched) path both passes are inactive and all HUDs render.
     */
    private static boolean shouldSkipForCurrentPass(boolean hasBlur) {
        if (inBatchPass && hasBlur) return true;
        if (inBlurPass && !hasBlur) return true;
        return false;
    }

    private float getHudDelta(RectHudModule module, boolean chatEditing, float deltaSeconds) {
        return deltaSeconds;
    }

    private float getHudDelta(ArmorHud module, boolean chatEditing, float deltaSeconds) {
        return deltaSeconds;
    }

    private void renderBufferedHudInternal(DrawContext context, boolean enabled, boolean batching, HudBuffer buffer, int targetFps, boolean chatEditing, Runnable renderLogic) {
        if (!enabled) {
            return;
        }

        // Per-HUD caching is unused; the entire HUD is batched into BatchedHudBuffer
        // at the InGameHudMixin#render TAIL injection level (Exordium-style).
        renderLogic.run();
    }

    private String getCachedHudText(RectHudModule module, int hudIndex, boolean chatEditing, Supplier<String> supplier) {
        long now = System.currentTimeMillis();
        boolean throttledDue = now - HUD_TEXT_CACHE_TIME_MS[hudIndex] >= HUD_TEXT_THROTTLE_MS;
        if (HUD_TEXT_CACHE[hudIndex] == null || throttledDue) {
            HUD_TEXT_CACHE[hudIndex] = supplier.get();
            HUD_TEXT_CACHE_TIME_MS[hudIndex] = now;
        }
        return HUD_TEXT_CACHE[hudIndex];
    }

    private void renderRectHud(
            DrawContext context,
            MinecraftClient client,
            RectHudModule module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        renderRectHud(context, client, module, text, hudIndex, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, BASE_WIDTH, BASE_HEIGHT);
    }

    private void renderRectHud(
            DrawContext context,
            MinecraftClient client,
            RectHudModule module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY,
            float baseWidth,
            float baseHeight
    ) {
        if (!module.isEnabled()) {
            return;
        }

        float scale = MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale());
        module.setHudScale(scale);

        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;
        float maxX = Math.max(0.0f, screenWidth - hudWidth);
        float maxY = Math.max(0.0f, screenHeight - hudHeight);
        float x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
        float y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
        module.setHudX(x);
        module.setHudY(y);

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
        // Place resize handle diagonally on the bottom-right corner:
        // half inside the HUD and half outside, like a corner grip.
        float handleX = x + hudWidth - handleSize / 2.0f;
        float handleY = y + hudHeight - handleSize / 2.0f;

        boolean hoveredHud = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;

        if (!chatEditing) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], 0.0f, 10.0f, deltaSeconds);
        } else {
            hoveredHud = isHovered(mouseX, mouseY, x, y, hudWidth, hudHeight);
            hoveredHandle = isHovered(mouseX, mouseY, handleX, handleY, handleSize, handleSize);
            nearHud = isNearRect(mouseX, mouseY, x, y, hudWidth, hudHeight, Math.max(14.0f, 12.0f * scale));
            if (!mouseDown) {
                RECT_DRAGGING[hudIndex] = false;
                RECT_RESIZING[hudIndex] = false;
            } else if (!wasMouseDown && !isAnyHudInteractionActive()) {
                if (hoveredHandle) {
                    RECT_RESIZING[hudIndex] = true;
                    RECT_RESIZE_START_WIDTH[hudIndex] = hudWidth;
                    RECT_RESIZE_START_MOUSE_X[hudIndex] = (float) mouseX;
                    RECT_RESIZE_START_MOUSE_Y[hudIndex] = (float) mouseY;
                } else if (hoveredHud) {
                    RECT_DRAGGING[hudIndex] = true;
                    RECT_DRAG_OFFSET_X[hudIndex] = (float) mouseX - x;
                    RECT_DRAG_OFFSET_Y[hudIndex] = (float) mouseY - y;
                }
            }

            if (mouseDown) {
                if (RECT_DRAGGING[hudIndex]) {
                    float newX = MathHelper.clamp((float) mouseX - RECT_DRAG_OFFSET_X[hudIndex], 0.0f, maxX);
                    float newY = MathHelper.clamp((float) mouseY - RECT_DRAG_OFFSET_Y[hudIndex], 0.0f, maxY);
                    float centerX = newX + hudWidth * 0.5f;
                    float centerY = newY + hudHeight * 0.5f;

                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5f, 0.0f, maxX);
                        showVerticalGuideThisFrame = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5f, 0.0f, maxY);
                        showHorizontalGuideThisFrame = true;
                    }

                    module.setHudX(newX);
                    module.setHudY(newY);
                    x = newX;
                    y = newY;
                    handleX = x + hudWidth - handleSize / 2.0f;
                    handleY = y + hudHeight - handleSize / 2.0f;
                } else if (RECT_RESIZING[hudIndex]) {
                    float deltaX = (float) mouseX - RECT_RESIZE_START_MOUSE_X[hudIndex];
                    float deltaY = (float) mouseY - RECT_RESIZE_START_MOUSE_Y[hudIndex];
                    float delta = (deltaX + deltaY) * 0.5f;

                    float minWidth = baseWidth * module.getMinHudScale();
                    float maxWidth = baseWidth * module.getMaxHudScale();
                    float newWidth = MathHelper.clamp(RECT_RESIZE_START_WIDTH[hudIndex] + delta * 0.9f, minWidth, maxWidth);
                    float newScale = newWidth / baseWidth;

                    module.setHudScale(newScale);
                    scale = newScale;
                    hudWidth = baseWidth * scale;
                    hudHeight = baseHeight * scale;
                    maxX = Math.max(0.0f, screenWidth - hudWidth);
                    maxY = Math.max(0.0f, screenHeight - hudHeight);
                    x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
                    y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
                    module.setHudX(x);
                    module.setHudY(y);
                    handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
                    handleX = x + hudWidth - handleSize / 2.0f;
                    handleY = y + hudHeight - handleSize / 2.0f;
                }
            }

            if (RECT_DRAGGING[hudIndex]) {
                float centerX = x + hudWidth * 0.5f;
                float centerY = y + hudHeight * 0.5f;
                if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                    showVerticalGuideThisFrame = true;
                }
                if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                    showHorizontalGuideThisFrame = true;
                }
            }

            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], hoveredHud ? 1.0f : 0.0f, 10.0f, deltaSeconds);
        }

        float textWidth = getHudTextWidth(client, text, HUD_TEXT_SIZE);
        float textX = (baseWidth - textWidth) / 2.0f;
        float textY = (baseHeight - 8.0f) / 2.0f;
        int hoverOutlineThickness = Math.max(1, Math.round(BASE_HOVER_OUTLINE_THICKNESS / Math.max(1.0f, scale)));

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);

        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z);
        context.getMatrices().scale(scale, scale, 1.0f);

        if (module.background.isValue() && hudIndex != HUD_KEYSTROKES) {
            float blurRadius = Math.max(0.0f, module.backgroundBlurRadius.getValue());
            if (blurRadius > 0.0f) {
                float safeScale = Math.max(scale, 1.0f);
                float normalizedBlurRadius = blurRadius / safeScale;
                float blurX = 0.0f;
                float blurY = 0.0f;
                float blurWidth = baseWidth;
                float blurHeight = baseHeight;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, blurX, blurY, blurWidth, blurHeight);
                Blur.INSTANCE.registerHudBlurState(hudIndex, blurStateKey);

                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(), blurX, blurY, blurWidth, blurHeight)
                            .round(0.0f)
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            int targetBgColor = module.getResolvedBackgroundColor(client);
            if (!RECT_BG_COLOR_INITIALIZED[hudIndex]) {
                RECT_BG_ANIMATED_COLOR[hudIndex] = targetBgColor;
                RECT_BG_COLOR_INITIALIZED[hudIndex] = true;
            } else {
                RECT_BG_ANIMATED_COLOR[hudIndex] = approachColorExp(RECT_BG_ANIMATED_COLOR[hudIndex], targetBgColor, 12.0f, deltaSeconds);
            }

            int bgColor = RECT_BG_ANIMATED_COLOR[hudIndex];
            if (chatEditing) {
                int hoverFill = withAlpha(0xFFFFFF, (int) (30.0f * RECT_HOVER_PROGRESS[hudIndex]));
                bgColor = blendARGB(bgColor, hoverFill);
            }
            if (hudIndex != HUD_KEYSTROKES) {
                context.fill(0, 0, Math.round(baseWidth), Math.round(baseHeight), bgColor);
                context.draw();
            }
        }

        context.getMatrices().pop();

        // Use custom color for MemoryHud if Color Based On Usage is enabled
        int textColor = HUD_TEXT_COLOR;
        if (module instanceof MemoryHud) {
            MemoryHud memoryHud = (MemoryHud) module;
            textColor = memoryHud.getMemoryColor();
        } else if (module instanceof HealthIndicator) {
            // HealthIndicator picks its colour from the victim's current
            // HP (matching the original mod's red/gold/yellow/green/dark-
            // green table). The method internally returns the white
            // sentinel HUD_TEXT_COLOR when Color By HP is off OR when no
            // target is tracked, so this branch transparently degrades
            // to the inherited default in those cases.
            textColor = ((HealthIndicator) module).getCurrentHpColor();
        } else if (module instanceof TpsHud) {
            // TPS HUD: green / yellow / red tier color when the user
            // has Color By TPS on. Off = white sentinel.
            textColor = ((TpsHud) module).getColor();
        }

        renderScaledHudTextColored(context, client, text, x, y, textX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), textColor);

        if (chatEditing && RECT_HOVER_PROGRESS[hudIndex] > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (175.0f * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
        if (showResizeHandle) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, HANDLE_RENDER_Z);
            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);

            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                int borderColor = withAlpha(0xFFFFFF, 220);
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, borderColor);
            }
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    private void renderArmorHud(
            DrawContext context,
            MinecraftClient client,
            ArmorHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.player == null) {
            return;
        }

        // Rebuild the armor list every frame. Caching it across frames was
        // causing the HUD to stay invisible if the player joined the world
        // without armor: the cache was only invalidated on world change, so
        // equipping a piece of armor mid-game never refreshed it and the
        // empty-list early return below kept the HUD permanently hidden.
        // The list has at most 5 entries (4 armor + 1 shield) so the
        // per-frame cost is negligible.
        List<ItemStack> updatedStacks = new ArrayList<>();
        List<String> updatedDurabilityTexts = new ArrayList<>();

        var inventory = client.player.getInventory();
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack stack = inventory.getArmorStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            updatedStacks.add(stack);
            updatedDurabilityTexts.add(formatDurabilityText(module, stack));
        }

        ItemStack offHand = client.player.getOffHandStack();
        if (!offHand.isEmpty() && offHand.isOf(Items.SHIELD)) {
            updatedStacks.add(offHand);
            updatedDurabilityTexts.add(formatDurabilityText(module, offHand));
        }

        if (updatedStacks.isEmpty() && chatEditing) {
            updatedStacks.add(new ItemStack(Items.DIAMOND_HELMET));
            updatedStacks.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            updatedStacks.add(new ItemStack(Items.DIAMOND_LEGGINGS));
            updatedStacks.add(new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack stack : updatedStacks) {
                updatedDurabilityTexts.add(module.formatDurability(stack.getMaxDamage(), stack.getMaxDamage()));
            }
        }

        armorStacksCache = updatedStacks;
        armorDurabilityTextsCache = updatedDurabilityTexts;

        List<ItemStack> stacks = armorStacksCache;
        List<String> durabilityTexts = armorDurabilityTextsCache;
        if (stacks.isEmpty() && !chatEditing) {
            return;
        }

        int iconSize = 16;
        int rowHeight = 18;
        int textGap = 3;
        int numberSidePadding = 2;
        float maxTextWidth = 0.0f;
        for (String text : durabilityTexts) {
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, text, HUD_TEXT_SIZE));
        }

        float scale = MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale());
        module.setHudScale(scale);
        float baseWidth = iconSize + textGap + maxTextWidth + numberSidePadding;
        float baseHeight = stacks.size() * rowHeight;
        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;

        float maxX = Math.max(0.0f, screenWidth - hudWidth);
        float maxY = Math.max(0.0f, screenHeight - hudHeight);
        float x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
        float y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
        module.setHudX(x);
        module.setHudY(y);

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
        float handleX = x + hudWidth - handleSize / 2.0f;
        float handleY = y + hudHeight - handleSize / 2.0f;
        boolean hovered = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;

        if (!chatEditing) {
            armorDragging = false;
            armorResizing = false;
            armorHoverProgress = approachExp(armorHoverProgress, 0.0f, 10.0f, deltaSeconds);
        } else {
            hovered = isHovered(mouseX, mouseY, x, y, hudWidth, hudHeight);
            hoveredHandle = isHovered(mouseX, mouseY, handleX, handleY, handleSize, handleSize);
            nearHud = isNearRect(mouseX, mouseY, x, y, hudWidth, hudHeight, Math.max(14.0f, 12.0f * scale));
            if (!mouseDown) {
                armorDragging = false;
                armorResizing = false;
            } else if (!wasMouseDown && !isAnyHudInteractionActive()) {
                if (hoveredHandle) {
                    armorResizing = true;
                    armorResizeStartScale = scale;
                    armorResizeStartMouseX = (float) mouseX;
                    armorResizeStartMouseY = (float) mouseY;
                } else if (hovered) {
                    armorDragging = true;
                    armorDragOffsetX = (float) mouseX - x;
                    armorDragOffsetY = (float) mouseY - y;
                }
            }

            if (mouseDown) {
                if (armorDragging) {
                    float newX = MathHelper.clamp((float) mouseX - armorDragOffsetX, 0.0f, maxX);
                    float newY = MathHelper.clamp((float) mouseY - armorDragOffsetY, 0.0f, maxY);
                    float centerX = newX + hudWidth * 0.5f;
                    float centerY = newY + hudHeight * 0.5f;

                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5f, 0.0f, maxX);
                        showVerticalGuideThisFrame = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5f, 0.0f, maxY);
                        showHorizontalGuideThisFrame = true;
                    }

                    module.setHudX(newX);
                    module.setHudY(newY);
                    x = newX;
                    y = newY;
                    handleX = x + hudWidth - handleSize / 2.0f;
                    handleY = y + hudHeight - handleSize / 2.0f;
                } else if (armorResizing) {
                    float deltaX = (float) mouseX - armorResizeStartMouseX;
                    float deltaY = (float) mouseY - armorResizeStartMouseY;
                    float delta = (deltaX + deltaY) * 0.5f;
                    // Match resize feel of other rect HUDs (reference width = 64).
                    float newScale = armorResizeStartScale + (delta * 0.9f) / BASE_WIDTH;
                    newScale = MathHelper.clamp(newScale, module.getMinHudScale(), module.getMaxHudScale());
                    module.setHudScale(newScale);
                    scale = newScale;
                    hudWidth = baseWidth * scale;
                    hudHeight = baseHeight * scale;
                    maxX = Math.max(0.0f, screenWidth - hudWidth);
                    maxY = Math.max(0.0f, screenHeight - hudHeight);
                    x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
                    y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
                    module.setHudX(x);
                    module.setHudY(y);
                    handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
                    handleX = x + hudWidth - handleSize / 2.0f;
                    handleY = y + hudHeight - handleSize / 2.0f;
                }
            }

            if (armorDragging) {
                float centerX = x + hudWidth * 0.5f;
                float centerY = y + hudHeight * 0.5f;
                if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                    showVerticalGuideThisFrame = true;
                }
                if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                    showHorizontalGuideThisFrame = true;
                }
            }

            armorHoverProgress = approachExp(armorHoverProgress, hovered ? 1.0f : 0.0f, 10.0f, deltaSeconds);
        }

        boolean textOnLeft = x > screenWidth * 0.5f;
        int hoverOutlineThickness = Math.max(1, Math.round(2.0f / Math.max(1.0f, scale)));

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z);
        context.getMatrices().scale(scale, scale, 1.0f);

        if (module.background.isValue()) {
            float blurRadius = Math.max(0.0f, module.backgroundBlurRadius.getValue());
            if (blurRadius > 0.0f) {
                float safeScale = Math.max(scale, 1.0f);
                float normalizedBlurRadius = blurRadius / safeScale;
                float blurX = 0.0f;
                float blurY = 0.0f;
                float blurWidth = baseWidth;
                float blurHeight = baseHeight;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, blurX, blurY, blurWidth, blurHeight);
                Blur.INSTANCE.registerHudBlurState(HUD_ARMOR_BLUR_SLOT, blurStateKey);

                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(), blurX, blurY, blurWidth, blurHeight)
                            .round(0.0f)
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            int targetBgColor = module.background.isValue() ? module.getResolvedBackgroundColor(client) : 0;
            if (!armorBackgroundColorInitialized) {
                armorAnimatedBackgroundColor = targetBgColor;
                armorBackgroundColorInitialized = true;
            } else {
                armorAnimatedBackgroundColor = approachColorExp(armorAnimatedBackgroundColor, targetBgColor, 12.0f, deltaSeconds);
            }

            int bgColor = armorAnimatedBackgroundColor;
            if (chatEditing) {
                int hoverFill = withAlpha(0xFFFFFF, (int) (30.0f * armorHoverProgress));
                bgColor = blendARGB(bgColor, hoverFill);
            }
            context.fill(0, 0, Math.round(baseWidth), Math.round(baseHeight), bgColor);
            context.draw();
        }

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            String durabilityText = durabilityTexts.get(i);
            float rowY = i * rowHeight;
            float textWidth = getHudTextWidth(client, durabilityText, HUD_TEXT_SIZE);

            int iconX;
            float textX;
            if (textOnLeft) {
                iconX = Math.round(numberSidePadding + maxTextWidth + textGap);
                textX = numberSidePadding + (maxTextWidth - textWidth);
            } else {
                iconX = 0;
                textX = iconSize + textGap;
            }

            int iconY = Math.round(rowY + 1.0f);
            context.drawItem(stack, iconX, iconY);
        }
        context.getMatrices().pop();

        for (int i = 0; i < stacks.size(); i++) {
            String durabilityText = durabilityTexts.get(i);
            ItemStack stackForColor = stacks.get(i);
            float rowY = i * rowHeight;
            float textWidth = getHudTextWidth(client, durabilityText, HUD_TEXT_SIZE);

            float textX;
            if (textOnLeft) {
                textX = numberSidePadding + (maxTextWidth - textWidth);
            } else {
                textX = iconSize + textGap;
            }

            float textY = rowY + 4.0f;

            // Color By Durability: pick a stoplight colour from the
            // remaining vs max ratio of THIS row's stack. Falls back
            // to white when the toggle is off (the inherited
            // {@code renderScaledHudText} default), so the visual is
            // unchanged on disabled.
            int stackMax = stackForColor.getMaxDamage();
            int stackRemaining = stackForColor.isDamageable()
                    ? Math.max(0, stackMax - stackForColor.getDamage())
                    : stackMax;
            int textColor = module.colorForDurability(stackRemaining, stackMax);

            renderScaledHudTextColored(context, client, durabilityText, x, y, textX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), textColor);
        }

        if (chatEditing && armorHoverProgress > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (160.0f * armorHoverProgress));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && (armorResizing || hoveredHandle || hovered || nearHud);
        if (showResizeHandle) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, HANDLE_RENDER_Z);
            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = armorResizing ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);

            if (hoveredHandle || armorResizing) {
                int borderColor = withAlpha(0xFFFFFF, 220);
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, borderColor);
            }
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    /**
     * Custom render path for the {@code Consumable} module - rect HUD
     * background reused via {@link RectHudModule#getResolvedBackgroundColor}
     * but populated with {@link DrawContext#drawItem} icons rather
     * than text. Mirrors the structure of
     * {@link #renderArmorHud} (drag / resize / hover outline /
     * resize handle) but compresses the parts that don't apply to
     * a fixed-grid icon list (no text-on-left flip, no per-row
     * durability strings, no armor blur slot - Consumable uses its
     * own RECT_DRAGGING slot instead).
     */
    private void renderConsumableHud(
            DrawContext context,
            MinecraftClient client,
            vorga.phazeclient.implement.features.modules.hud.Consumable module,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.player == null) {
            return;
        }

        vorga.phazeclient.implement.features.modules.hud.Consumable.Layout layout = module.computeLayout(chatEditing);
        if (layout.entries().isEmpty()) {
            return;
        }

        float baseWidth = layout.baseWidth();
        float baseHeight = layout.baseHeight();

        float scale = MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale());
        module.setHudScale(scale);
        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;

        float maxX = Math.max(0.0f, screenWidth - hudWidth);
        float maxY = Math.max(0.0f, screenHeight - hudHeight);
        float x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
        float y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
        module.setHudX(x);
        module.setHudY(y);

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
        float handleX = x + hudWidth - handleSize;
        float handleY = y + hudHeight - handleSize;
        boolean hovered = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;

        if (!chatEditing) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], 0.0f, 10.0f, deltaSeconds);
        } else {
            hovered = isHovered(mouseX, mouseY, x, y, hudWidth, hudHeight);
            hoveredHandle = isHovered(mouseX, mouseY, handleX, handleY, handleSize, handleSize);
            nearHud = isNearRect(mouseX, mouseY, x, y, hudWidth, hudHeight, Math.max(14.0f, 12.0f * scale));
            if (!mouseDown) {
                RECT_DRAGGING[hudIndex] = false;
                RECT_RESIZING[hudIndex] = false;
            } else if (!wasMouseDown && !isAnyHudInteractionActive()) {
                if (hoveredHandle) {
                    RECT_RESIZING[hudIndex] = true;
                    RECT_RESIZE_START_WIDTH[hudIndex] = scale;
                    RECT_RESIZE_START_MOUSE_X[hudIndex] = (float) mouseX;
                    RECT_RESIZE_START_MOUSE_Y[hudIndex] = (float) mouseY;
                } else if (hovered) {
                    RECT_DRAGGING[hudIndex] = true;
                    RECT_DRAG_OFFSET_X[hudIndex] = (float) mouseX - x;
                    RECT_DRAG_OFFSET_Y[hudIndex] = (float) mouseY - y;
                }
            }

            if (mouseDown) {
                if (RECT_DRAGGING[hudIndex]) {
                    float newX = MathHelper.clamp((float) mouseX - RECT_DRAG_OFFSET_X[hudIndex], 0.0f, maxX);
                    float newY = MathHelper.clamp((float) mouseY - RECT_DRAG_OFFSET_Y[hudIndex], 0.0f, maxY);
                    float centerX = newX + hudWidth * 0.5f;
                    float centerY = newY + hudHeight * 0.5f;
                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5f, 0.0f, maxX);
                        showVerticalGuideThisFrame = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5f, 0.0f, maxY);
                        showHorizontalGuideThisFrame = true;
                    }
                    module.setHudX(newX);
                    module.setHudY(newY);
                    x = newX;
                    y = newY;
                    handleX = x + hudWidth - handleSize;
                    handleY = y + hudHeight - handleSize;
                } else if (RECT_RESIZING[hudIndex]) {
                    float deltaX = (float) mouseX - RECT_RESIZE_START_MOUSE_X[hudIndex];
                    float deltaY = (float) mouseY - RECT_RESIZE_START_MOUSE_Y[hudIndex];
                    float delta = (deltaX + deltaY) * 0.5f;
                    float newScale = RECT_RESIZE_START_WIDTH[hudIndex] + (delta * 0.9f) / BASE_WIDTH;
                    newScale = MathHelper.clamp(newScale, module.getMinHudScale(), module.getMaxHudScale());
                    module.setHudScale(newScale);
                    scale = newScale;
                    hudWidth = baseWidth * scale;
                    hudHeight = baseHeight * scale;
                    maxX = Math.max(0.0f, screenWidth - hudWidth);
                    maxY = Math.max(0.0f, screenHeight - hudHeight);
                    x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
                    y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
                    module.setHudX(x);
                    module.setHudY(y);
                    handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
                    handleX = x + hudWidth - handleSize;
                    handleY = y + hudHeight - handleSize;
                }
            }

            if (RECT_DRAGGING[hudIndex]) {
                float centerX = x + hudWidth * 0.5f;
                float centerY = y + hudHeight * 0.5f;
                if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                    showVerticalGuideThisFrame = true;
                }
                if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                    showHorizontalGuideThisFrame = true;
                }
            }

            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], hovered ? 1.0f : 0.0f, 10.0f, deltaSeconds);
        }

        int hoverOutlineThickness = Math.max(1, Math.round(2.0f / Math.max(1.0f, scale)));

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z);
        context.getMatrices().scale(scale, scale, 1.0f);

        if (module.background.isValue()) {
            float blurRadius = Math.max(0.0f, module.backgroundBlurRadius.getValue());
            if (blurRadius > 0.0f) {
                float safeScale = Math.max(scale, 1.0f);
                float normalizedBlurRadius = blurRadius / safeScale;
                float blurX = 0.0f;
                float blurY = 0.0f;
                float blurWidth = baseWidth;
                float blurHeight = baseHeight;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, blurX, blurY, blurWidth, blurHeight);
                Blur.INSTANCE.registerHudBlurState(hudIndex, blurStateKey);

                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(), blurX, blurY, blurWidth, blurHeight)
                            .round(0.0f)
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            int targetBgColor = module.getResolvedBackgroundColor(client);
            if (!RECT_BG_COLOR_INITIALIZED[hudIndex]) {
                RECT_BG_ANIMATED_COLOR[hudIndex] = targetBgColor;
                RECT_BG_COLOR_INITIALIZED[hudIndex] = true;
            } else {
                RECT_BG_ANIMATED_COLOR[hudIndex] = approachColorExp(RECT_BG_ANIMATED_COLOR[hudIndex], targetBgColor, 12.0f, deltaSeconds);
            }
            int bgColor = RECT_BG_ANIMATED_COLOR[hudIndex];
            if (chatEditing) {
                int hoverFill = withAlpha(0xFFFFFF, (int) (30.0f * RECT_HOVER_PROGRESS[hudIndex]));
                bgColor = blendARGB(bgColor, hoverFill);
            }
            context.fill(0, 0, Math.round(baseWidth), Math.round(baseHeight), bgColor);
            context.draw();
        }

        // Icon pass: drawItem internally pushes its own
        // model-view stack so we don't have to translate per-icon.
        // Padding mirrors the value computeLayout sized the rect to.
        float padding = 3.0f;
        float itemSize = 18.0f;
        for (vorga.phazeclient.implement.features.modules.hud.Consumable.IconEntry entry : layout.entries()) {
            int iconX = Math.round(padding + entry.col() * itemSize);
            int iconY = Math.round(padding + entry.row() * itemSize);
            context.drawItem(entry.stack(), iconX, iconY);
            if (module.showCount.isValue()) {
                // drawItemInSlot is the vanilla helper that paints
                // the small bottom-right stack count number with
                // the correct shadow / scale baked in. Passing a
                // null label uses the stack's own count, which is
                // already pre-baked into the IconEntry's stack
                // copy.
                context.drawStackOverlay(client.textRenderer, entry.stack(), iconX, iconY);
            }
        }
        context.getMatrices().pop();

        if (chatEditing && RECT_HOVER_PROGRESS[hudIndex] > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (160.0f * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && (RECT_RESIZING[hudIndex] || hoveredHandle || hovered || nearHud);
        if (showResizeHandle) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, HANDLE_RENDER_Z);
            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);
            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                int borderColor = withAlpha(0xFFFFFF, 220);
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, borderColor);
            }
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    private void renderCoordinatesHud(
            DrawContext context,
            MinecraftClient client,
            CoordinatesHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.player == null || client.world == null) {
            return;
        }

        // Rebuild the line set every frame. Caching the list across
        // frames silently broke the per-axis toggles (Show X / Y / Z /
        // Biome): once initialised the cache was reused regardless of
        // setting changes AND it never picked up the player's new
        // position. The computations here are cheap enough (a single
        // BlockPos read + at most one biome registry lookup) that
        // recomputing per frame stays well under the HUD's frame
        // budget while letting the toggles take immediate effect.
        List<String> updatedLines = new ArrayList<>();
        BlockPos pos = client.player.getBlockPos();
        // Streamer Mode suppresses only the position-revealing rows
        // (X / Y / Z / Chunk). Biome and Direction stay visible because
        // they do not leak the player's absolute location - biome is a
        // world-type hint and the direction indicator is just yaw. The
        // rect keeps rendering with whatever remains, instead of
        // collapsing entirely, so the user still sees the HUD during a
        // stream just without the coord axes.
        boolean hideCoords = StreamerMode.getInstance().isHideCoordinatesEnabled();
        if (module.showX.isValue() && !hideCoords) {
            updatedLines.add("X: " + pos.getX());
        }
        if (module.showY.isValue() && !hideCoords) {
            updatedLines.add("Y: " + pos.getY());
        }
        if (module.showZ.isValue() && !hideCoords) {
            updatedLines.add("Z: " + pos.getZ());
        }
        if (module.showChunk.isValue() && !hideCoords) {
            updatedLines.add("C: " + ChunkSectionPos.getLocalCoord(pos.getX()) + "/" + ChunkSectionPos.getLocalCoord(pos.getZ()));
        }
        // When Show Biome is OFF the row is omitted entirely - the
        // earlier behaviour kept emitting an empty Biome line in a
        // dimmed colour, which the user reasonably read as "the toggle
        // is broken". Now the row only exists when the toggle is on.
        coordinatesBiomeNameCache = "";
        coordinatesBiomeColorCache = 0xFFFF55;
        if (module.showBiome.isValue()) {
            coordinatesBiomeNameCache = getBiomeName(client, pos);
            coordinatesBiomeColorCache = getBiomeColor(client, pos);
            updatedLines.add("Biome: " + coordinatesBiomeNameCache);
        }
        coordinatesFacingCache = getFacing8Point(client.player.getYaw(0.0f));
        coordinatesTopSignCache = getDirectionXSign(coordinatesFacingCache);
        coordinatesBottomSignCache = getDirectionZSign(coordinatesFacingCache);
        coordinatesLinesCache = updatedLines;

        List<String> lines = coordinatesLinesCache;
        String biomeName = coordinatesBiomeNameCache;
        if (lines.isEmpty() && !chatEditing) {
            return;
        }
        if (lines.isEmpty()) {
            lines.add("Coordinates");
        }

        float paddingX = 5.0f;
        float paddingY = 4.0f;
        float lineHeight = 10.0f;
        float maxLineWidth = 0.0f;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, getHudTextWidth(client, line, HUD_TEXT_SIZE));
        }
        boolean showDirection = module.showDirection.isValue();
        float directionColumn = showDirection ? 26.0f : 0.0f;
        float baseWidth = Math.max(74.0f, paddingX * 2.0f + maxLineWidth + directionColumn);
        float baseHeight = Math.max(BASE_HEIGHT, paddingY * 2.0f + lines.size() * lineHeight);

        renderRectHud(context, client, module, "", HUD_COORDINATES, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);

        // Vertically centre the text block inside the rect. For the
        // normal multi-row layout {@code (baseHeight - textBlockHeight)
        // / 2} equals {@code paddingY}, so nothing changes; the
        // difference only matters when Streamer Mode strips the X/Y/Z/C
        // rows and a single Biome line is left rattling around in a
        // 20 px tall rect - without this fix the line stayed glued to
        // the top of the rect with a 6 px gap below it.
        float textBlockHeight = lines.size() * lineHeight;
        float textOffsetY = (baseHeight - textBlockHeight) * 0.5f;
        if (textOffsetY < paddingY) {
            textOffsetY = paddingY;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float textY = textOffsetY + i * lineHeight;
            if (module.showBiome.isValue() && line.startsWith("Biome: ")) {
                String label = "Biome: ";
                renderScaledHudTextColored(context, client, label, x, y, paddingX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), 0xFFFFFF);
                float labelWidth = getHudTextWidth(client, label, HUD_TEXT_SIZE);
                renderScaledHudTextColored(context, client, biomeName, x, y, paddingX + labelWidth, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), coordinatesBiomeColorCache);
            } else {
                renderScaledHudText(context, client, line, x, y, paddingX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
            }
        }

        if (showDirection) {
            String facing = coordinatesFacingCache;
            float facingWidth = getHudTextWidth(client, facing, HUD_TEXT_SIZE);
            float facingCenterX = baseWidth - directionColumn * 0.5f;
            float facingX = facingCenterX - facingWidth * 0.5f;
            float facingY = baseHeight * 0.5f - 4.0f;
            if (module.showAxisSigns.isValue()) {
                String topSign = coordinatesTopSignCache;
                String bottomSign = coordinatesBottomSignCache;
                // The +/- axis-sign trio (top sign / facing / bottom
                // sign) is normally laid out at a fixed 10 px offset
                // above and below the facing letter, which produces a
                // ~28 px tall stack that visibly overshoots a short
                // rect (eg the 20 px Streamer Mode rect with just
                // Biome + compass). Scale the offset so the stack
                // always fits inside the rect with one pixel of
                // breathing room top and bottom; for the normal tall
                // layout we still cap at the original 10 px so the
                // signs don't drift apart further than designed.
                float maxStackHalf = baseHeight * 0.5f - 5.0f;
                if (maxStackHalf < 0.0f) {
                    maxStackHalf = 0.0f;
                }
                float signOffset = Math.min(10.0f, maxStackHalf);
                // Streamer Mode also shrinks the actual glyph size of
                // the +/- signs (not just their spacing). The default
                // HUD_TEXT_SIZE is calibrated for the ~50 px tall
                // four-row rect; shrinking the rect to just Biome +
                // compass leaves the +/- looking comically large
                // relative to the column. 5 px (≈63% of 8) keeps the
                // glyphs visibly recognisable as +/- without
                // dominating the column. The central facing letter
                // stays at HUD_TEXT_SIZE because it carries the
                // primary semantic.
                float signTextSize = hideCoords ? 5.0f : HUD_TEXT_SIZE;
                if (!topSign.isEmpty()) {
                    float signWidth = getHudTextWidth(client, topSign, signTextSize);
                    renderScaledHudText(context, client, topSign, x, y, facingCenterX - signWidth * 0.5f, facingY - signOffset, signTextSize, scale, module.textShadow.isValue());
                }
                renderScaledHudText(context, client, facing, x, y, facingX, facingY, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
                if (!bottomSign.isEmpty()) {
                    float signWidth = getHudTextWidth(client, bottomSign, signTextSize);
                    renderScaledHudText(context, client, bottomSign, x, y, facingCenterX - signWidth * 0.5f, facingY + signOffset, signTextSize, scale, module.textShadow.isValue());
                }
            } else {
                renderScaledHudText(context, client, facing, x, y, facingX, facingY, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
            }
        }

        context.getMatrices().pop();
    }

    private void renderPingHud(
            DrawContext context,
            MinecraftClient client,
            PingHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.player == null) {
            return;
        }

        boolean local = client.isIntegratedServerRunning() || client.getCurrentServerEntry() == null;
        // {@code reverseOrder} swaps the HUD between the labelled
        // "Ping: 50 ms" and the value-first "50 ms Ping" forms. The
        // value half (the actual ping number with " ms" unit) is the
        // colorised segment in both layouts; only its X anchor and the
        // surrounding label string change.
        boolean reversed = module.reverseOrder.isValue();
        String label = reversed ? " Ping" : "Ping: ";
        String value = getCachedHudText(module, HUD_PING, chatEditing, () -> {
            if (local) {
                module.resetPingCache();
                return "... ms";
            }
            int rawPing = getServerPing(client);
            module.updatePing(rawPing);
            int displayPing = module.getCachedPing();
            return displayPing >= 0 ? displayPing + " ms" : "... ms";
        });
        String fullText = reversed ? value + label : label + value;
        fullText = wrapTextWithBrackets(fullText, module);
        float baseWidth = Math.max(48.0f, getHudTextWidth(client, fullText, HUD_TEXT_SIZE) + 12.0f);

        renderRectHud(context, client, module, "", HUD_PING, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, BASE_HEIGHT);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        float totalWidth = getHudTextWidth(client, fullText, HUD_TEXT_SIZE);
        float textX = (baseWidth - totalWidth) * 0.5f;
        float textY = (BASE_HEIGHT - 8.0f) / 2.0f;

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        renderScaledHudText(context, client, fullText, x, y, textX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
        float labelWidth = getHudTextWidth(client, label, HUD_TEXT_SIZE);
        int pingColor;
        if (local) {
            pingColor = 0xFFFF55;
        } else {
            int ping = module.getCachedPing();
            if (module.dynamicPingColor.isValue() && ping > 0) {
                if (ping < 60) pingColor = 0xFF55FF55;
                else if (ping < 150) pingColor = 0xFFFFFF55;
                else if (ping < 300) pingColor = 0xFFFFAA00;
                else pingColor = 0xFFFF5555;
            } else {
                pingColor = 0xFFFFFF;
            }
        }
        // In reversed layout the value segment leads the line, so it
        // sits flush at {@code textX}. In default layout the label
        // takes the leading position and the value lives just after
        // it (offset by {@code labelWidth}).
        float valueX = reversed ? textX : textX + labelWidth;
        renderScaledHudTextColored(context, client, value, x, y, valueX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), pingColor);
        context.getMatrices().pop();
    }

    private void renderSimpleTextHud(
            DrawContext context,
            MinecraftClient client,
            RectHudModule module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        float baseWidth = Math.max(48.0f, getHudTextWidth(client, text, HUD_TEXT_SIZE) + 14.0f);
        renderRectHud(context, client, module, text, hudIndex, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, BASE_HEIGHT);
    }

    private void renderSessionTimeHud(
            DrawContext context,
            MinecraftClient client,
            RectHudModule module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        float textWidth = client.textRenderer.getWidth(text);
        float baseWidth = Math.max(48.0f, textWidth + 16.0f);
        float baseHeight = BASE_HEIGHT;

        renderRectHud(context, client, module, "", hudIndex, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        float textX = (baseWidth - textWidth) * 0.5f;
        float textY = (baseHeight - 9.0f) * 0.5f;
        context.drawText(client.textRenderer, text, Math.round(textX), Math.round(textY), 0xFFFFFF, module.textShadow.isValue());
        context.draw();
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    private void renderScoreboardHud(
            DrawContext context,
            MinecraftClient client,
            ScoreboardHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.world == null || client.player == null) {
            return;
        }

        // Get scoreboard data
        var scoreboard = client.world.getScoreboard();
        var objective = scoreboard.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            return;
        }

        var entries = scoreboard.getScoreboardEntries(objective);
        var filteredEntries = entries.stream()
                .filter(entry -> !entry.hidden())
                .sorted((a, b) -> {
                    int byValue = Integer.compare(b.value(), a.value());
                    if (byValue != 0) return byValue;
                    return String.CASE_INSENSITIVE_ORDER.compare(a.owner(), b.owner());
                })
                .limit(15)
                .toList();
        if (filteredEntries.isEmpty()) {
            return;
        }

        // Create sidebar entries
        interface SidebarEntry {
            net.minecraft.text.Text name();
            net.minecraft.text.Text score();
            boolean hasScore();
            int scoreWidth();
        }

        var sidebarEntries = new java.util.ArrayList<SidebarEntry>();
        for (var entry : filteredEntries) {
            var team = scoreboard.getScoreHolderTeam(entry.owner());
            var name = entry.name();
            var decoratedName = net.minecraft.scoreboard.Team.decorateName(team, name);
            // Respect server-provided number rendering. If the server omits
            // right-side numbers, do not force-draw them.
            net.minecraft.text.Text formattedScore = net.minecraft.text.Text.empty();
            boolean hasScore = false;
            try {
                formattedScore = entry.formatted(objective.getNumberFormat());
                String rawScore = formattedScore == null ? "" : formattedScore.getString();
                hasScore = rawScore != null && !rawScore.trim().isEmpty();
            } catch (Throwable ignored) {
                hasScore = false;
            }
            int scoreWidth = hasScore ? (int)getHudTextWidth(client, formattedScore.getString(), HUD_TEXT_SIZE) : 0;

            final var finalDecoratedName = decoratedName;
            final var finalFormattedScore = formattedScore;
            final var finalHasScore = hasScore;
            final var finalScoreWidth = scoreWidth;

            sidebarEntries.add(new SidebarEntry() {
                @Override
                public net.minecraft.text.Text name() { return finalDecoratedName; }
                @Override
                public net.minecraft.text.Text score() { return finalFormattedScore; }
                @Override
                public boolean hasScore() { return finalHasScore; }
                @Override
                public int scoreWidth() { return finalScoreWidth; }
            });
        }

        // Calculate dimensions
        var title = objective.getDisplayName();
        int titleWidth = (int)getHudTextWidth(client, title.getString(), HUD_TEXT_SIZE);
        int maxContentWidth = titleWidth;
        int entryCount = sidebarEntries.size();

        for (var sidebarEntry : sidebarEntries) {
            int textWidth = (int)getHudTextWidth(client, sidebarEntry.name().getString(), HUD_TEXT_SIZE);
            boolean hideZeroScore = sidebarEntry.hasScore()
                    && module.showNumbers.isValue()
                    && !module.showZeros.isValue()
                    && phaze$isZeroScoreText(sidebarEntry.score());
            int scoreWidth = (sidebarEntry.hasScore() && !hideZeroScore) ? sidebarEntry.scoreWidth() : 0;
            if (scoreWidth > 0) {
                // Vanilla sidebar aligns score to the right edge with a small
                // fixed gap; no extra ": " separator is rendered.
                maxContentWidth = Math.max(maxContentWidth, textWidth + scoreWidth + 2);
            } else {
                maxContentWidth = Math.max(maxContentWidth, textWidth);
            }
        }

        int vanillaHorizontalPadding = 3;
        float baseWidth = maxContentWidth + vanillaHorizontalPadding + 2;
        int topInset = module.showTitle.isValue() ? 10 : 1;
        float baseHeight = entryCount * 9.0f + topInset;

        // If the HUD is still at its constructor default (0,0), place it at
        // vanilla-like sidebar position (right side, vertically centered).
        if (module.getHudX() <= 1.0f && module.getHudY() <= 1.0f) {
            float scale = module.getHudScale();
            float hudWidth = baseWidth * scale;
            float hudHeight = baseHeight * scale;
            float vanillaX = Math.max(0.0f, screenWidth - hudWidth - 2.0f);
            float vanillaY = Math.max(0.0f, (screenHeight - hudHeight) * 0.5f);
            module.setHudX(vanillaX);
            module.setHudY(vanillaY);
        }

        // Keep drag/resize logic from RectHud, but prevent its own background
        // from drawing here: scoreboard renders a vanilla-style background below.
        boolean prevBackground = module.background.isValue();
        module.background.setValue(false);
        try {
            renderRectHud(context, client, module, "", HUD_SCOREBOARD, chatEditing, mouseX, mouseY, mouseDown,
                    deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);
        } finally {
            module.background.setValue(prevBackground);
        }

        // Get actual position and scale after renderRectHud
        float hudX = module.getHudX();
        float hudY = module.getHudY();
        float hudScale = module.getHudScale();

        // Calculate render positions in local coordinates (relative to hudX, hudY)
        int rightEdgeLocal = Math.round(baseWidth);
        int textLeftLocal = 2;
        int verticalPosLocal = entryCount * 9;

        // Get colors
        int titleBgColor;
        int rowBgColor;
        if (module.shouldUseVanillaColors()) {
            titleBgColor = client.options.getTextBackgroundColor(0.4F);
            rowBgColor = client.options.getTextBackgroundColor(0.3F);
        } else {
            titleBgColor = module.getResolvedBackgroundColor(client);
            rowBgColor = module.getResolvedBackgroundColor(client);
        }

        // Push matrices and use local coordinates like renderRectHud
        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z);
        context.getMatrices().scale(hudScale, hudScale, 1.0f);
        // Keep all drawable pixels inside RectHud bounds so selection/resize
        // area always matches, including the title strip.
        context.getMatrices().translate(0.0f, topInset, 0.0f);

        // Render blur and background
        if (module.background.isValue()) {
            int backgroundTopLocal = -topInset;
            int backgroundBottomLocal = verticalPosLocal;

            if (module.backgroundBlurRadius.getValue() > 0) {
                float blurQuality = getOptimizedHudBlurQuality(module.backgroundBlurRadius.getValue());
                context.draw();
                if (blurQuality > 0.0f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(),
                                    0, backgroundTopLocal, rightEdgeLocal, backgroundBottomLocal - backgroundTopLocal)
                            .round(0.0f)
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
                if (module.showTitle.isValue()) {
                    context.fill(0, -topInset, rightEdgeLocal, -1, titleBgColor);
                }
                context.fill(0, -1, rightEdgeLocal, verticalPosLocal, rowBgColor);
                context.draw();
            } else {
                if (module.showTitle.isValue()) {
                    context.fill(0, -topInset, rightEdgeLocal, -1, titleBgColor);
                }
                context.fill(0, -1, rightEdgeLocal, verticalPosLocal, rowBgColor);
            }
        }

        // Render title (in local coordinates)
        if (module.showTitle.isValue()) {
            float titleX = (rightEdgeLocal - titleWidth) / 2.0f;
            var textRenderer = client.textRenderer;
            context.drawText(textRenderer, title, (int)titleX, -topInset + 1, -1, false);
        }

        // Render entries (in local coordinates)
        for (int i = 0; i < entryCount; i++) {
            var sidebarEntry = sidebarEntries.get(i);
            int rowY = verticalPosLocal - (entryCount - i) * 9;

            var textRenderer = client.textRenderer;
            context.drawText(textRenderer, sidebarEntry.name(), textLeftLocal, rowY, -1, false);

            if (module.showNumbers.isValue() && sidebarEntry.hasScore()) {
                if (!module.showZeros.isValue() && phaze$isZeroScoreText(sidebarEntry.score())) {
                    continue;
                }
                float scoreWidth = sidebarEntry.scoreWidth();
                float scoreX = rightEdgeLocal - 2.0f - scoreWidth;
                context.drawText(textRenderer, sidebarEntry.score(), (int)scoreX, rowY, -1, false);
            }
        }

        context.getMatrices().pop();
        context.getMatrices().pop();

    }

    @Unique
    private static boolean phaze$isZeroScoreText(Text scoreText) {
        if (scoreText == null) {
            return false;
        }
        String raw = scoreText.getString();
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        boolean sawDigit = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (Character.isDigit(c)) {
                sawDigit = true;
                if (c != '0') {
                    return false;
                }
                continue;
            }
            return false;
        }
        return sawDigit;
    }

    private void renderDirectionHud(
            DrawContext context,
            MinecraftClient client,
            DirectionHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.player == null) {
            return;
        }

        float baseWidth = module.hudLength.getValue();
        float baseHeight = 52.0f;
        renderRectHud(context, client, module, "", HUD_DIRECTION, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        float targetYaw = wrap360(client.player.getYaw(0.0f));
        if (Float.isNaN(directionDisplayYaw)) {
            directionDisplayYaw = targetYaw;
        }
        float speed = MathHelper.clamp(module.smoothness.getValue(), 1.0f, 20.0f);
        directionDisplayYaw = approachAngle(directionDisplayYaw, targetYaw, deltaSeconds * speed * 6.0f);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        float centerX = baseWidth * 0.5f;
        float leftPadding = 8.0f;
        float rightPadding = 8.0f;
        float visibleWidth = Math.max(64.0f, baseWidth - leftPadding - rightPadding);
        float degreesPerPixel = 90.0f / visibleWidth;

        // Final layout rows (relative to HUD Y):
        float yawNumberY = 1.0f;
        float triangleY = 11.0f;
        float majorTickTop = 14.0f;
        float majorTickBottom = 28.0f;
        float minorTickTop = 17.0f;
        float minorTickBottom = 26.0f;
        float labelY = 30.0f;
        float baselineY = 28.0f;

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.fill(Math.round(x + leftPadding), Math.round(y + baselineY), Math.round(x + baseWidth - rightPadding), Math.round(y + baselineY + 1.0f), withAlpha(0xFFFFFF, 30));

        // Pass 1: ticks only on fixed world marks.
        for (int deg = 0; deg < 360; deg += 15) {
            float markYaw = deg;
            float delta = shortestAngleDeg(markYaw, directionDisplayYaw);
            float px = centerX + (delta / degreesPerPixel);
            if (px < leftPadding || px > baseWidth - rightPadding) continue;

            int alphaCenter = MathHelper.clamp(Math.round(255.0f * directionEdgeFade(px, baseWidth)), 0, 255);
            if (alphaCenter <= 4) {
                continue;
            }

            String label = directionLabel(deg, module.showIntermediate.isValue());
            if (label.isEmpty()) {
                int minorHalf = 0;
                context.fill(
                        Math.round(x + px - minorHalf),
                        Math.round(y + minorTickTop),
                        Math.round(x + px + minorHalf + 1.0f),
                        Math.round(y + minorTickBottom),
                        withAlpha(0xFFFFFF, Math.round(130.0f * alphaCenter / 255.0f))
                );
            } else {
                int majorHalf = 0;
                context.fill(
                        Math.round(x + px - majorHalf),
                        Math.round(y + majorTickTop),
                        Math.round(x + px + majorHalf + 1.0f),
                        Math.round(y + majorTickBottom),
                        withAlpha(0xFFFFFF, Math.round(210.0f * alphaCenter / 255.0f))
                );
            }
        }

        // Pass 2: labels.
        for (int deg = 0; deg < 360; deg += 15) {
            float markYaw = deg;
            float delta = shortestAngleDeg(markYaw, directionDisplayYaw);
            float px = centerX + (delta / degreesPerPixel);
            if (px < leftPadding || px > baseWidth - rightPadding) continue;

            String label = directionLabel(deg, module.showIntermediate.isValue());
            if (label.isEmpty()) {
                continue;
            }
            int alphaCenter = MathHelper.clamp(Math.round(255.0f * directionEdgeFade(px, baseWidth)), 0, 255);
            if (alphaCenter <= 4) {
                continue;
            }

            float tw = getHudTextWidth(client, label, HUD_TEXT_SIZE);
            float textLeft = px - tw * 0.5f;
            float textRight = textLeft + tw;
            int alphaLeft = MathHelper.clamp(Math.round(255.0f * directionEdgeFade(textLeft + 1.0f, baseWidth)), 0, 255);
            int alphaRight = MathHelper.clamp(Math.round(255.0f * directionEdgeFade(textRight - 1.0f, baseWidth)), 0, 255);
            int alpha = Math.min(alphaCenter, Math.min(alphaLeft, alphaRight));
            if (alpha <= 4) {
                continue;
            }

            if (textRight < leftPadding || textLeft > baseWidth - rightPadding) {
                continue;
            }
            renderScaledHudTextWithAlpha(context, client, label, x, y, textLeft, labelY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), alpha);
        }

        if (module.showDegreeNumber.isValue()) {
            String degree = String.valueOf(Math.round(directionDisplayYaw));
            float tw = getHudTextWidth(client, degree, HUD_TEXT_SIZE);
            renderScaledHudText(context, client, degree, x, y, centerX - tw * 0.5f, yawNumberY, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
            int triSize = 18;
            renderScaledHudTexture(context, x, y, centerX - triSize * 0.5f, triangleY, triSize, triSize, scale);
        }
        context.getMatrices().pop();
    }

    private static void renderScaledHudTexture(
            DrawContext context,
            float hudX,
            float hudY,
            float localX,
            float localY,
            int width,
            int height,
            float scale
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawTexture(RenderLayer::getGuiTextured, DIRECTION_TRIANGLE_TEXTURE, Math.round(localX), Math.round(localY), 0.0f, 0.0f, width, height, width, height);
        context.getMatrices().pop();
    }

    private void renderKeystrokesHud(
            DrawContext context,
            MinecraftClient client,
            KeystrokesHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled()) {
            return;
        }

        updateKeystroke(KEYSTROKE_W, client.options.forwardKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_A, client.options.leftKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_S, client.options.backKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_D, client.options.rightKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_LMB, mouseDown && client.currentScreen == null, deltaSeconds);
        boolean rightMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        updateKeystroke(KEYSTROKE_RMB, rightMouseDown && client.currentScreen == null, deltaSeconds);
        updateKeystroke(KEYSTROKE_SPACE, client.options.jumpKey.isPressed(), deltaSeconds);

        float baseWidth = 54.0f;
        float baseHeight = 63.0f;
        renderRectHud(context, client, module, "", HUD_KEYSTROKES, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        int idleColor = module.background.isValue() ? module.getResolvedBackgroundColor(client) : 0x00000000;

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z + 20.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderKeystrokeButtonBlur(context, module, scale);
        renderKeyButton(context, 20, 0, 16, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_W]);
        renderKeyButton(context, 0, 19, 18, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_A]);
        renderKeyButton(context, 19, 19, 16, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_S]);
        renderKeyButton(context, 36, 19, 18, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_D]);
        renderKeyButton(context, 0, 38, 54, 8, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE]);
        renderKeyButton(context, 0, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_LMB]);
        renderKeyButton(context, 28, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_RMB]);
        context.draw();
        context.getMatrices().pop();

        renderKeyLabel(context, client, "W", x, y, 20, 4, 16, KEYSTROKE_PROGRESS[KEYSTROKE_W], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "A", x, y, 0, 23, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "S", x, y, 19, 23, 16, KEYSTROKE_PROGRESS[KEYSTROKE_S], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "D", x, y, 36, 23, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D], scale, module.textShadow.isValue());
        renderSpacebarLabel(context, x, y, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE], scale);
        renderKeyLabel(context, client, "LMB", x, y, 0, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_LMB], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "RMB", x, y, 28, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_RMB], scale, module.textShadow.isValue());
        context.getMatrices().pop();
    }

    private void renderPotionHud(
            DrawContext context,
            MinecraftClient client,
            PotionHud module,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        if (!module.isEnabled() || client.player == null) {
            return;
        }

        if (!potionCacheInitialized || !chatEditing) {
            List<StatusEffectInstance> updatedEffects = new ArrayList<>(client.player.getStatusEffects());
            updatedEffects.sort(Comparator.comparing(effect -> effect.getEffectType().value().getName().getString()));
            boolean updatedSample = false;
            List<String> updatedNames = new ArrayList<>();
            List<String> updatedDurations = new ArrayList<>();
            if (updatedEffects.isEmpty()) {
                if (chatEditing) {
                    updatedSample = true;
                    updatedNames.add("Invisibility");
                    updatedDurations.add("02:14");
                }
            } else {
                for (StatusEffectInstance effect : updatedEffects) {
                    updatedNames.add(getEffectName(effect));
                    updatedDurations.add(formatEffectDuration(effect));
                }
            }
            potionEffectsCache = updatedEffects;
            potionNamesCache = updatedNames;
            potionDurationsCache = updatedDurations;
            potionSampleCache = updatedSample;
            potionCacheInitialized = true;
        }

        List<StatusEffectInstance> effects = potionEffectsCache;
        boolean sample = potionSampleCache;
        if (effects.isEmpty() && !sample) {
            return;
        }

        float rowHeight = 24.0f;
        float iconSize = 18.0f;
        float paddingX = 6.0f;
        float paddingY = 4.0f;
        // Effect-name column shifted 2 px to the left (was iconSize + 5,
        // now iconSize + 3) so the text sits closer to the icon and
        // gives the rect a tighter visual feel.
        float textX = paddingX + iconSize + 3.0f;
        int rows = sample ? 1 : effects.size();
        float maxTextWidth = 0.0f;
        for (int i = 0; i < rows; i++) {
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, potionNamesCache.get(i), HUD_TEXT_SIZE));
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, potionDurationsCache.get(i), HUD_TEXT_SIZE));
        }

        // Right side trimmed by 3 px (was paddingX, now paddingX - 3).
        // Min width also drops by 3 (94 -> 91) so the user-perceived
        // width matches across the empty / non-empty cases.
        float baseWidth = Math.max(91.0f, textX + maxTextWidth + paddingX - 3.0f);
        // Height: -1 from top (rect's top edge moves DOWN 1 px) and
        // -3 from bottom (rect's bottom edge moves UP 3 px). Total -4.
        // We achieve the top-edge shift via a matrix translate on Y by
        // +1, and the bottom-edge shift by feeding renderRectHud a
        // baseHeight reduced by 4. Content (icons + text) renders
        // outside this matrix scope so it stays at its original Y.
        float baseHeight = paddingY * 2.0f + rows * rowHeight;
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 1.0f, 0.0f);
        renderRectHud(context, client, module, "", HUD_POTION, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight - 4.0f);
        context.getMatrices().pop();

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);

        for (int i = 0; i < rows; i++) {
            float rowY = paddingY + i * rowHeight;
            context.getMatrices().push();
            context.getMatrices().translate(x, y, HUD_RENDER_Z + 25.0f);
            context.getMatrices().scale(scale, scale, 1.0f);
            if (sample) {
                context.drawItem(new ItemStack(Items.POTION), Math.round(paddingX), Math.round(rowY + 2.0f));
            } else {
                Sprite sprite = client.getStatusEffectSpriteManager().getSprite(effects.get(i).getEffectType());
                context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, Math.round(paddingX), Math.round(rowY + 2.0f), Math.round(iconSize), Math.round(iconSize));
            }
            context.draw();
            context.getMatrices().pop();
        }
        for (int i = 0; i < rows; i++) {
            float rowY = paddingY + i * rowHeight;
            String name = potionNamesCache.get(i);
            String duration = potionDurationsCache.get(i);

            // Color By Type: pick a tint for the effect NAME based
            // on the StatusEffect category (BENEFICIAL / HARMFUL /
            // NEUTRAL). Fixed colors so no extra picker UI; the
            // user toggles the whole behaviour with a single
            // checkbox. When sampling the empty list (chat-edit
            // preview) we have no live effect to query, so fall
            // back to plain white.
            int nameColor = 0xFFFFFFFF;
            if (module.colorByType.isValue() && !sample && i < potionEffectsCache.size()) {
                StatusEffectInstance effect = potionEffectsCache.get(i);
                StatusEffectCategory cat = effect.getEffectType().value().getCategory();
                if (cat == StatusEffectCategory.BENEFICIAL) nameColor = 0xFF55FF55;
                else if (cat == StatusEffectCategory.HARMFUL) nameColor = 0xFFFF5555;
            }

            // Flash-on-expiry: pulse the name's alpha when there's
            // less than 10 seconds left. Sin oscillator so the
            // change is smooth and breathes at ~5Hz.
            if (module.flashOnExpiry.isValue() && !sample && i < potionEffectsCache.size()) {
                StatusEffectInstance effect = potionEffectsCache.get(i);
                if (!effect.isInfinite() && effect.getDuration() < 200) {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 200_000_000.0));
                    int origAlpha = (nameColor >>> 24) & 0xFF;
                    int newAlpha = (int) (origAlpha * (0.45f + 0.55f * pulse));
                    nameColor = (newAlpha << 24) | (nameColor & 0x00FFFFFF);
                }
            }

            renderScaledHudTextColored(context, client, name, x, y, textX, rowY + 2.0f, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), nameColor);
            renderScaledHudText(context, client, duration, x, y, textX, rowY + 12.0f, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
        }

        context.getMatrices().pop();
    }

    private static String formatDurabilityText(ArmorHud module, ItemStack stack) {
        if (!stack.isDamageable()) {
            return "-";
        }
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
        return module.formatDurability(remaining, stack.getMaxDamage());
    }

    private static float getTextHudBaseWidth(MinecraftClient client, String text) {
        float width = Math.max(44.0f, getHudTextWidth(client, text, HUD_TEXT_SIZE) + 14.0f);
        // Add 2 pixels to background width when text contains "A"
        if (text.contains("A") || text.contains("a")) {
            width += 2.0f;
        }
        return width;
    }

    private static String getSprintHudText(MinecraftClient client) {
        // Sprint HUD now displays state without surrounding [] - the
        // qualifier sources (Key Held / AutoSprint / Vanilla) live in
        // round parens so the line reads naturally as one phrase.
        if (client.player == null || client.options == null) {
            return "Not Sprinting";
        }
        if (client.player.getAbilities().flying && client.options.sneakKey.isPressed()) {
            return "Flying Descending";
        }
        if (client.player.getAbilities().flying) {
            return "Flying";
        }
        if (client.options.sneakKey.isPressed() || client.player.isSneaking()) {
            return "Sneaking (Key Held)";
        }
        // AutoSprint label override: when the module is on AND its
        // {@code showInSprintHud} toggle is true, the label flips
        // from "Key Held" / "Vanilla" to "AutoSprint" so the user
        // can see the module is the source. We override the label
        // in any sprint-context (ground, in-air, falling) but never
        // when the player isn't sprinting at all - "Not Sprinting
        // (AutoSprint)" would be a lie.
        AutoSprint autoSprint = AutoSprint.getInstance();
        boolean autoSprintActive = autoSprint.isEnabled() && autoSprint.showInSprintHud.isValue();
        if (client.options.sprintKey.isPressed()) {
            return autoSprintActive ? "Sprinting (AutoSprint)" : "Sprinting (Key Held)";
        }
        if (client.player.isSprinting()) {
            return autoSprintActive ? "Sprinting (AutoSprint)" : "Sprinting (Vanilla)";
        }
        return "Not Sprinting";
    }

    private static String getDayCounterText(MinecraftClient client) {
        DayCounterHud module = DayCounterHud.getInstance();
        if (client.world == null) {
            // Idle / pre-world state: keep the legacy "0 Days" text in
            // the default order, but still respect the user's
            // {@code reverseOrder} preference so the HUD doesn't flip
            // formats the moment the world finishes loading.
            return module.reverseOrder.isValue() ? "Day: 0" : "0 Days";
        }
        long days = Math.max(0L, client.world.getTime() / 24000L) + 1;
        if (module.reverseOrder.isValue()) {
            // Label-prefixed, plain-number form. We deliberately drop
            // the singular/plural distinction here because "Day: 1"
            // already reads naturally regardless of the count.
            return "Day: " + days;
        }
        return days + (days == 1L ? " Day" : " Days");
    }

    private static String getTimeHudText(TimeHud module, MinecraftClient client) {
        LocalTime now = LocalTime.now();
        boolean is24 = module.hour24.isValue();
        boolean wantSeconds = module.showSeconds.isValue();
        String pattern;
        if (is24) {
            pattern = wantSeconds ? "H:mm:ss" : "H:mm";
        } else if (module.showAmPm.isValue()) {
            pattern = wantSeconds ? "h:mm:ss a" : "h:mm a";
        } else {
            pattern = wantSeconds ? "h:mm:ss" : "h:mm";
        }
        String time = now.format(DateTimeFormatter.ofPattern(pattern, Locale.US));
        if (module.showPhase.isValue() && client != null && client.world != null) {
            TimeHud.Phase phase = module.phaseForTime(client.world.getTimeOfDay());
            String label = module.colorPhase.isValue() ? phase.colorCode + phase.label + "§r" : phase.label;
            return time + " " + label;
        }
        return time;
    }

    private static String getSessionText(SessionTimeHud module) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - SESSION_START_MS);
        long ms = elapsed % 1000L;
        long totalSec = elapsed / 1000L;
        long sec = totalSec % 60L;
        long totalMin = totalSec / 60L;
        long min = totalMin % 60L;
        long hours = totalMin / 60L;
        String mode = module.displayOption.getSelected();
        return switch (mode) {
            case "12h 34m 56s" -> String.format(Locale.US, "%dh %dm %ds", hours, min, sec);
            case "123.456s" -> String.format(Locale.US, "%.3fs", elapsed / 1000.0);
            case "12:34" -> String.format(Locale.US, "%02d:%02d", hours, min);
            case "12:34:56.789" -> String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, min, sec, ms);
            default -> String.format(Locale.US, "%02d:%02d:%02d", hours, min, sec);
        };
    }

    private static float wrap360(float angle) {
        float a = angle % 360.0f;
        if (a < 0.0f) a += 360.0f;
        return a;
    }

    private static String wrapTextWithBrackets(String text, RectHudModule module) {
        if (module != null && !module.background.isValue() && module.showBrackets.isValue()) {
            return "[" + text + "]";
        }
        return text;
    }

    private static float shortestAngleDeg(float target, float current) {
        float diff = (target - current) % 360.0f;
        if (diff > 180.0f) diff -= 360.0f;
        if (diff < -180.0f) diff += 360.0f;
        return diff;
    }

    private static float approachAngle(float current, float target, float step) {
        float diff = shortestAngleDeg(target, current);
        return wrap360(current + diff * MathHelper.clamp(step, 0.0f, 1.0f));
    }

    private static String directionLabel(int deg, boolean showIntermediate) {
        return switch (deg) {
            case 0 -> "S";
            case 45 -> showIntermediate ? "SE" : "";
            case 90 -> "W";
            case 135 -> showIntermediate ? "SW" : "";
            case 180 -> "N";
            case 225 -> showIntermediate ? "NW" : "";
            case 270 -> "E";
            case 315 -> showIntermediate ? "NE" : "";
            default -> String.valueOf(deg);
        };
    }

    private static String getBiomeName(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return "Unknown";
        }
        return client.world.getBiome(pos).getKey()
                .map(key -> toTitleCase(key.getValue().getPath()))
                .orElse("Unknown");
    }

    private static int getBiomeColor(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return 0xFFFF55;
        }
        String biomeId = client.world.getBiome(pos).getKey()
                .map(key -> key.getValue().getPath())
                .orElse("");
        return switch (biomeId) {
            case "river", "frozen_river" -> 0x55AAFF;
            case "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "lukewarm_ocean", "deep_lukewarm_ocean" -> 0x3388DD;
            case "warm_ocean" -> 0x55CCEE;
            case "frozen_ocean", "deep_frozen_ocean" -> 0xAADDFF;
            case "desert" -> 0xFFDD55;
            case "beach" -> 0xFFEE88;
            case "snowy_beach" -> 0xDDEEFF;
            case "plains", "sunflower_plains" -> 0x88DD44;
            case "meadow" -> 0x77DD66;
            case "forest", "flower_forest" -> 0x44BB33;
            case "birch_forest", "old_growth_birch_forest" -> 0x88CC55;
            case "dark_forest" -> 0x336622;
            case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> 0x559944;
            case "snowy_taiga" -> 0xDDEEFF;
            case "snowy_plains", "ice_spikes", "snowy_slopes", "frozen_peaks" -> 0xFFFFFF;
            case "jungle", "bamboo_jungle", "sparse_jungle" -> 0x33BB22;
            case "savanna", "savanna_plateau", "windswept_savanna" -> 0xBBAA44;
            case "badlands", "eroded_badlands", "wooded_badlands" -> 0xDD7733;
            case "swamp", "mangrove_swamp" -> 0x668855;
            case "mushroom_fields" -> 0xCC77CC;
            case "stony_shore", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_peaks" -> 0xAAAAAA;
            case "grove" -> 0xBBDDAA;
            case "jagged_peaks" -> 0xCCDDEE;
            case "cherry_grove" -> 0xFFAACC;
            case "the_nether", "nether_wastes" -> 0xFF5533;
            case "soul_sand_valley" -> 0x774433;
            case "crimson_forest" -> 0xDD3344;
            case "warped_forest" -> 0x22BBAA;
            case "basalt_deltas" -> 0x888888;
            case "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands" -> 0xDDDD88;
            case "the_void" -> 0x555555;
            case "deep_dark" -> 0x224455;
            case "lush_caves", "dripstone_caves" -> 0x66BB55;
            default -> 0xFFFF55;
        };
    }

    private static String toTitleCase(String value) {
        String[] parts = value.replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private static String getFacingShort(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "";
        };
    }

    private static String getFacing8Point(float yaw) {
        float normalized = ((yaw % 360.0f) + 360.0f) % 360.0f;
        if (normalized >= 337.5f || normalized < 22.5f) return "S";
        if (normalized < 67.5f) return "SW";
        if (normalized < 112.5f) return "W";
        if (normalized < 157.5f) return "NW";
        if (normalized < 202.5f) return "N";
        if (normalized < 247.5f) return "NE";
        if (normalized < 292.5f) return "E";
        return "SE";
    }

    private static String getDirectionXSign(String facing) {
        return switch (facing) {
            case "E", "NE", "SE" -> "+";
            case "W", "NW", "SW" -> "-";
            default -> "";
        };
    }

    private static String getDirectionZSign(String facing) {
        return switch (facing) {
            case "S", "SE", "SW" -> "+";
            case "N", "NE", "NW" -> "-";
            default -> "";
        };
    }

    private static int getServerPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) {
            return 0;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : Math.max(0, entry.getLatency());
    }

    private static void updateKeystroke(int index, boolean pressed, float deltaSeconds) {
        KEYSTROKE_PROGRESS[index] = approachExp(KEYSTROKE_PROGRESS[index], pressed ? 1.0f : 0.0f, 16.0f, deltaSeconds);
    }

    private static void renderKeystrokeButtonBlur(DrawContext context, KeystrokesHud module, float scale) {
        if (!module.background.isValue()) {
            return;
        }
        float blurRadius = Math.max(0.0f, module.backgroundBlurRadius.getValue());
        if (blurRadius <= 0.0f) {
            return;
        }
        float safeScale = Math.max(scale, 1.0f);
        float normalizedBlurRadius = blurRadius / safeScale;
        float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
        if (blurQuality <= 0.0f) {
            return;
        }
        long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, 0.0f, 0.0f, 54.0f, 63.0f);
        Blur.INSTANCE.registerHudBlurState(HUD_KEYSTROKES, blurStateKey);
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 18.0f, 0.0f, 16.0f, 18.0f, blurQuality));  // W
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 0.0f, 19.0f, 16.0f, 18.0f, blurQuality));  // A
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 18.0f, 19.0f, 16.0f, 18.0f, blurQuality)); // S
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 36.0f, 19.0f, 18.0f, 18.0f, blurQuality)); // D
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 0.0f, 38.0f, 54.0f, 8.0f, blurQuality));   // Space
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 0.0f, 47.0f, 27.0f, 16.0f, blurQuality));  // LMB
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 27.0f, 47.0f, 27.0f, 16.0f, blurQuality)); // RMB
    }

    private static ShapeProperties createKeystrokeBlurRect(DrawContext context, float x, float y, float width, float height, float blurQuality) {
        return ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(0.0f)
                .softness(0.0f)
                .quality(blurQuality)
                .color(0xFFFFFFFF)
                .build();
    }
    private static void renderKeyButton(DrawContext context, int x, int y, int width, int height, int idleColor, float progress) {
        int activeOverlay = withAlpha(0xFFFFFF, Math.round(185.0f * progress));
        int color = blendARGB(idleColor, activeOverlay);
        context.fill(x, y, x + width, y + height, color);
    }

    private static void renderKeyLabel(
            DrawContext context,
            MinecraftClient client,
            String label,
            float hudX,
            float hudY,
            float keyX,
            float keyY,
            float keyWidth,
            float progress,
            float scale,
            boolean shadow
    ) {
        float textWidth = getHudTextWidth(client, label, HUD_TEXT_SIZE);
        int color = progress > 0.45f ? 0x111111 : 0xFFFFFF;
        renderScaledHudTextColored(context, client, label, hudX, hudY, keyX + (keyWidth - textWidth) * 0.5f, keyY, HUD_TEXT_SIZE, scale, shadow, color);
    }

    private static void renderSpacebarLabel(DrawContext context, float hudX, float hudY, float keyX, float keyY, float keyWidth, float keyHeight, float progress, float scale) {
        int color = progress > 0.45f ? 0x111111 : 0xFFFFFF;
        int lineColor = withAlpha(color, 200);
        float lineWidth = 14.0f;
        float lineThickness = 1.0f;
        float lx = keyX + (keyWidth - lineWidth) * 0.5f;
        float ly = keyY + 2.0f;
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.fill(Math.round(lx), Math.round(ly), Math.round(lx + lineWidth), Math.round(ly + lineThickness), lineColor);
        context.draw();
        context.getMatrices().pop();
    }

    private static String getKeyLabel(Text keyText) {
        String value = keyText == null ? "" : keyText.getString();
        if (value == null || value.isBlank()) {
            return "?";
        }
        if (value.length() > 3) {
            return value.substring(0, 3).toUpperCase(Locale.ROOT);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static String getEffectName(StatusEffectInstance effect) {
        String name = effect.getEffectType().value().getName().getString();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1) {
            name = name + " " + toRoman(amplifier + 1);
        }
        return name;
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    private static String formatEffectDuration(StatusEffectInstance effect) {
        if (effect.isInfinite()) {
            // Infinity glyph (U+221E) - reads instantly as "permanent"
            // and lines up nicely under the effect name.
            return "\u221E";
        }
        int totalSeconds = Math.max(0, effect.getDuration() / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static float getHudTextWidth(MinecraftClient client, String text, float textSize) {
        String cacheKey = "V|" + textSize + "|" + text;
        Float cached = HUD_TEXT_WIDTH_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        float width = client.textRenderer.getWidth(text);
        HUD_TEXT_WIDTH_CACHE.put(cacheKey, width);
        return width;
    }

    private static void renderHudTextWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float textSize, boolean shadow, int alpha) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, textSize, shadow, 0x00FFFFFF, alpha);
    }

    private static void renderHudTextColoredWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float textSize, boolean shadow, int rgbColor, int alpha) {
        int textColor = (MathHelper.clamp(alpha, 0, 255) << 24) | (rgbColor & 0x00FFFFFF);
        context.drawText(client.textRenderer, text, Math.round(x), Math.round(y), textColor, shadow);
    }

    private static float getOptimizedHudBlurQuality(float normalizedBlurRadius) {
        float radius = Math.max(0.0f, normalizedBlurRadius);
        if (radius < 0.10f) {
            return 0.0f;
        }

        // Soft compressed curve: the 0-32 slider changes blur gently, and max blur stays controlled.
        float t = MathHelper.clamp(radius / 32.0f, 0.0f, 1.0f);
        float strength = 0.42f + 0.33f * t;
        // User request: at slider 32 HUD blur must be exactly 3x weaker
        // than before, while preserving the same smooth response curve.
        return radius * strength * Theme.getInstance().getHudBlurQualityMultiplier() * (1.0f / 3.0f);
    }

    private static long makeHudBlurStateKey(float normalizedRadius, float safeScale, float x, float y, float width, float height) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h * 0x100000001B3L) ^ quantizeBlurKeyPart(normalizedRadius, 100.0f);
        h = (h * 0x100000001B3L) ^ quantizeBlurKeyPart(safeScale, 100.0f);
        h = (h * 0x100000001B3L) ^ quantizeBlurKeyPart(x, 1000.0f);
        h = (h * 0x100000001B3L) ^ quantizeBlurKeyPart(y, 1000.0f);
        h = (h * 0x100000001B3L) ^ quantizeBlurKeyPart(width, 1000.0f);
        h = (h * 0x100000001B3L) ^ quantizeBlurKeyPart(height, 1000.0f);
        return h;
    }

    private static long quantizeBlurKeyPart(float value, float precision) {
        return Math.round(value * precision);
    }

    private static void renderHudText(DrawContext context, MinecraftClient client, String text, float x, float y, float msdfSize, boolean shadow) {
        renderHudTextWithAlpha(context, client, text, x, y, msdfSize, shadow, 255);
    }

    private static void renderHudTextColored(DrawContext context, MinecraftClient client, String text, float x, float y, float msdfSize, boolean shadow, int rgbColor) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, msdfSize, shadow, rgbColor, 255);
    }

    private static void renderScaledHudText(
            DrawContext context,
            MinecraftClient client,
            String text,
            float hudX,
            float hudY,
            float textX,
            float textY,
            float textSize,
            float scale,
            boolean shadow
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderHudText(context, client, text, textX, textY, textSize, shadow);
        context.getMatrices().pop();
    }

    private static void renderScaledHudTextWithAlpha(
            DrawContext context,
            MinecraftClient client,
            String text,
            float hudX,
            float hudY,
            float textX,
            float textY,
            float textSize,
            float scale,
            boolean shadow,
            int alpha
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderHudTextWithAlpha(context, client, text, textX, textY, textSize, shadow, alpha);
        context.getMatrices().pop();
    }

    private static void renderScaledHudTextColored(
            DrawContext context,
            MinecraftClient client,
            String text,
            float hudX,
            float hudY,
            float textX,
            float textY,
            float textSize,
            float scale,
            boolean shadow,
            int rgbColor
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderHudTextColored(context, client, text, textX, textY, textSize, shadow, rgbColor);
        context.getMatrices().pop();
    }

    private static void updateClicksPerSecond(boolean mouseDown, boolean rightMouseDown, boolean gameplayInput) {
        if (!gameplayInput) {
            LEFT_CLICKS.clear();
            RIGHT_CLICKS.clear();
            wasLeftMouseDown = false;
            wasRightMouseDown = false;
            return;
        }

        long nowMs = System.currentTimeMillis();
        // Left click tracking (button 0)
        if (mouseDown && !wasLeftMouseDown) {
            LEFT_CLICKS.addLast(nowMs);
        }
        wasLeftMouseDown = mouseDown;

        while (!LEFT_CLICKS.isEmpty() && nowMs - LEFT_CLICKS.peekFirst() > 1000L) {
            LEFT_CLICKS.removeFirst();
        }

        // Right click tracking (button 1)
        if (rightMouseDown && !wasRightMouseDown) {
            RIGHT_CLICKS.addLast(nowMs);
        }
        wasRightMouseDown = rightMouseDown;

        while (!RIGHT_CLICKS.isEmpty() && nowMs - RIGHT_CLICKS.peekFirst() > 1000L) {
            RIGHT_CLICKS.removeFirst();
        }
    }

    private static boolean hasAnyHudEnabled() {
        return FpsHud.getInstance().isEnabled()
                || CpsHud.getInstance().isEnabled()
                || ReachHud.getInstance().isEnabled()
                || ArmorHud.getInstance().isEnabled()
                || SprintHud.getInstance().isEnabled()
                || CoordinatesHud.getInstance().isEnabled()
                || PingHud.getInstance().isEnabled()
                || KeystrokesHud.getInstance().isEnabled()
                || PotionHud.getInstance().isEnabled()
                || DayCounterHud.getInstance().isEnabled()
                || NametagHud.getInstance().isEnabled()
                || TimeHud.getInstance().isEnabled()
                || SessionTimeHud.getInstance().isEnabled()
                || MemoryHud.getInstance().isEnabled()
                || TpsHud.getInstance().isEnabled()
                || ComboCounterHud.getInstance().isEnabled()
                || ServerAddressHud.getInstance().isEnabled()
                // Missing entries caused the parent dispatch to early-return
                // when MovementSpeedHud or WailaHud was the only HUD enabled,
                // making the HUD silently invisible. They render through the
                // same renderBufferedHud path as the others, so they have to
                // be counted here to unblock the dispatch.
                || MovementSpeedHud.getInstance().isEnabled()
                || WailaHud.getInstance().isEnabled()
                || HealthIndicator.getInstance().isEnabled()
                || vorga.phazeclient.implement.features.modules.hud.Consumable.getInstance().isEnabled();
    }

    private static boolean isAnyHudInteractionActive() {
        return RECT_DRAGGING[HUD_FPS] || RECT_RESIZING[HUD_FPS]
                || RECT_DRAGGING[HUD_CPS] || RECT_RESIZING[HUD_CPS]
                || RECT_DRAGGING[HUD_REACH] || RECT_RESIZING[HUD_REACH]
                || RECT_DRAGGING[HUD_SPRINT] || RECT_RESIZING[HUD_SPRINT]
                || RECT_DRAGGING[HUD_COORDINATES] || RECT_RESIZING[HUD_COORDINATES]
                || RECT_DRAGGING[HUD_PING] || RECT_RESIZING[HUD_PING]
                || RECT_DRAGGING[HUD_KEYSTROKES] || RECT_RESIZING[HUD_KEYSTROKES]
                || RECT_DRAGGING[HUD_POTION] || RECT_RESIZING[HUD_POTION]
                || RECT_DRAGGING[HUD_DAY_COUNTER] || RECT_RESIZING[HUD_DAY_COUNTER]
                || RECT_DRAGGING[HUD_TAB] || RECT_RESIZING[HUD_TAB]
                || RECT_DRAGGING[HUD_NAMETAG] || RECT_RESIZING[HUD_NAMETAG]
                || RECT_DRAGGING[HUD_TIME] || RECT_RESIZING[HUD_TIME]
                || RECT_DRAGGING[HUD_SESSION] || RECT_RESIZING[HUD_SESSION]
                || RECT_DRAGGING[HUD_MEMORY] || RECT_RESIZING[HUD_MEMORY]
                || RECT_DRAGGING[HUD_TPS] || RECT_RESIZING[HUD_TPS]
                || RECT_DRAGGING[HUD_COMBO] || RECT_RESIZING[HUD_COMBO]
                || RECT_DRAGGING[HUD_SERVER_ADDRESS] || RECT_RESIZING[HUD_SERVER_ADDRESS]
                || RECT_DRAGGING[HUD_SCOREBOARD] || RECT_RESIZING[HUD_SCOREBOARD]
                || RECT_DRAGGING[HUD_HEALTH_INDICATOR] || RECT_RESIZING[HUD_HEALTH_INDICATOR]
                || RECT_DRAGGING[HUD_CONSUMABLE] || RECT_RESIZING[HUD_CONSUMABLE]
                || armorDragging || armorResizing;
    }

    private static void renderHudGuides(DrawContext context, float screenWidth, float screenHeight, float inverseGuiScale) {
        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);

        if (verticalGuideProgress > 0.01f) {
            int alpha = MathHelper.clamp(Math.round(GUIDE_MAX_ALPHA * verticalGuideProgress), 0, 255);
            int x = Math.round(screenWidth * 0.5f);
            context.fill(x, 0, x + 1, Math.round(screenHeight), withAlpha(0xFFFFFF, alpha));
        }

        if (horizontalGuideProgress > 0.01f) {
            int alpha = MathHelper.clamp(Math.round(GUIDE_MAX_ALPHA * horizontalGuideProgress), 0, 255);
            int y = Math.round(screenHeight * 0.5f);
            context.fill(0, y, Math.round(screenWidth), y + 1, withAlpha(0xFFFFFF, alpha));
        }

        context.getMatrices().pop();
    }

    private static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean isNearRect(double mouseX, double mouseY, float x, float y, float width, float height, float distance) {
        return mouseX >= x - distance
                && mouseX <= x + width + distance
                && mouseY >= y - distance
                && mouseY <= y + height + distance;
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = MathHelper.clamp(alpha, 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float directionEdgeFade(float x, float width) {
        float fadeWidth = Math.max(18.0f, width * 0.16f);
        float left = MathHelper.clamp((x - 4.0f) / fadeWidth, 0.0f, 1.0f);
        float right = MathHelper.clamp((width - 4.0f - x) / fadeWidth, 0.0f, 1.0f);
        return smoothStep(left) * smoothStep(right);
    }

    private static float smoothStep(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float approachExp(float current, float target, float speed, float dt) {
        float t = 1.0f - (float) Math.exp(-speed * dt);
        return current + (target - current) * t;
    }

    private static void drawOutlineNoOverlap(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            return;
        }
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        if (height > 2) {
            context.fill(x, y + 1, x + 1, y + height - 1, color);
            context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
        }
    }

    private static void drawOuterOutline(DrawContext context, float x, float y, float width, float height, int thickness, int color) {
        if (thickness <= 0) {
            return;
        }
        int innerLeft = MathHelper.floor(x);
        int innerTop = MathHelper.floor(y);
        int innerRight = MathHelper.ceil(x + width);
        int innerBottom = MathHelper.ceil(y + height);

        int outerLeft = innerLeft - thickness;
        int outerTop = innerTop - thickness;
        int outerRight = innerRight + thickness;
        int outerBottom = innerBottom + thickness;

        context.fill(outerLeft, outerTop, outerRight, innerTop, color);
        context.fill(outerLeft, innerBottom, outerRight, outerBottom, color);
        context.fill(outerLeft, innerTop, innerLeft, innerBottom, color);
        context.fill(innerRight, innerTop, outerRight, innerBottom, color);
    }

    private static int blendARGB(int base, int over) {
        float oa = ((over >>> 24) & 0xFF) / 255.0f;
        if (oa <= 0.0f) {
            return base;
        }
        float ba = ((base >>> 24) & 0xFF) / 255.0f;
        float outA = oa + ba * (1.0f - oa);
        if (outA <= 0.0f) {
            return 0;
        }

        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int or = (over >>> 16) & 0xFF;
        int og = (over >>> 8) & 0xFF;
        int ob = over & 0xFF;

        int outR = MathHelper.clamp(Math.round((or * oa + br * ba * (1.0f - oa)) / outA), 0, 255);
        int outG = MathHelper.clamp(Math.round((og * oa + bg * ba * (1.0f - oa)) / outA), 0, 255);
        int outB = MathHelper.clamp(Math.round((ob * oa + bb * ba * (1.0f - oa)) / outA), 0, 255);
        int outAlpha = MathHelper.clamp(Math.round(outA * 255.0f), 0, 255);
        return (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int approachColorExp(int current, int target, float speed, float dt) {
        float t = 1.0f - (float) Math.exp(-speed * dt);

        int ca = (current >>> 24) & 0xFF;
        int cr = (current >>> 16) & 0xFF;
        int cg = (current >>> 8) & 0xFF;
        int cb = current & 0xFF;

        int ta = (target >>> 24) & 0xFF;
        int tr = (target >>> 16) & 0xFF;
        int tg = (target >>> 8) & 0xFF;
        int tb = target & 0xFF;

        int a = MathHelper.clamp(Math.round(ca + (ta - ca) * t), 0, 255);
        int r = MathHelper.clamp(Math.round(cr + (tr - cr) * t), 0, 255);
        int g = MathHelper.clamp(Math.round(cg + (tg - cg) * t), 0, 255);
        int b = MathHelper.clamp(Math.round(cb + (tb - cb) * t), 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderMemoryHud(
            DrawContext context,
            MinecraftClient client,
            MemoryHud module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        renderRectHud(context, client, module, text, hudIndex,
                chatEditing, mouseX, mouseY, mouseDown, deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                getTextHudBaseWidth(client, text), BASE_HEIGHT);
    }

    private void renderTpsHud(
            DrawContext context,
            MinecraftClient client,
            TpsHud module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        renderRectHud(context, client, module, text, hudIndex,
                chatEditing, mouseX, mouseY, mouseDown, deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                getTextHudBaseWidth(client, text), BASE_HEIGHT);
    }

    private void renderComboHud(
            DrawContext context,
            MinecraftClient client,
            ComboCounterHud module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        renderRectHud(context, client, module, text, hudIndex,
                chatEditing, mouseX, mouseY, mouseDown, deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                getTextHudBaseWidth(client, text), BASE_HEIGHT);
    }

    private void renderServerAddressHud(
            DrawContext context,
            MinecraftClient client,
            ServerAddressHud module,
            String text,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        renderRectHud(context, client, module, text, hudIndex,
                chatEditing, mouseX, mouseY, mouseDown, deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                getTextHudBaseWidth(client, text), BASE_HEIGHT);

        // Server icon is drawn AFTER renderRectHud so it overlays nothing
        // important and so module.getHudX/Y read the post-clamp / post-
        // drag positions that renderRectHud has just written back. The
        // icon hugs the rect's left edge at the same Y and is sized 1:1
        // to the rect's rendered height (BASE_HEIGHT * scale), per the
        // user's spec - "to the left of the IP, square, same size as
        // the rect Y dimension".
        if (module.displayServerIcon.isValue()) {
            float scale = MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale());
            float hudHeight = BASE_HEIGHT * scale;
            module.renderServerIcon(context, module.getHudX(), module.getHudY(), hudHeight, inverseGuiScale);
        }
    }

    private String getWailaText(MinecraftClient client) {
        WailaHud waila = WailaHud.getInstance();
        if (client.player == null || client.world == null) {
            return waila.alwaysShow.isValue() ? "No target" : "";
        }

        HitResult hit = client.player.raycast(4.5, 0.0f, false);
        
        if (hit.getType() == HitResult.Type.MISS) {
            return waila.alwaysShow.isValue() ? "No target" : "";
        }

        StringBuilder sb = new StringBuilder();

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)hit).getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();

            // Block name (uses Minecraft language)
            String blockName = block.getName().getString();
            sb.append(blockName);

            // Coordinates formatted as "(x, y, z)" with parentheses and
            // signed integers to match the screenshot the user shared
            // (e.g. "(-101, 71, -298)"). Single line, no axis labels -
            // the parens make the role unambiguous at a glance.
            // Streamer Mode veto: the Hide Coordinates toggle suppresses
            // this line even when WAILA's own Show Coordinates setting
            // is on, so the privacy feature wins over the per-HUD
            // preference the user last chose for normal play.
            if (waila.showCoordinates.isValue()
                    && !StreamerMode.getInstance().isHideCoordinatesEnabled()) {
                sb.append("\n(").append(pos.getX())
                  .append(", ").append(pos.getY())
                  .append(", ").append(pos.getZ())
                  .append(')');
            }

            // Optimal-tool hint. Walks the vanilla mineable BlockTags
            // (each block carries at most one of pickaxe/axe/shovel/hoe;
            // sword is the special-case for cobweb / leaves). The tier
            // prefix comes from the needs_X_tool tags so a wood pick
            // on diamond ore correctly shows "Diamond Pickaxe" instead
            // of the bare "Pickaxe" - users expect to see why their
            // current tool fails to harvest, not just the family.
            if (waila.showCorrectTool.isValue()) {
                sb.append("\nCorrect Tool: ").append(phaze$resolveCorrectTool(state));
            }

            // Break time on separate line. Label changed from "Break:"
            // to "Break Time:" per user request - keeps parity with
            // "Correct Tool:" wording above and is less ambiguous than
            // a bare "Break:" which reads like a verb.
            if (waila.showBreakTime.isValue()) {
                if (client.player.isCreative()) {
                    sb.append("\nBreak Time: Instant");
                } else {
                    float hardness = state.getHardness(client.world, pos);
                    if (hardness <= 0) {
                        sb.append("\nBreak Time: Instant");
                    } else {
                        float breakSpeed = client.player.getBlockBreakingSpeed(state);
                        if (breakSpeed <= 0) {
                            sb.append("\nBreak Time: Never");
                        } else {
                            boolean canHarvest = client.player.canHarvest(state);
                            int divider = canHarvest ? 30 : 100;
                            float breakTime = hardness / breakSpeed * divider / 20.0f;
                            if (breakTime < 0.05f) {
                                sb.append("\nBreak Time: Instant");
                            } else {
                                sb.append(String.format("\nBreak Time: %.2fs", breakTime));
                            }
                        }
                    }
                }
            }

        } else if (hit.getType() == HitResult.Type.ENTITY && waila.showEntities.isValue()) {
            Entity entity = ((EntityHitResult)hit).getEntity();
            String entityName = entity.getName().getString();
            sb.append(entityName);
            
            // Add entity type info
            EntityType<?> entityType = entity.getType();
            sb.append("\nType: ").append(entityType.getName().getString());
        }

        return sb.toString();
    }

    /**
     * Resolves the best-fit tool family for {@code state} by consulting
     * the vanilla {@link BlockTags} the block has been registered under.
     *
     * <p>Vanilla's data-driven mining system uses one of four mutually-
     * exclusive {@code *_MINEABLE} tags per block plus an orthogonal
     * tier tag ({@code NEEDS_DIAMOND_TOOL} / {@code NEEDS_IRON_TOOL} /
     * {@code NEEDS_STONE_TOOL}). We walk these in priority order:
     * <ol>
     *   <li>If a tier tag matches, prefix it ({@code "Diamond "}, etc.).
     *       Wooden / golden / netherite blocks have no needs-tag because
     *       wood is the implicit floor and netherite never requires a
     *       higher-than-diamond tier, so an unmarked block falls through
     *       to the bare family name.</li>
     *   <li>Pickaxe / axe / shovel / hoe in tag-registered order.
     *       Sword fires last because {@code SWORD_EFFICIENT} is a
     *       cosmetic speed-up (cobweb, leaves) rather than a true
     *       requirement; if a block were somehow in both pickaxe and
     *       sword tags, the pickaxe win is the user-expected answer.</li>
     *   <li>Default "Hand" - dirt, sand, gravel, plants, etc. don't
     *       benefit from any tool above bare-fists in vanilla.</li>
     * </ol>
     */
    private static String phaze$resolveCorrectTool(BlockState state) {
        String tier = "";
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
            tier = "Diamond ";
        } else if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
            tier = "Iron ";
        } else if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) {
            tier = "Stone ";
        }
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return tier + "Pickaxe";
        if (state.isIn(BlockTags.AXE_MINEABLE))     return tier + "Axe";
        if (state.isIn(BlockTags.SHOVEL_MINEABLE))  return tier + "Shovel";
        if (state.isIn(BlockTags.HOE_MINEABLE))     return tier + "Hoe";
        if (state.isIn(BlockTags.SWORD_EFFICIENT))  return "Sword";
        return "Hand";
    }

    private ItemStack getWailaIcon(MinecraftClient client) {
        WailaHud waila = WailaHud.getInstance();
        if (!waila.showIcon.isValue()) {
            return null;
        }
        
        if (client.player == null || client.world == null) {
            return null;
        }

        HitResult hit = client.player.raycast(4.5, 0.0f, false);
        
        if (hit.getType() == HitResult.Type.MISS) {
            // Return barrier block icon for "No target"
            return new ItemStack(Items.BARRIER);
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)hit).getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();
            
            // Get block as item stack for icon
            Item item = block.asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        } else if (hit.getType() == HitResult.Type.ENTITY && waila.showEntities.isValue()) {
            Entity entity = ((EntityHitResult)hit).getEntity();
            
            // For item frames, show the held item
            if (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                net.minecraft.entity.decoration.ItemFrameEntity itemFrame = (net.minecraft.entity.decoration.ItemFrameEntity)entity;
                ItemStack heldItem = itemFrame.getHeldItemStack();
                if (!heldItem.isEmpty()) {
                    return heldItem;
                }
            }
            
            // Try to get spawn egg for entity type
            try {
                String entityName = entity.getType().getName().getString().toLowerCase().replace(" ", "_");
                Identifier spawnEggId = Identifier.of("minecraft", entityName + "_spawn_egg");
                Item spawnEgg = Registries.ITEM.get(spawnEggId);
                if (spawnEgg != null && spawnEgg != Items.AIR) {
                    return new ItemStack(spawnEgg);
                }
            } catch (Exception e) {
                // Fall through to default
            }
            
            // Default to barrier icon for entities
            return new ItemStack(Items.BARRIER);
        }

        return null;
    }

    private void renderWailaHud(
            DrawContext context,
            MinecraftClient client,
            WailaHud module,
            String text,
            ItemStack icon,
            int hudIndex,
            boolean chatEditing,
            double mouseX,
            double mouseY,
            boolean mouseDown,
            float deltaSeconds,
            float inverseGuiScale,
            float screenWidth,
            float screenHeight,
            float screenCenterX,
            float screenCenterY
    ) {
        float lineHeight = 10.0f;
        float scale = module.getHudScale();
        // Nominal icon size matches the original two-line-tall sprite. We
        // clamp it down per-frame against {@code baseHeight} so a small
        // rect (e.g. block name only, every sub-toggle disabled) doesn't
        // overflow the rect with a 18 px icon.
        float nominalIconSize = lineHeight * 2.0f / 1.1f;
        boolean noTarget = text.isEmpty() || text.contains("No target");

        // Split text into lines and compute base dimensions (without scale, like PotionHud)
        String[] lines = text.split("\n");
        float maxTextWidth = 0.0f;
        for (String line : lines) {
            if (!line.isEmpty()) {
                maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, line, HUD_TEXT_SIZE));
            }
        }

        // Height: vanilla two-line minimum for the "no target" pill so it
        // doesn't collapse into a sliver; otherwise hug the actual text
        // line count + 2 px breathing room. This is what makes the rect
        // grow / shrink automatically as the user toggles Show Break Time
        // / Show Coordinates / Show Correct Tool - fewer enabled options
        // -> fewer lines -> shorter rect.
        float baseHeight = noTarget ? 20.0f : lines.length * lineHeight + 2.0f;

        // Effective icon size: shrink so the icon always fits inside the
        // rect with 2 px of padding top + bottom. For a 4-line targeting
        // rect (42 px) the icon stays at its 18 px nominal; for a single-
        // line block-name-only rect (12 px) it shrinks to 8 px and still
        // sits cleanly in the centre. Floored at 8 px so the icon never
        // becomes a single pixel speck.
        float effectiveIconSize = icon != null
                ? Math.max(8.0f, Math.min(nominalIconSize, baseHeight - 4.0f))
                : 0.0f;
        float iconOffset = icon != null ? effectiveIconSize + 3.0f : 0.0f;

        float baseWidth = iconOffset + maxTextWidth;
        // Universal +4 px right-side padding so the WAILA text never sits
        // flush against the rect's right edge.
        baseWidth += 4.0f;
        // Compensate for the +4 px icon-X nudge applied below (see
        // {@code iconLocalX}). The text follows the icon and would
        // otherwise overshoot the rect's right edge by exactly 4 px;
        // padding the rect itself by +4 keeps both icon and text
        // proportionally inside.
        baseWidth += 4.0f;
        // Extra +2 px specifically for the "no target" pill - the empty
        // / "No target" copy is shorter than block-name text and the
        // user wants the rect to feel less cramped when nothing is
        // selected.
        if (noTarget) {
            baseWidth += 2.0f;
        } else {
            // And another +2 px on the targeting state per the user's
            // follow-up request: block-name text plus the optional
            // tool / break-time / coord lines were sitting flush
            // against the rect's right edge on long block names like
            // "Cracked Polished Blackstone Bricks", which read as a
            // visual cramp rather than a tight design choice.
            baseWidth += 2.0f;
        }

        // Visual top-edge lift for the targeting state. Vanilla anchors
        // the rect at {@code module.getHudY()} as its top-left, which
        // means we cannot grow the rect upward by changing baseHeight
        // alone (that grows it downward). Instead we apply a matrix
        // translate of {@code -4 px} on Y around the entire WAILA
        // sub-render (rect + icon + text), which visually moves the
        // top edge up by 4 px while leaving the saved hudY untouched.
        // Drag/resize hit-tests still use the saved coord so the user's
        // actual cursor target stays where they parked it.
        boolean liftTop = !noTarget;
        if (liftTop) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, -4.0F, 0.0F);
        }

        // renderRectHud handles scaling internally, pass base dimensions
        renderRectHud(context, client, module, "", hudIndex,
                chatEditing, mouseX, mouseY, mouseDown, deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                baseWidth, baseHeight);

        float x = module.getHudX();
        float y = module.getHudY();

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);

        // Horizontal nudge of the icon by +4 px in HUD-local space, in
        // both the "no target" and "targeting" states - shifts the icon
        // away from the rect's left edge so it has a bit of breathing
        // room. The text x-anchor still uses {@code iconOffset + 4} so
        // text follows the icon's new x without overlapping it.
        float iconLocalX = 1.0f + 4.0f;

        // Render icon - translate to its centred slot in HUD-local space
        // first, THEN scale the 16x16 sprite to {@code effectiveIconSize}.
        // Separating translate and scale lets us reason about position
        // (always centred vertically in {@code baseHeight}) without
        // worrying about how it interacts with the sprite-scale factor.
        if (icon != null) {
            float iconLocalY = (baseHeight - effectiveIconSize) * 0.5f;
            context.getMatrices().push();
            context.getMatrices().translate(x, y, HUD_RENDER_Z + 50.0f);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.getMatrices().translate(iconLocalX, iconLocalY, 0.0f);
            float iconDrawScale = effectiveIconSize / 16.0f;
            context.getMatrices().scale(iconDrawScale, iconDrawScale, 1.0f);
            // Disable blend to prevent blur from affecting icon
            RenderSystem.disableBlend();
            context.drawItem(icon, 0, 0);
            context.draw();
            // Re-enable blend so subsequent HUD elements (hotbar selection,
            // tab list, chat, etc.) render with proper alpha blending. Without
            // this the rest of the HUD inherits a blend-off state and looks
            // noticeably darker/desaturated until something else triggers a
            // RenderLayer state re-setup (e.g. a context.draw() flush).
            RenderSystem.enableBlend();
            context.getMatrices().pop();
        }

        // Text is centred vertically inside the rect for ALL states - both
        // "no target" and "targeting", regardless of how many sub-toggles
        // are enabled. This is the single source of truth requested: "в
        // любых вкл/выкл оно должно быть нормального размера и по центру
        // ректа по высоте". The "no target" pill receives an extra +1 px
        // downward nudge per the user's typography preference - the
        // single-word "No target" copy reads visually higher than the
        // optical centre, so the +1 brings it down to the perceptual
        // middle of the pill. The targeting state was later given the
        // same +1 nudge: block-name + tool/break/coord text was reading
        // visually higher than centre on multi-line rects because the
        // capital-letter baseline of vanilla's font sits slightly above
        // the geometric line midpoint, so the +1 brings the perceived
        // text mass into the rect's optical centre.
        float totalTextHeight = lines.length * lineHeight;
        float verticalOffset = (baseHeight - totalTextHeight) * 0.5f;
        if (noTarget) {
            verticalOffset += 1.0f;
        } else {
            verticalOffset += 1.0f;
        }
        // Text x-anchor follows the icon's new shifted column so the
        // gap between icon and text stays constant. Original layout was
        // {@code iconOffset + 4}; with the icon nudged by +4 px we add
        // the same +4 to the text so the visual relationship between
        // them is preserved.
        float textXOffset = iconOffset + 4.0f + 4.0f;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            renderScaledHudText(context, client, line, x, y, textXOffset, verticalOffset + (i * lineHeight), HUD_TEXT_SIZE, scale, true);
        }

        context.getMatrices().pop();

        if (liftTop) {
            context.getMatrices().pop();
        }
    }

    private void renderZoomLevel(DrawContext context, MinecraftClient client, float screenWidth, float screenHeight) {
        if (!Zoom.getInstance().isShowCurrentZoom() || !Zoom.isZoomActive()) {
            return;
        }

        float currentZoom = Zoom.getInstance().getCurrentZoomLevel();
        String zoomText = String.format("%.1f", currentZoom);
        String displayText = zoomText + "x";

        float textWidth = client.textRenderer.getWidth(displayText);
        float x = (screenWidth - textWidth) / 2.0f;
        float y = screenHeight - 50.0f;

        context.drawText(client.textRenderer, Text.literal(displayText), (int)x, (int)y, 0xFFFFFFFF, true);
    }

    // ====================================================================
    // ===== Merged sibling InGameHud mixins ==============================
    // ====================================================================
    //
    // The following blocks were previously separate @Mixin(InGameHud.class)
    // files. Each block keeps the original injectors verbatim with a unique
    // method name and {@code phaze$<feature>} field prefix. Field names
    // that collided across the originals (e.g. {@code phaze$dragging} used
    // by both InventoryHud and PlayerModel) are renamed per feature.
    //
    // Mergers:
    //   1. Saturation (renderStatusBars TAIL + renderAirBubbles HEAD/RETURN)
    //   2. Cooldowns (renderHotbarItem TAIL)
    //   3. HotbarSlide (renderHotbar HEAD/INVOKE/RETURN)
    //   4. MaceIndicator (renderHotbar TAIL)
    //   5. ScoreboardNickHider (renderScoreboardSidebar @ModifyArg)
    //   6. TabSlide (render TAIL)
    //   7. ItemHighlighter (renderHotbar TAIL)
    //   8. PlayerModel (render TAIL)
    //   9. InventoryHud (render TAIL)
    //   10. HealingHelper (renderHotbar TAIL)
    //   11. HotbarFlush (renderHotbar TAIL)
    //   12. Crosshair (renderCrosshair @Redirect on Perspective.isFirstPerson)

    // ---------- TabSlide shadows ----------
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private PlayerListHud playerListHud;

    // ---------- Saturation state ----------
    @Unique private float phaze$satUnclampedFlashAlpha = 0f;
    @Unique private float phaze$satFlashAlpha = 0f;
    @Unique private byte phaze$satAlphaDir = 1;
    @Unique private final SaturationOffsetsCache phaze$satBarOffsets = new SaturationOffsetsCache();
    @Unique private final SaturationHeldFoodCache phaze$satHeldFood = new SaturationHeldFoodCache();
    @Unique private boolean phaze$bubblesLifted = false;
    @Unique private static final int PHAZE_BUBBLE_LIFT_PX = 10;

    // ---------- HotbarSlide state ----------
    @Unique private static final int PHAZE_HOTBAR_SLOT_PX = 20;
    @Unique private static final int PHAZE_HOTBAR_SLOTS = 9;
    @Unique private static final int PHAZE_HOTBAR_PIXEL_W = PHAZE_HOTBAR_SLOT_PX * PHAZE_HOTBAR_SLOTS;
    @Unique private static final int PHAZE_HOTBAR_BG_W = 182;
    @Unique private static final int PHAZE_HOTBAR_BG_H = 22;
    @Unique private float phaze$hotbarCurrentSlotX = 0.0F;
    @Unique private int phaze$hotbarLastSelected = -1;
    @Unique private long phaze$hotbarLastFrameNanos = 0L;
    @Unique private boolean phaze$hotbarShouldDrawMirror = false;
    @Unique private int phaze$hotbarMirrorOffsetX = 0;
    @Unique private int phaze$hotbarLastDrawX;
    @Unique private int phaze$hotbarLastDrawY;
    @Unique private int phaze$hotbarLastWidth;
    @Unique private int phaze$hotbarLastHeight;
    @Unique private Identifier phaze$hotbarLastTexture;
    @Unique private Function<Identifier, ?> phaze$hotbarLastSpriteFn;
    @Unique private boolean phaze$hotbarScissorOn = false;

    // ---------- TabSlide state ----------
    @Unique private boolean phaze$tabWasOpenedThisCycle = false;

    // ---------- PlayerModel drag state ----------
    @Unique private static boolean phaze$pmDragging = false;
    @Unique private static float phaze$pmDragOffsetX = 0.0F;
    @Unique private static float phaze$pmDragOffsetY = 0.0F;
    @Unique private static boolean phaze$pmWasMouseDown = false;

    // ---------- InventoryHud drag state ----------
    @Unique private static final int PHAZE_INV_SLOT = 18;
    @Unique private static final int PHAZE_INV_ICON = 16;
    @Unique private static final int PHAZE_INV_PADDING = 1;
    @Unique private static boolean phaze$invDragging = false;
    @Unique private static float phaze$invDragOffsetX = 0.0F;
    @Unique private static float phaze$invDragOffsetY = 0.0F;
    @Unique private static boolean phaze$invWasMouseDown = false;

    // ====================================================================
    // 1) Saturation: renderStatusBars TAIL + renderAirBubbles HEAD/RETURN
    // ====================================================================

    @Inject(method = "renderStatusBars", at = @At("TAIL"))
    private void phaze$onSaturationRenderStatusBars(DrawContext context, CallbackInfo ci) {
        if (!Saturation.getInstance().isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        PlayerEntity player = mc.player;
        HungerManager stats = player.getHungerManager();
        float saturationLevel = stats.getSaturationLevel();
        if (saturationLevel <= 0) {
            phaze$resetSaturationFlash();
            return;
        }

        int right = context.getScaledWindowWidth() / 2 + 91;
        int top = context.getScaledWindowHeight() - 39;
        phaze$drawSaturationOverlay(context, saturationLevel, 0, 1.0F, mc.inGameHud.getTicks(), right, top);

        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) heldItem = player.getOffHandStack();
        if (heldItem.isEmpty() || !heldItem.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
            phaze$resetSaturationFlash();
            return;
        }
        phaze$satOnClientTick();
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"))
    private void phaze$liftAirBubblesHead(DrawContext context, PlayerEntity player,
                                          int heartCount, int maxAirBubbles, int top,
                                          CallbackInfo ci) {
        if (phaze$shouldLiftBubbles()) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, -PHAZE_BUBBLE_LIFT_PX, 0.0F);
            phaze$bubblesLifted = true;
        }
    }

    @Inject(method = "renderAirBubbles", at = @At("RETURN"))
    private void phaze$liftAirBubblesReturn(DrawContext context, PlayerEntity player,
                                            int heartCount, int maxAirBubbles, int top,
                                            CallbackInfo ci) {
        if (phaze$bubblesLifted) {
            context.getMatrices().pop();
            phaze$bubblesLifted = false;
        }
    }

    @Unique
    private static boolean phaze$shouldLiftBubbles() {
        Saturation sat = Saturation.getInstance();
        if (sat == null || !sat.isEnabled()) return false;
        return "Second Hunger Bar".equals(sat.style.getSelected());
    }

    @Unique
    private void phaze$drawSaturationOverlay(DrawContext context, float saturationLevel, float saturationGained, float alpha, int guiTicks, int right, int top) {
        if (saturationLevel + saturationGained < 0) return;
        int alphaColor = ColorHelper.argbFromRGBA(1.0F, 1.0F, 1.0F, alpha);
        float modifiedSaturation = Math.max(0, Math.min(saturationLevel + saturationGained, 20));
        int startSaturationBar = 0;
        int endSaturationBar = (int) Math.ceil(modifiedSaturation / 2.0F);
        if (saturationGained != 0) startSaturationBar = (int) Math.max(saturationLevel / 2.0F, 0);

        int iconSize = 9;
        int saturationTop = top - 10;
        String styleValue = Saturation.getInstance().style.getSelected();
        boolean isYellowBar = styleValue.equals("Yellow Bar");

        for (int i = startSaturationBar; i < endSaturationBar; ++i) {
            int x = right - i * 8 - 9;
            int y = saturationTop;
            float effectiveSaturationOfBar = (modifiedSaturation / 2.0F) - i;

            if (!isYellowBar) {
                if (effectiveSaturationOfBar <= 0) continue;
                Identifier foodTexture = null;
                if (effectiveSaturationOfBar >= 1) {
                    foodTexture = TextureHelper.FOOD_FULL_TEXTURE;
                } else if (effectiveSaturationOfBar >= .5) {
                    foodTexture = TextureHelper.FOOD_HALF_TEXTURE;
                }
                if (foodTexture == null) continue;
                context.drawGuiTexture(RenderLayer::getGuiTextured, TextureHelper.FOOD_EMPTY_TEXTURE, x, y, iconSize, iconSize);
                context.drawGuiTexture(RenderLayer::getGuiTextured, foodTexture, x, y, iconSize, iconSize);
            } else {
                int hungerY = top;
                int v = 0;
                int u = 0;
                if (effectiveSaturationOfBar >= 1) u = 3 * iconSize;
                else if (effectiveSaturationOfBar > .5) u = 2 * iconSize;
                else if (effectiveSaturationOfBar > .25) u = 1 * iconSize;
                context.drawTexture(RenderLayer::getGuiTextured, TextureHelper.MOD_ICONS, x, hungerY, u, v, iconSize, iconSize, 256, 256, alphaColor);
            }
        }
    }

    @Unique
    private void phaze$satOnClientTick() {
        phaze$satUnclampedFlashAlpha += phaze$satAlphaDir * 0.125F;
        if (phaze$satUnclampedFlashAlpha >= 1.5F) phaze$satAlphaDir = -1;
        else if (phaze$satUnclampedFlashAlpha <= -0.5F) phaze$satAlphaDir = 1;
        phaze$satFlashAlpha = Math.max(0F, Math.min(1F, phaze$satUnclampedFlashAlpha));
    }

    @Unique
    private void phaze$resetSaturationFlash() {
        phaze$satUnclampedFlashAlpha = phaze$satFlashAlpha = 0;
        phaze$satAlphaDir = 1;
    }

    @Unique
    private static class SaturationOffsetsCache {
        private final Vector<IntPoint> foodBarOffsets = new Vector<>();
        private int lastGuiTick = 0;
        private final Random random = new Random();

        private void generate(int guiTicks, PlayerEntity player) {
            final int preferFoodBars = 10;
            if (foodBarOffsets.size() != preferFoodBars) foodBarOffsets.setSize(preferFoodBars);
            random.setSeed((long) (guiTicks * 312871));
            for (int i = 0; i < preferFoodBars; ++i) {
                int x = -(i * 8) - 9;
                int y = 0;
                IntPoint point = foodBarOffsets.get(i);
                if (point == null) {
                    point = new IntPoint();
                    foodBarOffsets.set(i, point);
                }
                point.x = x;
                point.y = y;
            }
            lastGuiTick = guiTicks;
        }

        public Vector<IntPoint> foodBarOffsets(int guiTicks, PlayerEntity player) {
            if (guiTicks != lastGuiTick) generate(guiTicks, player);
            return this.foodBarOffsets;
        }
    }

    @Unique
    private static class SaturationHeldFoodCache {
        private ItemStack lastHeldItem;
        private int lastGuiTick = 0;

        public ItemStack result(int guiTick, PlayerEntity player) {
            if (guiTick != lastGuiTick) {
                ItemStack heldItem = player.getMainHandStack();
                if (heldItem.isEmpty()) heldItem = player.getOffHandStack();
                lastHeldItem = heldItem;
                lastGuiTick = guiTick;
            }
            return lastHeldItem;
        }
    }

    // ====================================================================
    // 2) Cooldowns: renderHotbarItem TAIL
    // ====================================================================

    @Inject(
            method = "renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V",
            at = @At("TAIL")
    )
    private void phaze$drawCooldownNumber(
            DrawContext context, int x, int y, RenderTickCounter tickCounter,
            PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci
    ) {
        Cooldowns module = Cooldowns.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (stack == null || stack.isEmpty() || player == null) return;
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (manager == null) return;
        float tickDelta = tickCounter.getTickDelta(false);
        float progress = manager.getCooldownProgress(stack, tickDelta);
        if (progress <= 0.0F) return;

        Identifier groupId = manager.getGroup(stack);
        ItemCooldownManager.Entry entry = manager.entries.get(groupId);
        if (entry == null) return;
        float remainingTicks = entry.endTick - (manager.tick + tickDelta);
        if (remainingTicks <= 0.0F) return;
        float remainingSeconds = remainingTicks / 20.0F;

        String text = module.formatSeconds(remainingSeconds);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;
        int color = module.colorForProgress(progress);
        int textWidth = mc.textRenderer.getWidth(text);
        int drawX = x + 8 - textWidth / 2;
        // Anchor lifted 3px from the previous y+4 position so the
        // digits sit near the top portion of the slot but still
        // overlap the held-item icon. {@code DrawContext.drawText}
        // is invoked at TAIL of {@code renderHotbarItem}, so the
        // text already draws AFTER the item glyph in z-order - no
        // extra translate is needed for it to read as "on top".
        int drawY = y + 1;
        // Push the matrix forward in Z so the digits land in front of
        // the held item glyph. {@code DrawContext.drawText} batches
        // text under its own draw layer that vanilla normally sorts
        // BEHIND the 3D item model (the item runs through
        // {@code ItemRenderer} which has its own depth contribution).
        // Translating +200 in Z is the same trick vanilla uses for
        // the stack-count label inside {@code drawItem}, so we copy
        // it here to stay above the icon without touching depth
        // state globally.
        net.minecraft.client.util.math.MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0F, 0.0F, 200.0F);
        context.drawText(mc.textRenderer, text, drawX, drawY, color, module.textShadow.isValue());
        // Flush any pending text batches under this z so the pop
        // doesn't leave the digits stuck behind subsequent
        // post-hotbar overlays.
        context.draw();
        matrices.pop();
    }

    // ====================================================================
    // 3) HotbarSlide: renderHotbar HEAD + scissor + ModifyArgs + after
    // ====================================================================

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void phaze$tickHotbarSlide(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        phaze$hotbarShouldDrawMirror = false;
        phaze$hotbarScissorOn = false;

        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) {
            phaze$hotbarLastSelected = -1;
            phaze$hotbarLastFrameNanos = 0L;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        int selected = mc.player.getInventory().selectedSlot;
        float target = selected * PHAZE_HOTBAR_SLOT_PX;

        if (module.isHotbarRolloverEnabled() && phaze$hotbarLastSelected >= 0) {
            int slotDelta = selected - phaze$hotbarLastSelected;
            if (slotDelta >= 5) phaze$hotbarCurrentSlotX += PHAZE_HOTBAR_PIXEL_W;
            else if (slotDelta <= -5) phaze$hotbarCurrentSlotX -= PHAZE_HOTBAR_PIXEL_W;
        }
        phaze$hotbarLastSelected = selected;

        long now = System.nanoTime();
        float dt;
        if (phaze$hotbarLastFrameNanos == 0L) dt = 1.0F / 60.0F;
        else {
            dt = (now - phaze$hotbarLastFrameNanos) / 1_000_000_000.0F;
            if (dt > 0.25F) dt = 0.25F;
        }
        phaze$hotbarLastFrameNanos = now;

        float smoothness = module.smoothnessForSpeed(module.hotbarSpeed.getValue());
        float decay = (float) Math.pow(smoothness, dt);
        phaze$hotbarCurrentSlotX = (phaze$hotbarCurrentSlotX - target) * decay + target;
        if (Math.abs(phaze$hotbarCurrentSlotX - target) < 0.4F) phaze$hotbarCurrentSlotX = target;

        if (module.isHotbarRolloverEnabled()) {
            float maxNormalX = (PHAZE_HOTBAR_SLOTS - 1) * PHAZE_HOTBAR_SLOT_PX;
            if (phaze$hotbarCurrentSlotX < 0.0F) {
                phaze$hotbarShouldDrawMirror = true;
                phaze$hotbarMirrorOffsetX = PHAZE_HOTBAR_PIXEL_W;
            } else if (phaze$hotbarCurrentSlotX > maxNormalX) {
                phaze$hotbarShouldDrawMirror = true;
                phaze$hotbarMirrorOffsetX = -PHAZE_HOTBAR_PIXEL_W;
            }
        }
    }

    @Inject(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1, shift = At.Shift.BEFORE)
    )
    private void phaze$openMirrorScissor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!phaze$hotbarShouldDrawMirror) return;
        context.draw();
        int hotbarLeft = context.getScaledWindowWidth() / 2 - PHAZE_HOTBAR_BG_W / 2;
        int hotbarTop = context.getScaledWindowHeight() - PHAZE_HOTBAR_BG_H;
        context.enableScissor(hotbarLeft, hotbarTop, hotbarLeft + PHAZE_HOTBAR_BG_W, hotbarTop + PHAZE_HOTBAR_BG_H);
        phaze$hotbarScissorOn = true;
    }

    @ModifyArgs(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1)
    )
    private void phaze$slideSelectionX(Args args) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        int selectedSlot = mc.player.getInventory().selectedSlot;
        int origX = args.<Integer>get(2);
        int baseX = origX - selectedSlot * PHAZE_HOTBAR_SLOT_PX;
        int newX = baseX + Math.round(phaze$hotbarCurrentSlotX);
        args.set(2, newX);

        if (phaze$hotbarShouldDrawMirror) {
            phaze$hotbarLastDrawX = newX;
            phaze$hotbarLastDrawY = args.<Integer>get(3);
            phaze$hotbarLastWidth = args.<Integer>get(4);
            phaze$hotbarLastHeight = args.<Integer>get(5);
            phaze$hotbarLastTexture = args.<Identifier>get(1);
            phaze$hotbarLastSpriteFn = args.<Function<Identifier, ?>>get(0);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1, shift = At.Shift.AFTER)
    )
    private void phaze$drawMirrorAndCloseScissor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!phaze$hotbarShouldDrawMirror || phaze$hotbarLastTexture == null || phaze$hotbarLastSpriteFn == null) {
            if (phaze$hotbarScissorOn) {
                context.draw();
                context.disableScissor();
                phaze$hotbarScissorOn = false;
            }
            return;
        }
        int mirrorX = phaze$hotbarLastDrawX + phaze$hotbarMirrorOffsetX;
        context.drawGuiTexture(
                (Function<Identifier, RenderLayer>) phaze$hotbarLastSpriteFn,
                phaze$hotbarLastTexture, mirrorX, phaze$hotbarLastDrawY,
                phaze$hotbarLastWidth, phaze$hotbarLastHeight);
        context.draw();
        if (phaze$hotbarScissorOn) {
            context.disableScissor();
            phaze$hotbarScissorOn = false;
        }
        phaze$hotbarShouldDrawMirror = false;
    }

    // ====================================================================
    // 4) MaceIndicator: renderHotbar TAIL — uses shared paint helper below
    // ====================================================================

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarMaceHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MaceIndicator module = MaceIndicator.getInstance();
        if (module == null || !module.isEnabled()) return;
        phaze$paintHotbarFills(context, module::colorForStack);
    }

    // ====================================================================
    // 5) ScoreboardNickHider: ModifyArg on drawText inside sidebar render
    // ====================================================================

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I")
    )
    private Text phaze$hideOwnNickInSidebar(Text original) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) return original;
        return hider.rewrite(original);
    }

    // ====================================================================
    // 6) TabSlide: render TAIL drives the slide animation when key released
    // ====================================================================

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$tabSlideTick(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (this.client == null || this.client.options == null) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (this.client.options.hudHidden) {
            module.snapTabClosed();
            phaze$tabWasOpenedThisCycle = false;
            return;
        }

        boolean keyPressed = this.client.options.playerListKey.isPressed();
        module.tickTabSlide(keyPressed);

        if (keyPressed) {
            if (phaze$vanillaTabListWouldRender()) phaze$tabWasOpenedThisCycle = true;
            return;
        }
        if (!module.isTabSlideRendering(false)) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (!phaze$tabWasOpenedThisCycle) return;

        this.playerListHud.setVisible(true);
        Scoreboard scoreboard = this.client.world == null ? null : this.client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null
                : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
    }

    @Unique
    private boolean phaze$vanillaTabListWouldRender() {
        if (this.client == null || this.client.player == null) return false;
        if (!this.client.isInSingleplayer()) return true;
        if (this.client.player.networkHandler != null
                && this.client.player.networkHandler.getListedPlayerListEntries().size() > 1) return true;
        if (this.client.world != null) {
            Scoreboard scoreboard = this.client.world.getScoreboard();
            if (scoreboard != null
                    && scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) != null) return true;
        }
        return false;
    }

    // ====================================================================
    // 7) ItemHighlighter: renderHotbar TAIL — shared paint helper
    // ====================================================================

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarItemHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ItemHighlighter module = ItemHighlighter.getInstance();
        if (module == null || !module.isEnabled()) return;
        phaze$paintHotbarFills(context, module::colorForStack);
    }

    // ====================================================================
    // 10) HealingHelper: renderHotbar TAIL — shared paint helper
    // ====================================================================

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarHealingHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HealingHelper module = HealingHelper.getInstance();
        if (module == null || !module.isEnabled()) return;
        phaze$paintHotbarFills(context, module::colorForStack);
    }

    /**
     * Shared 9-slot hotbar fill helper for HealingHelper, ItemHighlighter
     * and MaceIndicator. Walks the player's hotbar, calls the per-stack
     * colour function, and fills the slot when alpha is non-zero.
     */
    @Unique
    private void phaze$paintHotbarFills(DrawContext context,
                                        java.util.function.ToIntFunction<ItemStack> colorFn) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        PlayerEntity player = mc.player;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int centerX = screenW / 2;
        int itemY = screenH - 16 - 3;

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int n = 0; n < 9; n++) {
            ItemStack stack = player.getInventory().main.get(n);
            int color = colorFn.applyAsInt(stack);
            if ((color & 0xFF000000) == 0) continue;
            int itemX = centerX - 90 + n * 20 + 2;
            context.fill(itemX, itemY, itemX + 16, itemY + 16, color);
        }
        context.draw();
        phaze$resetGuiRenderState();
    }

    // ====================================================================
    // 11) HotbarFlush: renderHotbar TAIL — flush vertex batches
    // ====================================================================

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$flushHotbarBatch(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        context.draw();
        phaze$resetGuiRenderState();
    }

    // ====================================================================
    // 8) PlayerModel: render TAIL
    // ====================================================================

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawPlayerModel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerModelHud module = PlayerModelHud.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        if (mc.options.hudHidden) return;

        ClientPlayerEntity player = mc.player;
        float scale = module.getHudScale();
        int size = Math.round(module.modelSize.getValue());
        float panelW = 2.0F * size * scale;
        float panelH = 2.5F * size * scale;

        boolean chatEditing = mc.currentScreen instanceof ChatScreen;
        boolean mouseDown = chatEditing && GLFW.glfwGetMouseButton(
                mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double scaleFactor = mc.getWindow().getScaleFactor();
        if (scaleFactor <= 0.0) scaleFactor = 1.0;
        float mouseX = (float) (mc.mouse.getX() / scaleFactor);
        float mouseY = (float) (mc.mouse.getY() / scaleFactor);

        int scaledScreenW = mc.getWindow().getScaledWidth();
        int scaledScreenH = mc.getWindow().getScaledHeight();
        float maxX = Math.max(0.0F, scaledScreenW - panelW);
        float maxY = Math.max(0.0F, scaledScreenH - panelH);

        float panelX = MathHelper.clamp(module.getHudX(), 0.0F, maxX);
        float panelY = MathHelper.clamp(module.getHudY(), 0.0F, maxY);
        module.setHudX(panelX);
        module.setHudY(panelY);

        if (chatEditing) {
            boolean inside = mouseX >= panelX && mouseX <= panelX + panelW
                    && mouseY >= panelY && mouseY <= panelY + panelH;
            if (mouseDown && !phaze$pmWasMouseDown && inside) {
                phaze$pmDragging = true;
                phaze$pmDragOffsetX = mouseX - panelX;
                phaze$pmDragOffsetY = mouseY - panelY;
            }
            if (!mouseDown) phaze$pmDragging = false;
            if (phaze$pmDragging) {
                panelX = MathHelper.clamp(mouseX - phaze$pmDragOffsetX, 0.0F, maxX);
                panelY = MathHelper.clamp(mouseY - phaze$pmDragOffsetY, 0.0F, maxY);
                module.setHudX(panelX);
                module.setHudY(panelY);
            }
        } else {
            phaze$pmDragging = false;
        }
        phaze$pmWasMouseDown = mouseDown;

        float centerX = panelX + size;
        float centerY = panelY + size * 2.0F;

        Vector3f translation = new Vector3f();
        Quaternionf bodyRotation;
        Quaternionf headRotation = null;

        String mode = module.mode.getSelected();
        if ("Follow Mouse".equalsIgnoreCase(mode) && !chatEditing) {
            float mx = (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
            float my = (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
            float dx = (centerX - mx) / 50.0F;
            float dy = (centerY - my * 0.5F - size) / 50.0F;
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI);
            headRotation = new Quaternionf().rotateX(dy * 20.0F * (float) (Math.PI / 180.0));
            bodyRotation.mul(new Quaternionf().rotateY(dx * 20.0F * (float) (Math.PI / 180.0)));
        } else if ("Auto Rotate".equalsIgnoreCase(mode)) {
            float speed = module.rotationSpeed.getValue();
            float angleDeg = (System.nanoTime() / 1_000_000_000.0F) * speed;
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI).rotateY(angleDeg * (float) (Math.PI / 180.0));
        } else {
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI);
            headRotation = new Quaternionf();
        }

        InventoryScreen.drawEntity(context, centerX, centerY, size * scale,
                translation, bodyRotation, headRotation, player);
    }

    // ====================================================================
    // 9) InventoryHud: render TAIL — 9x3 panel with optional drag
    // ====================================================================

    /**
     * Render the InventoryHud panel. Refactored out of the
     * legacy {@code @Inject(method="render", at=TAIL)} entry point
     * so it can flow through {@code renderBufferedHud} like every
     * other HUD module - HudOptimizer routes it into Pass 1 (cached
     * FBO at the user's refresh rate) instead of redrawing the 27
     * slot icons every render frame, which is what made it spike
     * frame time on the user's machine.
     *
     * <p>Self-contained: the method pulls mouse / world / screen
     * state from {@link MinecraftClient} so the
     * {@code renderBufferedHud} call site doesn't need to thread
     * any extra context.
     */
    private void phaze$drawInventoryHudGuts(DrawContext context) {
        InventoryHud module = InventoryHud.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        if (mc.options.hudHidden) return;

        // Snapshot the live inventory at most once per game tick.
        // The 27 storage slots are a server-driven state - they
        // only change when packets land - so re-querying them per
        // frame plus re-evaluating cooldown / damage flags is
        // wasted work at modern HUD rates. Refresh is a no-op when
        // the tick counter hasn't advanced, so calling it every
        // frame is essentially free on the hot path.
        InventoryHud.refreshSnapshotIfStale(mc);
        ItemStack[] snapshot = InventoryHud.getSnapshotStacks();
        boolean[] overlayFlags = InventoryHud.getSnapshotOverlayFlags();

        float scale = module.getHudScale();
        int innerW = PHAZE_INV_SLOT * 9;
        int innerH = PHAZE_INV_SLOT * 3;
        int panelW = innerW + PHAZE_INV_PADDING * 2;
        int panelH = innerH + PHAZE_INV_PADDING * 2;

        boolean chatEditing = mc.currentScreen instanceof ChatScreen;
        boolean mouseDown = chatEditing && GLFW.glfwGetMouseButton(
                mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double scaleFactor = mc.getWindow().getScaleFactor();
        if (scaleFactor <= 0.0) scaleFactor = 1.0;
        float mouseX = (float) (mc.mouse.getX() / scaleFactor);
        float mouseY = (float) (mc.mouse.getY() / scaleFactor);

        float scaledW = panelW * scale;
        float scaledH = panelH * scale;
        int scaledScreenW = mc.getWindow().getScaledWidth();
        int scaledScreenH = mc.getWindow().getScaledHeight();
        float maxX = Math.max(0.0F, scaledScreenW - scaledW);
        float maxY = Math.max(0.0F, scaledScreenH - scaledH);

        float panelX = MathHelper.clamp(module.getHudX(), 0.0F, maxX);
        float panelY = MathHelper.clamp(module.getHudY(), 0.0F, maxY);
        module.setHudX(panelX);
        module.setHudY(panelY);

        if (chatEditing) {
            boolean inside = mouseX >= panelX && mouseX <= panelX + scaledW
                    && mouseY >= panelY && mouseY <= panelY + scaledH;
            if (mouseDown && !phaze$invWasMouseDown && inside) {
                phaze$invDragging = true;
                phaze$invDragOffsetX = mouseX - panelX;
                phaze$invDragOffsetY = mouseY - panelY;
            }
            if (!mouseDown) phaze$invDragging = false;
            if (phaze$invDragging) {
                panelX = MathHelper.clamp(mouseX - phaze$invDragOffsetX, 0.0F, maxX);
                panelY = MathHelper.clamp(mouseY - phaze$invDragOffsetY, 0.0F, maxY);
                module.setHudX(panelX);
                module.setHudY(panelY);
            }
        } else {
            phaze$invDragging = false;
        }
        phaze$invWasMouseDown = mouseDown;

        context.getMatrices().push();
        context.getMatrices().translate(panelX, panelY, 0);
        context.getMatrices().scale(scale, scale, 1.0F);

        // Backdrop + 27 slot-bg fills are cheap (one BufferBuilder
        // batch flushed at the end of the frame). drawItem on the
        // other hand binds a texture and submits a freshly-baked
        // model per slot, so we keep the slot loop tight: read
        // stacks + overlay flags from the per-tick snapshot, skip
        // empty slots, skip the stack-count overlay when the
        // pre-evaluated flag says it would draw nothing.
        context.fill(0, 0, panelW, panelH, 0x90000000);
        boolean drawCounts = module.drawCounts.isValue();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = PHAZE_INV_PADDING + col * PHAZE_INV_SLOT + 1;
                int sy = PHAZE_INV_PADDING + row * PHAZE_INV_SLOT + 1;
                context.fill(sx, sy, sx + PHAZE_INV_ICON, sy + PHAZE_INV_ICON, 0x55_8B8B8B);
                int idx = row * 9 + col;
                ItemStack stack = snapshot[idx];
                if (stack == null || stack.isEmpty()) continue;
                context.drawItem(stack, sx, sy);
                if (drawCounts && overlayFlags[idx]) {
                    context.drawStackOverlay(mc.textRenderer, stack, sx, sy);
                }
            }
        }
        // Single flush at the end of the HUD pass: drawItem queues
        // its sprites into the global immediate buffer, and without
        // an explicit flush the next HUD module reading from the
        // framebuffer (e.g. blur) would see uncomposited geometry.
        context.draw();
        context.getMatrices().pop();
    }

    /**
     * @deprecated superseded by the per-tick snapshot maintained in
     *             {@link InventoryHud#refreshSnapshotIfStale(MinecraftClient)}.
     *             Kept around because {@code phaze$drawInventoryHud}
     *             now reads the precomputed flag directly from the
     *             snapshot array, but other call sites in this mixin
     *             may still rely on the live predicate during early
     *             tear-down before the first snapshot refresh.
     */
    @Unique
    private static boolean phaze$shouldDrawSlotOverlay(ItemStack stack) {
        if (stack.getCount() != 1) return true;
        if (stack.isItemBarVisible()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null
                && mc.player.getItemCooldownManager().getCooldownProgress(stack, 0.0F) > 0.0F) {
            return true;
        }
        return false;
    }

    // ====================================================================
    // 12) Crosshair: redirect Perspective.isFirstPerson inside renderCrosshair
    // ====================================================================

    @Redirect(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z")
    )
    private boolean phaze$forceFirstPersonForCrosshair(Perspective perspective) {
        Crosshair module = Crosshair.getInstance();
        if (module != null && module.isEnabled() && module.showInThirdPerson.isValue()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && mc.options.hudHidden) {
                return perspective.isFirstPerson();
            }
            return true;
        }
        return perspective.isFirstPerson();
    }
}
