package vorga.phazeclient.implement.hud;

import static vorga.phazeclient.implement.hud.HudInfoText.*;
import static vorga.phazeclient.implement.hud.HudMath.*;
import static vorga.phazeclient.implement.hud.HudPaint.*;
import static vorga.phazeclient.implement.hud.HudStyle.*;
import static vorga.phazeclient.implement.hud.HudTextDraw.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.api.system.cursor.HudCursorRelay;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.BossBarHud;
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
import vorga.phazeclient.implement.features.modules.hud.ScoreboardSidebarEntry;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.SprintHud;
import vorga.phazeclient.implement.features.modules.hud.MemoryHud;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.hud.ServerAddressHud;
import vorga.phazeclient.implement.features.modules.hud.WailaHud;
import vorga.phazeclient.implement.features.modules.other.HealthIndicator;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.hud.PackDisplayHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.hud.TpsHud;
import vorga.phazeclient.implement.features.modules.other.Zoom;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.hud.HudCapture;
import vorga.phazeclient.api.system.hud.HudCachedTransform;
import vorga.phazeclient.api.system.hud.HudPassState;
import vorga.phazeclient.api.system.hud.HudRefreshPolicy;
import vorga.phazeclient.api.system.hud.Render2DState;
import vorga.phazeclient.api.system.hud.ChatAnimationFrameAccess;
import vorga.phazeclient.api.system.hud.BossBarHudAccess;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.ScaleSnapOverlay;
import vorga.phazeclient.implement.features.modules.other.HudOptimizer;
import vorga.phazeclient.implement.features.modules.other.NickHider;
import vorga.phazeclient.implement.features.modules.other.TrapTimer;
import vorga.phazeclient.implement.features.modules.hud.InventoryHud;
import vorga.phazeclient.implement.features.modules.hud.PaperDoll;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.implement.hud.vanilla.HotbarTweaks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

public final class CustomHudRenderer {
    private static final float GUIDE_SNAP_RADIUS = 3.0f;
    private static final float HUD_TO_HUD_SNAP_RADIUS = 4.0f;
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
    private static final int HUD_PAPER_DOLL = 24;
    private static final int HUD_TRAP_TIMER = 25;
    private static final int HUD_INVENTORY = 26;
    private static final int HUD_PACK_DISPLAY = 27;
    private static final int HUD_BOSS_BAR = 28;
    private static final int RECT_HUD_COUNT = 29;
    // Armor must never alias a rectangular HUD's blur state (27 is Pack Display).
    private static final int HUD_ARMOR_BLUR_SLOT = RECT_HUD_COUNT;
    private static final int HUD_SNAP_ARMOR = RECT_HUD_COUNT;
    private static final int HUD_SNAP_COUNT = RECT_HUD_COUNT + 1;
    private static final int KEYSTROKE_W = 0;
    private static final int KEYSTROKE_A = 1;
    private static final int KEYSTROKE_S = 2;
    private static final int KEYSTROKE_D = 3;
    private static final int KEYSTROKE_LMB = 4;
    private static final int KEYSTROKE_RMB = 5;
    private static final int KEYSTROKE_SPACE = 6;
    private static final List<ShapeProperties> KEYSTROKE_BLUR_RECTS = List.of(
            createKeystrokeBlurRect(20.0f, 0.0f, 16.0f, 18.0f),
            createKeystrokeBlurRect(0.0f, 19.0f, 18.0f, 18.0f),
            createKeystrokeBlurRect(19.0f, 19.0f, 16.0f, 18.0f),
            createKeystrokeBlurRect(36.0f, 19.0f, 18.0f, 18.0f),
            createKeystrokeBlurRect(0.0f, 38.0f, 54.0f, 8.0f),
            createKeystrokeBlurRect(0.0f, 47.0f, 26.0f, 16.0f),
            createKeystrokeBlurRect(28.0f, 47.0f, 26.0f, 16.0f)
    );

    private static final boolean[] RECT_DRAGGING = new boolean[RECT_HUD_COUNT];
    private static final boolean[] RECT_RESIZING = new boolean[RECT_HUD_COUNT];
    private static final float[] RECT_DRAG_OFFSET_X = new float[RECT_HUD_COUNT];
    private static final float[] RECT_DRAG_OFFSET_Y = new float[RECT_HUD_COUNT];
    private static final float[] RECT_RESIZE_START_WIDTH = new float[RECT_HUD_COUNT];
    private static final float[] RECT_RESIZE_START_MOUSE_X = new float[RECT_HUD_COUNT];
    private static final float[] RECT_RESIZE_START_MOUSE_Y = new float[RECT_HUD_COUNT];
    private static final float[] RECT_HOVER_PROGRESS = new float[RECT_HUD_COUNT];
    private static final float[] RECT_POSITION_RATIO_X = new float[RECT_HUD_COUNT];
    private static final float[] RECT_POSITION_RATIO_Y = new float[RECT_HUD_COUNT];
    private static final int[] RECT_LAST_SCREEN_WIDTH = new int[RECT_HUD_COUNT];
    private static final int[] RECT_LAST_SCREEN_HEIGHT = new int[RECT_HUD_COUNT];
    private static final boolean[] RECT_LAYOUT_INITIALIZED = new boolean[RECT_HUD_COUNT];
    private static final int[] RECT_BG_ANIMATED_COLOR = new int[RECT_HUD_COUNT];
    private static final boolean[] RECT_BG_COLOR_INITIALIZED = new boolean[RECT_HUD_COUNT];
    private static final float[] HUD_SNAP_X = new float[HUD_SNAP_COUNT];
    private static final float[] HUD_SNAP_Y = new float[HUD_SNAP_COUNT];
    private static final float[] HUD_SNAP_WIDTH = new float[HUD_SNAP_COUNT];
    private static final float[] HUD_SNAP_HEIGHT = new float[HUD_SNAP_COUNT];
    private static final Module[] HUD_SNAP_OWNER = new Module[HUD_SNAP_COUNT];
    private static final boolean[] HUD_SNAP_VALID = new boolean[HUD_SNAP_COUNT];
    private static final float[] HUD_LAYOUT_BASE_WIDTH = new float[HUD_SNAP_COUNT];
    private static final float[] HUD_LAYOUT_BASE_HEIGHT = new float[HUD_SNAP_COUNT];
    // Match the existing dispatcher priority, including the non-rectangular Armor HUD.
    private static final int[] HUD_INPUT_ORDER = {HUD_FPS, HUD_CPS, HUD_REACH, HUD_SNAP_ARMOR, HUD_INVENTORY,
            HUD_SPRINT, HUD_COORDINATES, HUD_PING, HUD_KEYSTROKES, HUD_POTION, HUD_DAY_COUNTER, HUD_TIME,
            HUD_SESSION, HUD_MEMORY, HUD_TPS, HUD_COMBO, HUD_SERVER_ADDRESS, HUD_TRAP_TIMER,
            HUD_MOVEMENT_SPEED, HUD_WAILA, HUD_HEALTH_INDICATOR, HUD_PACK_DISPLAY, HUD_CONSUMABLE,
            HUD_SCOREBOARD, HUD_BOSS_BAR, HUD_PAPER_DOLL};
    private static final HudBuffer PHAZE_EDITED_HUD_BUFFER = new HudBuffer(org.lwjgl.opengl.GL11C.GL_LINEAR);
    private static int isolatedHudIndex = -1;
    private static HudCachedTransform editedHudTransform;
    private static ChatScreen lastHudEditorScreen;
    private static double lastHudGuiScale = -1;
    private static int lastHudFramebufferWidth = -1;
    private static int lastHudFramebufferHeight = -1;
    private static final float[] KEYSTROKE_PROGRESS = new float[7];
    private static final String[] HUD_TEXT_CACHE = new String[RECT_HUD_COUNT];
    private static final long[] HUD_TEXT_CACHE_TIME_MS = new long[RECT_HUD_COUNT];
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
    private static long potionSampleCycle = Long.MIN_VALUE;
    private static int potionSampleIndex;
    private static final Random POTION_PREVIEW_RANDOM = new Random();
    private static final String[][] POTION_PREVIEW_SAMPLES = {
            {"Speed II", "01:48"},
            {"Strength", "02:31"},
            {"Regeneration II", "00:45"},
            {"Invisibility", "02:14"},
            {"Fire Resistance", "04:12"},
            {"Night Vision", "03:06"}
    };

    private static boolean armorDragging = false;
    private static boolean armorResizing = false;
    private static float armorDragOffsetX = 0.0f;
    private static float armorDragOffsetY = 0.0f;
    private static float armorResizeStartScale = 1.0f;
    private static float armorResizeStartMouseX = 0.0f;
    private static float armorResizeStartMouseY = 0.0f;
    private static float armorHoverProgress = 0.0f;
    private static float armorPositionRatioX = 0.0f;
    private static float armorPositionRatioY = 0.0f;
    private static int armorLastScreenWidth = -1;
    private static int armorLastScreenHeight = -1;
    private static boolean armorLayoutInitialized = false;
    private static int armorAnimatedBackgroundColor = 0;
    private static boolean armorBackgroundColorInitialized = false;
    private static int scoreboardAnimatedTitleColor = 0;
    private static int scoreboardAnimatedRowColor = 0;
    private static boolean scoreboardBackgroundColorsInitialized = false;

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
    private static float hudVerticalGuideProgress = 0.0f;
    private static float hudHorizontalGuideProgress = 0.0f;
    private static boolean showHudVerticalGuideThisFrame = false;
    private static boolean showHudHorizontalGuideThisFrame = false;
    private static float hudVerticalGuideX = 0.0f;
    private static float hudVerticalGuideTop = 0.0f;
    private static float hudVerticalGuideBottom = 0.0f;
    private static float hudHorizontalGuideY = 0.0f;
    private static float hudHorizontalGuideLeft = 0.0f;
    private static float hudHorizontalGuideRight = 0.0f;
    private static final HudPassState PHAZE_HUD_PASS = new HudPassState();
    /** Actual blur state, separate from animated-gradient high-refresh state. */
    private static boolean hasActiveHudBlurThisFrame = false;
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
    private static final Map<Object, Boolean> PHAZE_LAST_GRADIENT_STATE = new IdentityHashMap<>();
    private static float directionDisplayYaw = Float.NaN;

    public static void resetGuiRenderState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private CustomHudRenderer() {}

    public static void beginFrame(MinecraftClient client, DrawContext context) {
        renderedThisFrame = false;
        PHAZE_HUD_PASS.reset();
        HudCursorRelay.reset();
        Blur.INSTANCE.beginCachedFrame();
        // Disabled/invisible widgets must release their guard even on frames without a content capture.
        boolean editing = client != null && client.currentScreen instanceof ChatScreen
                && client.options != null && !client.options.hudHidden;
        ChatScreen editor = editing ? (ChatScreen) client.currentScreen : null;
        if (editor != lastHudEditorScreen) {
            for (int index = 0; index < HUD_SNAP_COUNT; index++) clearHudInteraction(index);
            selectIsolatedHud(-1);
            BatchedHudBuffer.INSTANCE.invalidate();
            lastHudEditorScreen = editor;
            ScaleSnapOverlay.hide();
            if (client != null && client.getWindow() != null) {
                wasMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                pmWasMouseDown = wasMouseDown;
            }
        }
        for (int index = 0; index < HUD_SNAP_COUNT; index++) {
            Module owner = HUD_SNAP_OWNER[index];
            if (!editing || !HUD_SNAP_VALID[index] || owner == null || !owner.isEnabled()) {
                clearHudInteraction(index);
                if (index == HUD_SNAP_ARMOR) armorHoverProgress = 0;
                else RECT_HOVER_PROGRESS[index] = 0;
            }
            if (owner != null && !owner.isEnabled() && HUD_SNAP_VALID[index]) {
                HUD_SNAP_VALID[index] = false;
                BatchedHudBuffer.INSTANCE.invalidate();
            }
        }
        if (isolatedHudIndex >= 0 && !HUD_SNAP_VALID[isolatedHudIndex]) selectIsolatedHud(-1);
        if (!editing || !HudOptimizer.getInstance().isEnabled()) selectIsolatedHud(-1);
        if (client != null && client.getWindow() != null) {
            double guiScale = client.getWindow().getScaleFactor();
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            if (guiScale != lastHudGuiScale || width != lastHudFramebufferWidth || height != lastHudFramebufferHeight) {
                // Resize offsets belong to the old coordinate system. Cancel instead of jumping while held.
                for (int index = 0; index < HUD_SNAP_COUNT; index++) clearHudInteraction(index);
                selectIsolatedHud(-1);
                Arrays.fill(HUD_SNAP_VALID, false);
                BatchedHudBuffer.INSTANCE.invalidate();
                PHAZE_EDITED_HUD_BUFFER.invalidate();
                editedHudTransform = null;
                lastHudGuiScale = guiScale;
                lastHudFramebufferWidth = width;
                lastHudFramebufferHeight = height;
                ScaleSnapOverlay.hide();
                wasMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                pmWasMouseDown = wasMouseDown;
            }
            if (!editing) {
                wasMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                pmWasMouseDown = wasMouseDown;
            }
        }

        // Animation clocks must advance at the display FPS, even when
        // Exordium serves renderHotbar/ChatHud from a cached framebuffer and
        // therefore skips those vanilla methods for this frame.
        ExordiumAnimationBridge.beginHudFrame(context);
        if (client != null && client.inGameHud != null) {
            if (BossBarHud.getInstance().isEnabled()) {
                ((BossBarHudAccess) client.inGameHud.getBossBarHud()).phaze$updateLayout();
            }
            BossBarHud.getInstance().beginFrame(System.nanoTime());
            if (!BossBarHud.getInstance().isEnabled() || !(client.currentScreen instanceof ChatScreen)) {
                RECT_DRAGGING[HUD_BOSS_BAR] = false;
                RECT_RESIZING[HUD_BOSS_BAR] = false;
                RECT_HOVER_PROGRESS[HUD_BOSS_BAR] = 0.0F;
            }
        }
        HotbarTweaks.advanceSlide();
        if (client != null && client.inGameHud != null
                && client.inGameHud.getChatHud() instanceof ChatAnimationFrameAccess access) {
            access.phaze$tickAnimationFrame();
        }
    }

    public static void renderFrame(DrawContext context) {
        // Drain vanilla geometry before establishing our boundary (Exordium may own the target).
        try {
            context.draw();
        } catch (RuntimeException | Error failure) {
            PHAZE_HUD_PASS.reset();
            try { Blur.INSTANCE.endCachedFrame(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        try (Render2DState state = Render2DState.capture()) {
            try {
                renderCustomHud(context);
            } finally {
                try {
                    Blur.INSTANCE.endCachedFrame();
                } finally {
                    PHAZE_HUD_PASS.reset();
                    context.draw();
                }
            }
        }
    }

    private static void renderCustomHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hudHidden = client == null || client.options == null || client.options.hudHidden;

        // Operator announcements sit above everything else the HUD
        // draws and are deliberately not suppressed by F1: they are
        // the operator talking, not decoration.
        vorga.phazeclient.implement.menu.AnnouncementOverlay.render(context);
        vorga.phazeclient.implement.features.modules.hud.KillStreakHud.getInstance().render(context);

        if (!renderedThisFrame) {
            // Capture the clean world/vanilla-HUD framebuffer at the one safe
            // point shared by normal gameplay and open GUIs. Doing this before
            // any Phaze FBO capture prevents live HUD blur from sampling the
            // cleared/intermediate framebuffer used during Screen rendering.
            boolean hasLiveHudAnimation = !hudHidden && prescanBlurStateFlips();
            if (!hudHidden && hasActiveHudBlurThisFrame) {
                context.draw();
                Blur.INSTANCE.captureBaseFrameForBlur();
            }

            if (hudHidden || !hasAnyHudEnabled()) {
                BatchedHudBuffer.INSTANCE.invalidate();
            } else if (!HudOptimizer.getInstance().isEnabled()) {
                BatchedHudBuffer.INSTANCE.invalidate();
                renderHudInternal(context);
            } else if (shouldBypassHudBatchingForCurrentScreen(client)) {
                // Vanilla pause/inventory/options screens with background blur
                // run additional framebuffer passes after InGameHud. Rendering
                // our HUDs directly on those screens avoids the cached-HUD FBO
                // colliding with vanilla's blur pipeline while keeping every
                // HUD feature visible and functional.
                BatchedHudBuffer.INSTANCE.invalidate();
                renderHudInternal(context);
            } else {
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
                int configuredRefreshRate = HudOptimizer.getInstance().refreshRate.getInt();
                // Animated HUDs keep native-resolution geometry/text together in the
                // existing FBO. Rebuilding them once per 120 Hz animation sample avoids
                // repeating all text, item and driver work at uncapped game FPS.
                // Blur can request a higher cadence; never cap it at the data refresh.
                int effectiveRefreshRate = HudRefreshPolicy.batch(configuredRefreshRate, hasLiveHudAnimation,
                        hasActiveHudBlurThisFrame, HudOptimizer.getInstance().blurBackgroundFps.getInt());
                BatchedHudBuffer.INSTANCE.setTargetFps(effectiveRefreshRate);

                // Input runs BEFORE capture and never submits content/blur or invalidates on motion.
                PHAZE_HUD_PASS.run(HudPassState.Mode.LOGIC, () -> renderHudInternal(context));
                boolean shouldRefresh = BatchedHudBuffer.INSTANCE.shouldRefresh(false);

                // Refresh frames only: draw all HUDs in their original order,
                // including blur/gradient, into the native-resolution batched FBO.
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
                    boolean keepIsolatedBounds = isolatedHudIndex >= 0 && HUD_SNAP_VALID[isolatedHudIndex];
                    Arrays.fill(HUD_SNAP_VALID, false);
                    if (keepIsolatedBounds) HUD_SNAP_VALID[isolatedHudIndex] = true;
                    PHAZE_HUD_PASS.run(HudPassState.Mode.CAPTURE, () -> {
                        try (HudCapture capture = BatchedHudBuffer.INSTANCE.openCapture()) {
                            if (capture == null) return;
                            try {
                                renderHudInternal(context);
                            } finally {
                                // Also drain partial deferred geometry before abort restores the caller's FBO.
                                context.draw();
                            }
                            capture.commit();
                        }
                    });
                }
                if (isolatedHudIndex >= 0 && !HUD_SNAP_VALID[isolatedHudIndex]) {
                    clearHudInteraction(isolatedHudIndex);
                    selectIsolatedHud(-1);
                }
                BatchedHudBuffer.INSTANCE.blit();
                drawIsolatedHud(client);
                // Handles, guides and key feedback stay live, but are never frozen inside either FBO.
                float inverseGuiScale = 1.0F / Math.max(1.0F, (float) client.getWindow().getScaleFactor());
                renderLiveKeystrokeAnimation(context, client, inverseGuiScale);
                if (client.currentScreen instanceof ChatScreen) {
                    renderLiveHudEditor(context, client);
                    renderHudGuides(context, client.getWindow().getWidth(), client.getWindow().getHeight(), inverseGuiScale);
                }
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

        // Draw the resize readout on the real final framebuffer. Rendering it
        // inside renderHudInternal lets HudOptimizer capture it into its FBO,
        // where it can disappear or update only at the cache refresh rate.
        if (client != null && client.currentScreen instanceof ChatScreen) {
            ScaleSnapOverlay.render(context);
        }

        // HUD blur uses temporary shader/framebuffer state. The vanilla
        // screen is rendered after InGameHud, so leaving the blur shader or
        // blend state active here makes pause/options buttons and text look
        // blurred after leaving the HUD editor or opening a GUI.
        context.draw();
        // Outer Render2DState restores the exact pre-Phaze state, not a guessed GUI baseline.
    }

    private static void renderHudInternal(DrawContext context) {
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
        boolean lastCallThisFrame = !PHAZE_HUD_PASS.isBatchPass();
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
            updateKeystrokeAnimations(client, mouseDown, rightMouseDown, deltaSeconds);
            showVerticalGuideThisFrame = false;
            showHorizontalGuideThisFrame = false;
            showHudVerticalGuideThisFrame = false;
            showHudHorizontalGuideThisFrame = false;
        }
        if (PHAZE_HUD_PASS.isLogicOnlyPass()) {
            updateCachedHudInteractions(client, chatEditing, mouseX, mouseY, mouseDown, deltaSeconds);
            if (chatEditing) {
                verticalGuideProgress = approachExp(verticalGuideProgress, showVerticalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                horizontalGuideProgress = approachExp(horizontalGuideProgress, showHorizontalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                hudVerticalGuideProgress = approachExp(hudVerticalGuideProgress, showHudVerticalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                hudHorizontalGuideProgress = approachExp(hudHorizontalGuideProgress, showHudHorizontalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
            } else {
                verticalGuideProgress = 0.0f;
                horizontalGuideProgress = 0.0f;
                hudVerticalGuideProgress = 0.0f;
                hudHorizontalGuideProgress = 0.0f;
            }
            wasMouseDown = mouseDown;
            pmWasMouseDown = mouseDown;
            return;
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
                renderInventoryHud(context, client, InventoryHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(InventoryHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale,
                        screenWidth, screenHeight, screenCenterX, screenCenterY));
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

        final TrapTimer trapTimer = TrapTimer.getInstance();
        renderBufferedHud(context, trapTimer, chatEditing, () -> {
            Text trapText = trapTimer.getDisplayText(chatEditing);
            if (trapText == null || trapText.getString().isEmpty()) {
                return;
            }
            renderTrapTimerHud(context, client, trapTimer, trapText, HUD_TRAP_TIMER,
                    chatEditing, mouseX, mouseY, mouseDown, getHudDelta(trapTimer, chatEditing, deltaSeconds),
                    inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY);
        });

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

        // Pack Display uses the same cached/blurred/draggable rectangle path
        // as every other HUD. Its text is rebuilt only when the enabled
        // pack list changes, not every frame.
        final PackDisplayHud packDisplay = PackDisplayHud.getInstance();
        renderBufferedHud(context, packDisplay, chatEditing, () -> {
            net.minecraft.util.Identifier packIcon = packDisplay.getPackIcon(client);
            String packText = packDisplay.getDisplayText(client);
            if (packText.isEmpty()) return;
            renderRectHud(context, client, packDisplay, packText, HUD_PACK_DISPLAY,
                    chatEditing, mouseX, mouseY, mouseDown, getHudDelta(packDisplay, chatEditing, deltaSeconds),
                    inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                    Math.max(BASE_WIDTH, packDisplay.getPanelBaseWidth(client)),
                    28.0f);
            if (packIcon != null) {
                context.getMatrices().push();
                context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
                context.getMatrices().translate(packDisplay.getHudX(), packDisplay.getHudY(), HUD_TEXT_RENDER_Z + 1.0f);
                context.getMatrices().scale(packDisplay.getRenderHudScale(), packDisplay.getRenderHudScale(), 1.0f);
                packDisplay.drawPackIcon(context);
                context.getMatrices().pop();
            }
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

        if (chatEditing) {
            BossBarHud bossBar = BossBarHud.getInstance();
            renderBufferedHud(context, bossBar, true, () ->
                    renderRectHud(context, client, bossBar, "", HUD_BOSS_BAR, true, mouseX, mouseY, mouseDown,
                            deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                            bossBar.getBaseWidth(), bossBar.getBaseHeight()));
        }

        // The model uses GUI coordinates, but its expensive entity renderer belongs in the same throttled cache.
        renderBufferedHud(context, PaperDoll.getInstance(), chatEditing, () -> renderPaperDollHud(context));

        if (lastCallThisFrame) {
            if (chatEditing) {
                verticalGuideProgress = approachExp(verticalGuideProgress, showVerticalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                horizontalGuideProgress = approachExp(horizontalGuideProgress, showHorizontalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                hudVerticalGuideProgress = approachExp(hudVerticalGuideProgress, showHudVerticalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                hudHorizontalGuideProgress = approachExp(hudHorizontalGuideProgress, showHudHorizontalGuideThisFrame ? 1.0f : 0.0f, GUIDE_FADE_SPEED, deltaSeconds);
                renderHudGuides(context, screenWidth, screenHeight, inverseGuiScale);
            } else {
                verticalGuideProgress = 0.0f;
                horizontalGuideProgress = 0.0f;
                hudVerticalGuideProgress = 0.0f;
                hudHorizontalGuideProgress = 0.0f;
            }
            wasMouseDown = mouseDown;
        }
    }

    private static void renderBufferedHud(DrawContext context, RectHudModule module, boolean chatEditing, Runnable renderLogic) {
        boolean liveBackground = module.hasActiveBackgroundBlur() || module.hasActiveAnimatedBackground();
        trackBlurStateChange(module, module.hasActiveBackgroundBlur());
        trackGradientStateChange(module, module.hasActiveAnimatedBackground());
        if (PHAZE_HUD_PASS.isGradientPass()) {
            if (module.hasActiveAnimatedBackground()) {
                renderBufferedHudInternal(context, module, renderLogic);
            }
            return;
        }
        if (shouldSkipForCurrentPass(liveBackground)) return;
        renderBufferedHudInternal(context, module, renderLogic);
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
    private static void trackBlurStateChange(Object module, boolean current) {
        Boolean prev = PHAZE_LAST_BLUR_STATE.put(module, current);
        if (prev != null && prev != current) {
            invalidateHudContent(module);
        }
    }

    private static void trackGradientStateChange(Object module, boolean current) {
        Boolean previous = PHAZE_LAST_GRADIENT_STATE.put(module, current);
        if (previous != null && previous != current) {
            invalidateHudContent(module);
        }
    }

    private static void invalidateHudContent(Object module) {
        BatchedHudBuffer.INSTANCE.invalidate();
        if (isolatedHudIndex >= 0 && HUD_SNAP_OWNER[isolatedHudIndex] == module) {
            PHAZE_EDITED_HUD_BUFFER.invalidate();
            editedHudTransform = null;
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
     * <p>The per-{@code renderBufferedHud} {@link #trackBlurStateChange}
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
    private static boolean prescanBlurStateFlips() {
        Main main = Main.getInstance();
        if (main == null) {
            hasActiveHudBlurThisFrame = false;
            return false;
        }
        var provider = main.getModuleProvider();
        if (provider == null) {
            hasActiveHudBlurThisFrame = false;
            return false;
        }
        boolean anyLiveBackground = false;
        boolean anyActualBlur = false;
        for (Module module : provider.getModules()) {
            boolean current;
            boolean animatedGradient;
            if (module instanceof RectHudModule rectModule) {
                animatedGradient = rectModule.hasActiveAnimatedBackground();
                current = rectModule.hasActiveBackgroundBlur();
            } else if (module instanceof ArmorHud armorModule) {
                animatedGradient = armorModule.hasActiveAnimatedBackground();
                current = armorModule.hasActiveBackgroundBlur();
            } else {
                continue;
            }
            anyActualBlur |= module.isEnabled() && current;
            anyLiveBackground |= module.isEnabled() && (current || animatedGradient);
            Boolean prev = PHAZE_LAST_BLUR_STATE.put(module, current);
            if (prev != null && prev != current) {
                invalidateHudContent(module);
            }
            Boolean previousGradient = PHAZE_LAST_GRADIENT_STATE.put(module, animatedGradient);
            if (previousGradient != null && previousGradient != animatedGradient) {
                invalidateHudContent(module);
            }
        }
        hasActiveHudBlurThisFrame = anyActualBlur;
        return anyLiveBackground;
    }

    private static void renderBufferedHud(DrawContext context, ArmorHud module, boolean chatEditing, Runnable renderLogic) {
        boolean liveBackground = module.hasActiveBackgroundBlur() || module.hasActiveAnimatedBackground();
        trackBlurStateChange(module, module.hasActiveBackgroundBlur());
        trackGradientStateChange(module, module.hasActiveAnimatedBackground());
        if (PHAZE_HUD_PASS.isGradientPass()) {
            if (module.hasActiveAnimatedBackground()) {
                renderBufferedHudInternal(context, module, renderLogic);
            }
            return;
        }
        if (shouldSkipForCurrentPass(liveBackground)) return;
        renderBufferedHudInternal(context, module, renderLogic);
    }

    /**
     * Two-pass filter: blur HUDs are skipped during the batched FBO pass, and
     * non-blur HUDs are skipped during the direct blur-only pass. In the
     * normal (un-batched) path both passes are inactive and all HUDs render.
     */
    private static boolean shouldSkipForCurrentPass(boolean hasBlur) {
        if (PHAZE_HUD_PASS.isLogicOnlyPass()) return true;
        if (PHAZE_HUD_PASS.isBatchPass() && hasBlur && !PHAZE_HUD_PASS.includesBlur()) return true;
        return false;
    }

    private static float getHudDelta(RectHudModule module, boolean chatEditing, float deltaSeconds) {
        return deltaSeconds;
    }

    private static float getHudDelta(ArmorHud module, boolean chatEditing, float deltaSeconds) {
        return deltaSeconds;
    }

    private static void renderBufferedHudInternal(DrawContext context, Module module, Runnable renderLogic) {
        if (!module.isEnabled()) return;
        if (!PHAZE_HUD_PASS.isBatchPass() || isolatedHudIndex < 0
                || HUD_SNAP_OWNER[isolatedHudIndex] != module) {
            renderLogic.run();
            return;
        }
        boolean gradient = module instanceof RectHudModule rect ? rect.hasActiveAnimatedBackground()
                : ((ArmorHud) module).hasActiveAnimatedBackground();
        boolean blur = module instanceof RectHudModule rect ? rect.hasActiveBackgroundBlur()
                : ((ArmorHud) module).hasActiveBackgroundBlur();
        int fps = HudRefreshPolicy.edited(HudOptimizer.getInstance().refreshRate.getInt(), gradient, blur,
                HudOptimizer.getInstance().blurBackgroundFps.getInt());
        if (!PHAZE_EDITED_HUD_BUFFER.shouldUpdate(fps)) return;

        // Only one spare FBO, reused for every widget. Drain neighbours before switching targets.
        context.draw();
        HUD_SNAP_VALID[isolatedHudIndex] = false;
        try (HudCapture capture = PHAZE_EDITED_HUD_BUFFER.openCapture()) {
            if (capture == null) return;
            try {
                renderLogic.run();
            } finally {
                context.draw();
            }
            capture.commit();
        }
        editedHudTransform = HUD_SNAP_VALID[isolatedHudIndex]
                ? captureEditedHudTransform(MinecraftClient.getInstance()) : null;
    }

    private static void clearHudInteraction(int index) {
        if (index == HUD_SNAP_ARMOR) {
            armorDragging = false;
            armorResizing = false;
        } else if (index >= 0 && index < RECT_HUD_COUNT) {
            RECT_DRAGGING[index] = false;
            RECT_RESIZING[index] = false;
        }
    }

    private static void selectIsolatedHud(int index) {
        if (index == isolatedHudIndex) return;
        isolatedHudIndex = index;
        editedHudTransform = null;
        PHAZE_EDITED_HUD_BUFFER.invalidate();
        // Only membership changes dirty the shared cache. Translation/scale never do.
        BatchedHudBuffer.INSTANCE.invalidate();
    }

    private static float hudPixelScale(Module owner, MinecraftClient client) {
        float scale = owner instanceof RectHudModule rect ? rect.getRenderHudScale() : ((ArmorHud) owner).getRenderHudScale();
        return owner instanceof PaperDoll ? scale * (float) client.getWindow().getScaleFactor() : scale;
    }

    private static void updateCachedHudInteractions(MinecraftClient client, boolean editing,
                                                         double mouseX, double mouseY, boolean mouseDown, float deltaSeconds) {
        int active = -1;
        // Refresh hit boxes from cached base dimensions, not from text/item/blur renderers.
        for (int index : HUD_INPUT_ORDER) {
            Module owner = HUD_SNAP_OWNER[index];
            if (!HUD_SNAP_VALID[index] || owner == null || !owner.isEnabled()) {
                clearHudInteraction(index);
                continue;
            }
            boolean armor = index == HUD_SNAP_ARMOR;
            float coordinateScale = index == HUD_PAPER_DOLL ? (float) client.getWindow().getScaleFactor() : 1;
            float pixelScale = hudPixelScale(owner, client);
            float width = HUD_LAYOUT_BASE_WIDTH[index] * pixelScale;
            float height = HUD_LAYOUT_BASE_HEIGHT[index] * pixelScale;
            if (width <= 0 || height <= 0) {
                clearHudInteraction(index);
                continue;
            }
            HUD_SNAP_X[index] = (armor ? ((ArmorHud) owner).getHudX() : ((RectHudModule) owner).getHudX()) * coordinateScale;
            HUD_SNAP_Y[index] = (armor ? ((ArmorHud) owner).getHudY() : ((RectHudModule) owner).getHudY()) * coordinateScale;
            HUD_SNAP_WIDTH[index] = width;
            HUD_SNAP_HEIGHT[index] = height;
            if (!editing || !mouseDown) clearHudInteraction(index);
            if (armor ? armorDragging || armorResizing : RECT_DRAGGING[index] || RECT_RESIZING[index]) active = index;
        }

        if (editing) {
            for (int index : HUD_INPUT_ORDER) {
                Module owner = HUD_SNAP_OWNER[index];
                if (!HUD_SNAP_VALID[index] || owner == null || !owner.isEnabled()
                        || HUD_LAYOUT_BASE_WIDTH[index] <= 0 || HUD_LAYOUT_BASE_HEIGHT[index] <= 0) continue;
                boolean armor = index == HUD_SNAP_ARMOR;
                boolean model = index == HUD_PAPER_DOLL;
                float coordinateScale = model ? (float) client.getWindow().getScaleFactor() : 1;
                float mx = (float) mouseX / coordinateScale, my = (float) mouseY / coordinateScale;
                float scale = hudPixelScale(owner, client) / coordinateScale;
                float x = HUD_SNAP_X[index] / coordinateScale, y = HUD_SNAP_Y[index] / coordinateScale;
                float width = HUD_SNAP_WIDTH[index] / coordinateScale, height = HUD_SNAP_HEIGHT[index] / coordinateScale;
                float hoverX = model ? x + 7 : x;
                float hoverWidth = model ? Math.max(12, width - 14) : width;
                int handleSize = model ? Math.max(4, Math.min(8, Math.round(4 * scale)))
                        : Math.max(4, Math.min(10, Math.round(5 * scale)));
                float handleInset = index == HUD_CONSUMABLE ? handleSize : handleSize / 2.0F;
                boolean hovered = isHovered(mx, my, hoverX, y, hoverWidth, height);
                boolean handle = isHovered(mx, my, hoverX + hoverWidth - handleInset, y + height - handleInset, handleSize, handleSize);
                if (armor) armorHoverProgress = approachExp(armorHoverProgress, hovered ? 1 : 0, 10, deltaSeconds);
                else RECT_HOVER_PROGRESS[index] = approachExp(RECT_HOVER_PROGRESS[index], hovered ? 1 : 0, 10, deltaSeconds);

                if (mouseDown && !wasMouseDown && active < 0 && (handle || hovered)) {
                    active = index;
                    if (armor) {
                        armorResizing = handle;
                        armorDragging = !handle;
                        armorResizeStartScale = scale;
                        armorResizeStartMouseX = mx;
                        armorResizeStartMouseY = my;
                        armorDragOffsetX = mx - x;
                        armorDragOffsetY = my - y;
                    } else {
                        RECT_RESIZING[index] = handle;
                        RECT_DRAGGING[index] = !handle;
                        RECT_RESIZE_START_WIDTH[index] = model || index == HUD_CONSUMABLE ? scale : width;
                        RECT_RESIZE_START_MOUSE_X[index] = mx;
                        RECT_RESIZE_START_MOUSE_Y[index] = my;
                        RECT_DRAG_OFFSET_X[index] = mx - x;
                        RECT_DRAG_OFFSET_Y[index] = my - y;
                    }
                }
                if (active != index) continue;

                float screenWidth = model ? client.getWindow().getScaledWidth() : client.getWindow().getWidth();
                float screenHeight = model ? client.getWindow().getScaledHeight() : client.getWindow().getHeight();
                float maxX = Math.max(0, screenWidth - width), maxY = Math.max(0, screenHeight - height);
                boolean dragging = armor ? armorDragging : RECT_DRAGGING[index];
                if (dragging) {
                    x = MathHelper.clamp(mx - (armor ? armorDragOffsetX : RECT_DRAG_OFFSET_X[index]), 0, maxX);
                    y = MathHelper.clamp(my - (armor ? armorDragOffsetY : RECT_DRAG_OFFSET_Y[index]), 0, maxY);
                    if (Math.abs(x + width * 0.5F - screenWidth * 0.5F) <= GUIDE_SNAP_RADIUS) {
                        x = MathHelper.clamp((screenWidth - width) * 0.5F, 0, maxX);
                        showVerticalGuideThisFrame = true;
                    } else x = snapHudAlignmentX(index, x, y, width, height, maxX, coordinateScale);
                    if (Math.abs(y + height * 0.5F - screenHeight * 0.5F) <= GUIDE_SNAP_RADIUS) {
                        y = MathHelper.clamp((screenHeight - height) * 0.5F, 0, maxY);
                        showHorizontalGuideThisFrame = true;
                    } else y = snapHudAlignmentY(index, x, y, width, height, maxY, coordinateScale);
                    HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
                } else {
                    float dx = mx - (armor ? armorResizeStartMouseX : RECT_RESIZE_START_MOUSE_X[index]);
                    float dy = my - (armor ? armorResizeStartMouseY : RECT_RESIZE_START_MOUSE_Y[index]);
                    float delta = (dx + dy) * 0.5F * 0.9F;
                    float min = armor ? ((ArmorHud) owner).getMinHudScale() : ((RectHudModule) owner).getMinHudScale();
                    float max = armor ? ((ArmorHud) owner).getMaxHudScale() : ((RectHudModule) owner).getMaxHudScale();
                    float raw;
                    if (armor || model || index == HUD_CONSUMABLE) {
                        float start = armor ? armorResizeStartScale : RECT_RESIZE_START_WIDTH[index];
                        // Preserve each legacy resize sensitivity: Armor/Consumable use the fixed reference width.
                        float reference = model ? PaperDoll.BASE_WIDTH : BASE_WIDTH;
                        raw = (start + delta / reference) / 2;
                    } else {
                        float baseline = scale / ((RectHudModule) owner).getHudScale();
                        raw = (RECT_RESIZE_START_WIDTH[index] + delta) / HUD_LAYOUT_BASE_WIDTH[index] / baseline;
                    }
                    float value = snapAndAnnounceHudScale(owner, raw, min, max);
                    if (armor) ((ArmorHud) owner).setHudScale(value);
                    else ((RectHudModule) owner).setHudScale(value);
                    scale = hudPixelScale(owner, client) / coordinateScale;
                    width = HUD_LAYOUT_BASE_WIDTH[index] * scale;
                    height = HUD_LAYOUT_BASE_HEIGHT[index] * scale;
                    x = MathHelper.clamp(x, 0, Math.max(0, screenWidth - width));
                    y = MathHelper.clamp(y, 0, Math.max(0, screenHeight - height));
                    HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
                }
                if (armor) {
                    ArmorHud module = (ArmorHud) owner;
                    module.setHudX(x);
                    module.setHudY(y);
                    module.setHudLayoutSize(width, height);
                } else {
                    RectHudModule module = (RectHudModule) owner;
                    module.setHudX(x);
                    module.setHudY(y);
                    module.setHudLayoutSize(width, height);
                }
                HUD_SNAP_X[index] = x * coordinateScale;
                HUD_SNAP_Y[index] = y * coordinateScale;
                HUD_SNAP_WIDTH[index] = width * coordinateScale;
                HUD_SNAP_HEIGHT[index] = height * coordinateScale;
            }
        }
        selectIsolatedHud(active);
    }

    private static HudCachedTransform captureEditedHudTransform(MinecraftClient client) {
        int index = isolatedHudIndex;
        float scale = hudPixelScale(HUD_SNAP_OWNER[index], client);
        float padding = Math.max(4, scale * (index == HUD_PAPER_DOLL ? 16 : 4));
        return HudCachedTransform.capture(HUD_SNAP_X[index], HUD_SNAP_Y[index], HUD_SNAP_WIDTH[index], HUD_SNAP_HEIGHT[index],
                scale, padding, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
    }

    private static void drawIsolatedHud(MinecraftClient client) {
        int index = isolatedHudIndex;
        if (index < 0 || !HUD_SNAP_VALID[index] || editedHudTransform == null) return;
        PHAZE_EDITED_HUD_BUFFER.drawCached(editedHudTransform, HUD_SNAP_X[index], HUD_SNAP_Y[index],
                hudPixelScale(HUD_SNAP_OWNER[index], client));
    }

    private static void renderLiveHudEditor(DrawContext context, MinecraftClient client) {
        float guiScale = (float) client.getWindow().getScaleFactor();
        context.getMatrices().push();
        try {
            context.getMatrices().scale(1 / guiScale, 1 / guiScale, 1);
            for (int index : HUD_INPUT_ORDER) {
                Module owner = HUD_SNAP_OWNER[index];
                if (!HUD_SNAP_VALID[index] || owner == null || !owner.isEnabled()) continue;
                boolean armor = index == HUD_SNAP_ARMOR, model = index == HUD_PAPER_DOLL;
                float coordinateScale = model ? guiScale : 1;
                float scale = hudPixelScale(owner, client) / coordinateScale;
                float x = HUD_SNAP_X[index], y = HUD_SNAP_Y[index];
                float width = HUD_SNAP_WIDTH[index], height = HUD_SNAP_HEIGHT[index];
                if (model) {
                    x += 7 * guiScale;
                    width = Math.max(12 * guiScale, width - 14 * guiScale);
                }
                float hover = armor ? armorHoverProgress : RECT_HOVER_PROGRESS[index];
                if (hover > 0.05F) {
                    int alpha = model ? 165 : armor ? 160 : 175;
                    int thickness = model ? Math.round(guiScale) : Math.max(1, Math.round((armor || index == HUD_CONSUMABLE ? 2 : 1) / Math.max(1, scale)));
                    drawOuterOutline(context, x, y, width, height, thickness, withAlpha(0xFFFFFF, (int) (alpha * hover)));
                }
                boolean resizing = armor ? armorResizing : RECT_RESIZING[index];
                int handleSize = model ? Math.max(4, Math.min(8, Math.round(4 * scale)))
                        : Math.max(4, Math.min(10, Math.round(5 * scale)));
                float size = handleSize * coordinateScale;
                float inset = index == HUD_CONSUMABLE ? size : size / 2;
                float handleX = x + width - inset, handleY = y + height - inset;
                boolean handle = isHovered(client.mouse.getX(), client.mouse.getY(), handleX, handleY, size, size);
                float near = (model ? Math.max(12, 10 * scale) : Math.max(14, 12 * scale)) * coordinateScale;
                if (!resizing && !handle && !isNearRect(client.mouse.getX(), client.mouse.getY(), x, y, width, height, near)) continue;
                context.getMatrices().push();
                try {
                    context.getMatrices().translate(0, 0, HANDLE_RENDER_Z);
                    int hx = Math.round(handleX), hy = Math.round(handleY), pixels = Math.round(size);
                    context.fill(hx, hy, hx + pixels, hy + pixels, resizing ? 0xFFFFFFFF : HANDLE_COLOR);
                    if (handle || resizing) drawOutlineNoOverlap(context, hx - 1, hy - 1, pixels + 2, pixels + 2, withAlpha(0xFFFFFF, 220));
                } finally {
                    context.getMatrices().pop();
                }
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    private static String getCachedHudText(RectHudModule module, int hudIndex, boolean chatEditing, Supplier<String> supplier) {
        long now = System.currentTimeMillis();
        boolean throttledDue = now - HUD_TEXT_CACHE_TIME_MS[hudIndex] >= HUD_TEXT_THROTTLE_MS;
        if (HUD_TEXT_CACHE[hudIndex] == null || throttledDue) {
            HUD_TEXT_CACHE[hudIndex] = supplier.get();
            HUD_TEXT_CACHE_TIME_MS[hudIndex] = now;
        }
        return HUD_TEXT_CACHE[hudIndex];
    }

    private static void renderRectHud(
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

    private static void renderRectHud(
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

        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();

        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;
        float maxX = Math.max(0.0f, screenWidth - hudWidth);
        float maxY = Math.max(0.0f, screenHeight - hudHeight);
        int currentScreenWidth = Math.round(screenWidth);
        int currentScreenHeight = Math.round(screenHeight);
        float x = module.getHudX();
        float y = module.getHudY();
        module.setHudLayoutSize(hudWidth, hudHeight);
        boolean resized = RECT_LAYOUT_INITIALIZED[hudIndex]
                && (RECT_LAST_SCREEN_WIDTH[hudIndex] != currentScreenWidth
                || RECT_LAST_SCREEN_HEIGHT[hudIndex] != currentScreenHeight);
        x = MathHelper.clamp(x, 0.0f, maxX);
        y = MathHelper.clamp(y, 0.0f, maxY);
        module.setHudX(x);
        module.setHudY(y);
        RECT_POSITION_RATIO_X[hudIndex] = maxX <= 0.0f ? 0.0f : x / maxX;
        RECT_POSITION_RATIO_Y[hudIndex] = maxY <= 0.0f ? 0.0f : y / maxY;
        RECT_LAST_SCREEN_WIDTH[hudIndex] = currentScreenWidth;
        RECT_LAST_SCREEN_HEIGHT[hudIndex] = currentScreenHeight;
        RECT_LAYOUT_INITIALIZED[hudIndex] = true;

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
        // Place resize handle diagonally on the bottom-right corner:
        // half inside the HUD and half outside, like a corner grip.
        float handleX = x + hudWidth - handleSize / 2.0f;
        float handleY = y + hudHeight - handleSize / 2.0f;

        boolean hoveredHud = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;
        boolean handleInput = !PHAZE_HUD_PASS.isBatchPass();

        if (!chatEditing && handleInput) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], 0.0f, 10.0f, deltaSeconds);
        } else if (chatEditing && handleInput) {
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
                    boolean snappedToScreenX = false;
                    boolean snappedToScreenY = false;

                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5f, 0.0f, maxX);
                        showVerticalGuideThisFrame = true;
                        snappedToScreenX = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5f, 0.0f, maxY);
                        showHorizontalGuideThisFrame = true;
                        snappedToScreenY = true;
                    }
                    if (!snappedToScreenX) {
                        newX = snapHudAlignmentX(hudIndex, newX, newY, hudWidth, hudHeight, maxX, 1.0f);
                    }
                    if (!snappedToScreenY) {
                        newY = snapHudAlignmentY(hudIndex, newX, newY, hudWidth, hudHeight, maxY, 1.0f);
                    }

                    module.setHudX(newX);
                    module.setHudY(newY);
                    x = newX;
                    y = newY;
                    RECT_POSITION_RATIO_X[hudIndex] = maxX <= 0.0f ? 0.0f : x / maxX;
                    RECT_POSITION_RATIO_Y[hudIndex] = maxY <= 0.0f ? 0.0f : y / maxY;
                    handleX = x + hudWidth - handleSize / 2.0f;
                    handleY = y + hudHeight - handleSize / 2.0f;
                } else if (RECT_RESIZING[hudIndex]) {
                    float deltaX = (float) mouseX - RECT_RESIZE_START_MOUSE_X[hudIndex];
                    float deltaY = (float) mouseY - RECT_RESIZE_START_MOUSE_Y[hudIndex];
                    float delta = (deltaX + deltaY) * 0.5f;

                    float scaleBaseline = scale / module.getHudScale();
                    float minWidth = baseWidth * module.getMinHudScale() * scaleBaseline;
                    float maxWidth = baseWidth * module.getMaxHudScale() * scaleBaseline;
                    float newWidth = MathHelper.clamp(RECT_RESIZE_START_WIDTH[hudIndex] + delta * 0.9f, minWidth, maxWidth);
                    float newScale = snapAndAnnounceHudScale(
                            module, newWidth / baseWidth / scaleBaseline,
                            module.getMinHudScale(), module.getMaxHudScale());

                    module.setHudScale(newScale);
                    scale = module.getRenderHudScale();
                    hudWidth = baseWidth * scale;
                    hudHeight = baseHeight * scale;
                    maxX = Math.max(0.0f, screenWidth - hudWidth);
                    maxY = Math.max(0.0f, screenHeight - hudHeight);
                    x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
                    y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
                    module.setHudX(x);
                    module.setHudY(y);
                    RECT_POSITION_RATIO_X[hudIndex] = maxX <= 0.0f ? 0.0f : x / maxX;
                    RECT_POSITION_RATIO_Y[hudIndex] = maxY <= 0.0f ? 0.0f : y / maxY;
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

        rememberHudSnapBounds(hudIndex, module, x, y, hudWidth, hudHeight);

        if (chatEditing && handleInput) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        if (hudIndex == HUD_BOSS_BAR && chatEditing && !BossBarHud.getInstance().hasLiveBars()) {
            ((BossBarHudAccess) client.inGameHud.getBossBarHud()).phaze$renderPreview(context);
        }

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
                float normalizedBlurRadius = blurRadius;
                float blurX = 0.0f;
                float blurY = 0.0f;
                float blurWidth = baseWidth;
                float blurHeight = baseHeight;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, blurX, blurY, blurWidth, blurHeight);
                Blur.INSTANCE.registerHudBlurState(hudIndex, blurStateKey);

                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(), blurX, blurY, blurWidth, blurHeight)
                            .round(getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            if (module.isGradientPreset()) {
                int hoverFill = chatEditing ? withAlpha(0xFFFFFF, (int) (30.0f * RECT_HOVER_PROGRESS[hudIndex])) : 0;
                if (hudIndex != HUD_KEYSTROKES) {
                    renderGradientRect(context, 0.0F, 0.0F, baseWidth, baseHeight, hoverFill,
                            module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                            module.getGradientDirection(), module.getGradientAnimationOffset(System.currentTimeMillis()),
                            module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
                    context.draw();
                }
            } else {
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
                    renderSolidHudBackground(context, 0.0F, 0.0F, baseWidth, baseHeight, bgColor,
                            module.cornerRounding.getValue());
                    context.draw();
                }
            }
        }

        context.getMatrices().pop();

        // Use custom color for MemoryHud if Color Based On Usage is enabled
        int textColor = resolveHudTextColor();
        if (module instanceof MemoryHud) {
            MemoryHud memoryHud = (MemoryHud) module;
            int memoryColor = memoryHud.getMemoryColor();
            if (memoryColor != HUD_TEXT_COLOR) {
                textColor = memoryColor;
            }
        } else if (module instanceof HealthIndicator) {
            // HealthIndicator picks its colour from the victim's current
            // HP (matching the original mod's red/gold/yellow/green/dark-
            // green table). The method internally returns the white
            // sentinel HUD_TEXT_COLOR when Color By HP is off OR when no
            // target is tracked, so this branch transparently degrades
            // to the inherited default in those cases.
            int healthColor = ((HealthIndicator) module).getCurrentHpColor();
            if (healthColor != HUD_TEXT_COLOR) {
                textColor = healthColor;
            }
        } else if (module instanceof TpsHud) {
            // TPS HUD: green / yellow / red tier color when the user
            // has Color By TPS on. Off = white sentinel.
            int tpsColor = ((TpsHud) module).getColor();
            if (tpsColor != HUD_TEXT_COLOR) {
                textColor = tpsColor;
            }
        }

        if (module instanceof PackDisplayHud packDisplay) {
            renderPackDisplayText(context, client, packDisplay, x, y, baseHeight, scale, textColor);
        } else if (hudIndex != HUD_BOSS_BAR) {
            float textX = (baseWidth - getHudTextWidth(client, text, HUD_TEXT_SIZE)) / 2.0f;
            renderScaledHudTextColored(context, client, text, x, y, textX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), textColor);
        }

        if (chatEditing && handleInput && RECT_HOVER_PROGRESS[hudIndex] > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (175.0f * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && handleInput && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
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

    private static void renderPackDisplayText(
            DrawContext context,
            MinecraftClient client,
            PackDisplayHud module,
            float x,
            float y,
            float baseHeight,
            float scale,
            int textColor
    ) {
        net.minecraft.text.Text title = module.getNameText(client);
        int textX = Math.round(module.getTextStartX());
        boolean hasDescription = module.hasDescription(client);
        float titleY = hasDescription ? 3.0f : (baseHeight - 8.0f) * 0.5f;

        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_TEXT_RENDER_Z);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(client.textRenderer, title, textX, Math.round(titleY), textColor, module.textShadow.isValue());

        if (hasDescription) {
            float lineY = 14.0f;
            for (Text line : module.getDescriptionLines(client)) {
                context.drawText(client.textRenderer, line, textX, Math.round(lineY), textColor, module.textShadow.isValue());
                lineY += 8.0f;
            }
        }
        context.getMatrices().pop();
    }

    private static void renderArmorHud(
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

        // Reuse cached list instances to eliminate per-frame GC allocations
        armorStacksCache.clear();
        armorDurabilityTextsCache.clear();

        var inventory = client.player.getInventory();
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack stack = inventory.getArmorStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            armorStacksCache.add(stack);
            armorDurabilityTextsCache.add(formatDurabilityText(module, stack));
        }

        ItemStack offHand = client.player.getOffHandStack();
        if (!offHand.isEmpty() && offHand.isOf(Items.SHIELD)) {
            armorStacksCache.add(offHand);
            armorDurabilityTextsCache.add(formatDurabilityText(module, offHand));
        }

        if (armorStacksCache.isEmpty() && chatEditing) {
            armorStacksCache.add(new ItemStack(Items.DIAMOND_HELMET));
            armorStacksCache.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            armorStacksCache.add(new ItemStack(Items.DIAMOND_LEGGINGS));
            armorStacksCache.add(new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack stack : armorStacksCache) {
                armorDurabilityTextsCache.add(module.formatDurability(stack.getMaxDamage(), stack.getMaxDamage()));
            }
        }

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

        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();
        float baseWidth = iconSize + textGap + maxTextWidth + numberSidePadding;
        float baseHeight = stacks.size() * rowHeight;
        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;

        float maxX = Math.max(0.0f, screenWidth - hudWidth);
        float maxY = Math.max(0.0f, screenHeight - hudHeight);
        float x = module.getHudX();
        float y = module.getHudY();
        int currentScreenWidth = Math.round(screenWidth);
        int currentScreenHeight = Math.round(screenHeight);
        module.setHudLayoutSize(hudWidth, hudHeight);
        boolean resized = armorLayoutInitialized
                && (armorLastScreenWidth != currentScreenWidth || armorLastScreenHeight != currentScreenHeight);
        x = MathHelper.clamp(x, 0.0f, maxX);
        y = MathHelper.clamp(y, 0.0f, maxY);
        module.setHudX(x);
        module.setHudY(y);
        armorPositionRatioX = maxX <= 0.0f ? 0.0f : x / maxX;
        armorPositionRatioY = maxY <= 0.0f ? 0.0f : y / maxY;
        armorLastScreenWidth = currentScreenWidth;
        armorLastScreenHeight = currentScreenHeight;
        armorLayoutInitialized = true;

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
        float handleX = x + hudWidth - handleSize / 2.0f;
        float handleY = y + hudHeight - handleSize / 2.0f;
        boolean hovered = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;
        boolean handleInput = !PHAZE_HUD_PASS.isBatchPass();

        if (!chatEditing && handleInput) {
            armorDragging = false;
            armorResizing = false;
            armorHoverProgress = approachExp(armorHoverProgress, 0.0f, 10.0f, deltaSeconds);
        } else if (chatEditing && handleInput) {
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
                    boolean snappedToScreenX = false;
                    boolean snappedToScreenY = false;

                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5f, 0.0f, maxX);
                        showVerticalGuideThisFrame = true;
                        snappedToScreenX = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5f, 0.0f, maxY);
                        showHorizontalGuideThisFrame = true;
                        snappedToScreenY = true;
                    }
                    if (!snappedToScreenX) {
                        newX = snapHudAlignmentX(HUD_SNAP_ARMOR, newX, newY, hudWidth, hudHeight, maxX, 1.0f);
                    }
                    if (!snappedToScreenY) {
                        newY = snapHudAlignmentY(HUD_SNAP_ARMOR, newX, newY, hudWidth, hudHeight, maxY, 1.0f);
                    }

                    module.setHudX(newX);
                    module.setHudY(newY);
                    x = newX;
                    y = newY;
                    armorPositionRatioX = maxX <= 0.0f ? 0.0f : x / maxX;
                    armorPositionRatioY = maxY <= 0.0f ? 0.0f : y / maxY;
                    handleX = x + hudWidth - handleSize / 2.0f;
                    handleY = y + hudHeight - handleSize / 2.0f;
                } else if (armorResizing) {
                    float deltaX = (float) mouseX - armorResizeStartMouseX;
                    float deltaY = (float) mouseY - armorResizeStartMouseY;
                    float delta = (deltaX + deltaY) * 0.5f;
                    // Match resize feel of other rect HUDs (reference width = 64).
                    float newScale = (armorResizeStartScale + (delta * 0.9f) / BASE_WIDTH) / 2.0F;
                    newScale = snapAndAnnounceHudScale(
                            module, newScale, module.getMinHudScale(), module.getMaxHudScale());
                    module.setHudScale(newScale);
                    scale = module.getRenderHudScale();
                    hudWidth = baseWidth * scale;
                    hudHeight = baseHeight * scale;
                    maxX = Math.max(0.0f, screenWidth - hudWidth);
                    maxY = Math.max(0.0f, screenHeight - hudHeight);
                    x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
                    y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
                    module.setHudX(x);
                    module.setHudY(y);
                    armorPositionRatioX = maxX <= 0.0f ? 0.0f : x / maxX;
                    armorPositionRatioY = maxY <= 0.0f ? 0.0f : y / maxY;
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

        rememberHudSnapBounds(HUD_SNAP_ARMOR, module, x, y, hudWidth, hudHeight);

        if (chatEditing && handleInput) {
            if (armorResizing) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (armorDragging) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
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
                float normalizedBlurRadius = blurRadius;
                float blurX = 0.0f;
                float blurY = 0.0f;
                float blurWidth = baseWidth;
                float blurHeight = baseHeight;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, blurX, blurY, blurWidth, blurHeight);
                Blur.INSTANCE.registerHudBlurState(HUD_ARMOR_BLUR_SLOT, blurStateKey);

                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(), blurX, blurY, blurWidth, blurHeight)
                            .round(getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            if (module.isGradientPreset()) {
                int hoverFill = chatEditing ? withAlpha(0xFFFFFF, (int) (30.0f * armorHoverProgress)) : 0;
                renderGradientRect(context, 0.0F, 0.0F, baseWidth, baseHeight, hoverFill,
                        module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                        module.getGradientDirection(), module.getGradientAnimationOffset(System.currentTimeMillis()),
                        module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
                context.draw();
            } else {
                int targetBgColor = module.getResolvedBackgroundColor(client);
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
                renderSolidHudBackground(context, 0.0F, 0.0F, baseWidth, baseHeight, bgColor,
                        module.cornerRounding.getValue());
                context.draw();
            }
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
            if (textColor == HUD_TEXT_COLOR) {
                textColor = resolveHudTextColor();
            }

            renderScaledHudTextColored(context, client, durabilityText, x, y, textX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), textColor);
        }

        if (chatEditing && handleInput && armorHoverProgress > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (160.0f * armorHoverProgress));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && handleInput && (armorResizing || hoveredHandle || hovered || nearHud);
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
    private static void renderConsumableHud(
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

        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();
        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;

        float maxX = Math.max(0.0f, screenWidth - hudWidth);
        float maxY = Math.max(0.0f, screenHeight - hudHeight);
        float x = MathHelper.clamp(module.getHudX(), 0.0f, maxX);
        float y = MathHelper.clamp(module.getHudY(), 0.0f, maxY);
        module.setHudLayoutSize(hudWidth, hudHeight);
        module.setHudX(x);
        module.setHudY(y);

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0f * scale)));
        float handleX = x + hudWidth - handleSize;
        float handleY = y + hudHeight - handleSize;
        boolean hovered = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;
        boolean handleInput = !PHAZE_HUD_PASS.isBatchPass();

        if (!chatEditing && handleInput) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], 0.0f, 10.0f, deltaSeconds);
        } else if (chatEditing && handleInput) {
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
                    boolean snappedToScreenX = false;
                    boolean snappedToScreenY = false;
                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5f, 0.0f, maxX);
                        showVerticalGuideThisFrame = true;
                        snappedToScreenX = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5f, 0.0f, maxY);
                        showHorizontalGuideThisFrame = true;
                        snappedToScreenY = true;
                    }
                    if (!snappedToScreenX) {
                        newX = snapHudAlignmentX(hudIndex, newX, newY, hudWidth, hudHeight, maxX, 1.0f);
                    }
                    if (!snappedToScreenY) {
                        newY = snapHudAlignmentY(hudIndex, newX, newY, hudWidth, hudHeight, maxY, 1.0f);
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
                    float newScale =
                            (RECT_RESIZE_START_WIDTH[hudIndex] + (delta * 0.9f) / BASE_WIDTH) / 2.0F;
                    newScale = snapAndAnnounceHudScale(
                            module, newScale, module.getMinHudScale(), module.getMaxHudScale());
                    module.setHudScale(newScale);
                    scale = module.getRenderHudScale();
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

        rememberHudSnapBounds(hudIndex, module, x, y, hudWidth, hudHeight);

        if (chatEditing && handleInput) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
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
                float normalizedBlurRadius = blurRadius;
                float blurX = 0.0f;
                float blurY = 0.0f;
                float blurWidth = baseWidth;
                float blurHeight = baseHeight;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, blurX, blurY, blurWidth, blurHeight);
                Blur.INSTANCE.registerHudBlurState(hudIndex, blurStateKey);

                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(), blurX, blurY, blurWidth, blurHeight)
                            .round(getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            if (module.isGradientPreset()) {
                int hoverFill = chatEditing ? withAlpha(0xFFFFFF, (int) (30.0f * RECT_HOVER_PROGRESS[hudIndex])) : 0;
                renderGradientRect(context, 0.0F, 0.0F, baseWidth, baseHeight, hoverFill,
                        module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                        module.getGradientDirection(), module.getGradientAnimationOffset(System.currentTimeMillis()),
                        module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
                context.draw();
            } else {
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
                renderSolidHudBackground(context, 0.0F, 0.0F, baseWidth, baseHeight, bgColor,
                        module.cornerRounding.getValue());
                context.draw();
            }
        }

        // Icon pass: drawItem internally pushes its own
        // model-view stack so we don't have to translate per-icon.
        // Padding mirrors the value computeLayout sized the rect to.
        float padding = 3.0f;
        float itemSize = 18.0f;
        for (vorga.phazeclient.implement.features.modules.hud.Consumable.IconEntry entry : layout.entries()) {
            // The item model's visible pixels are slightly left-heavy within
            // its 16px slot. Reserve one extra base pixel on the left so an
            // icon (and every icon grid cell) is visually centred.
            int iconX = Math.round(padding + 1.0F + entry.col() * itemSize);
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

        if (chatEditing && handleInput && RECT_HOVER_PROGRESS[hudIndex] > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (160.0f * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && handleInput && (RECT_RESIZING[hudIndex] || hoveredHandle || hovered || nearHud);
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

    private static void renderCoordinatesHud(
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
        float scale = module.getRenderHudScale();
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
                renderScaledHudTextColored(context, client, label, x, y, paddingX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), resolveHudTextColor());
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

    private static void renderPingHud(
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
        float scale = module.getRenderHudScale();
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
                pingColor = resolveHudTextColor();
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

    private static void renderSimpleTextHud(
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

    private static void renderTrapTimerHud(
            DrawContext context,
            MinecraftClient client,
            TrapTimer module,
            Text text,
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
        float baseWidth = Math.max(48.0F, client.textRenderer.getWidth(text) + 14.0F);
        module.ensureDefaultHudPosition(screenWidth, screenHeight, baseWidth, BASE_HEIGHT);
        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getRenderHudScale();

        renderRectHud(context, client, module, "", hudIndex, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, BASE_HEIGHT);

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0F);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z + 25.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        float textX = 7.0F;
        float textY = (BASE_HEIGHT - 9.0F) * 0.5F;
        context.drawText(client.textRenderer, text, Math.round(textX), Math.round(textY), resolveHudTextColor(), module.textShadow.isValue());
        context.draw();
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    private static void renderSessionTimeHud(
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
        float scale = module.getRenderHudScale();
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
        context.drawText(client.textRenderer, text, Math.round(textX), Math.round(textY), resolveHudTextColor(), module.textShadow.isValue());
        context.draw();
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

    private static void renderScoreboardHud(
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

        NickHider nickHider = NickHider.getInstance();
        boolean shouldHideNick = nickHider != null && nickHider.isEnabled();

        var sidebarEntries = new java.util.ArrayList<ScoreboardSidebarEntry>();
        for (var entry : filteredEntries) {
            var team = scoreboard.getScoreHolderTeam(entry.owner());
            var name = entry.name();
            net.minecraft.text.Text decoratedName = net.minecraft.scoreboard.Team.decorateName(team, name);
            if (shouldHideNick) {
                decoratedName = nickHider.rewrite(decoratedName);
            }
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

            sidebarEntries.add(new ScoreboardSidebarEntry(decoratedName, formattedScore, hasScore, scoreWidth));
        }

        // Calculate dimensions
        net.minecraft.text.Text title = objective.getDisplayName();
        if (shouldHideNick) {
            title = nickHider.rewrite(title);
        }
        int titleWidth = (int)getHudTextWidth(client, title.getString(), HUD_TEXT_SIZE);
        int maxContentWidth = titleWidth;
        int entryCount = sidebarEntries.size();

        for (var sidebarEntry : sidebarEntries) {
            int textWidth = (int)getHudTextWidth(client, sidebarEntry.name().getString(), HUD_TEXT_SIZE);
            boolean hideZeroScore = sidebarEntry.hasScore()
                    && module.showNumbers.isValue()
                    && !module.showZeros.isValue()
                    && isZeroScoreText(sidebarEntry.score());
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

        // If the HUD has not been placed/loaded yet, place it at
        // vanilla-like sidebar position (right side, vertically centered).
        if (!module.isPositionInitialized()) {
            float scale = module.getRenderHudScale();
            float hudWidth = baseWidth * scale;
            float hudHeight = baseHeight * scale;
            float vanillaX = Math.max(0.0f, screenWidth - hudWidth - 2.0f);
            float vanillaY = Math.max(0.0f, (screenHeight - hudHeight) * 0.5f);
            module.setHudX(vanillaX);
            module.setHudY(vanillaY);
            module.setPositionInitialized(true);
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
        float hudScale = module.getRenderHudScale();

        // Calculate render positions in local coordinates (relative to hudX, hudY)
        int rightEdgeLocal = Math.round(baseWidth);
        int textLeftLocal = 2;
        int verticalPosLocal = entryCount * 9;
        boolean gradientBackground = module.isGradientPreset();

        // Get colors
        int targetTitleBgColor;
        int targetRowBgColor;
        if (module.shouldUseVanillaColors()) {
            targetTitleBgColor = client.options.getTextBackgroundColor(0.4F);
            targetRowBgColor = client.options.getTextBackgroundColor(0.3F);
        } else {
            targetTitleBgColor = module.getResolvedBackgroundColor(client);
            targetRowBgColor = targetTitleBgColor;
        }

        int titleBgColor;
        int rowBgColor;
        if (!scoreboardBackgroundColorsInitialized) {
            scoreboardAnimatedTitleColor = targetTitleBgColor;
            scoreboardAnimatedRowColor = targetRowBgColor;
            scoreboardBackgroundColorsInitialized = true;
        } else {
            scoreboardAnimatedTitleColor = approachColorExp(scoreboardAnimatedTitleColor, targetTitleBgColor, 12.0f, deltaSeconds);
            scoreboardAnimatedRowColor = approachColorExp(scoreboardAnimatedRowColor, targetRowBgColor, 12.0f, deltaSeconds);
        }
        titleBgColor = scoreboardAnimatedTitleColor;
        rowBgColor = scoreboardAnimatedRowColor;

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

            float blurRadius = Math.max(0.0f, module.backgroundBlurRadius.getValue());
            if (blurRadius > 0.0f) {
                // Match the shared RectHud blur path exactly. The old
                // scoreboard-only path fed the raw radius through a scaled
                // matrix, so enlarging the scoreboard also multiplied its
                // apparent blur strength.
                float safeScale = Math.max(hudScale, 1.0f);
                float normalizedBlurRadius = blurRadius;
                float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
                float blurWidth = rightEdgeLocal;
                float blurHeight = backgroundBottomLocal - backgroundTopLocal;
                long blurStateKey = makeHudBlurStateKey(
                        normalizedBlurRadius,
                        safeScale,
                        0.0f,
                        backgroundTopLocal,
                        blurWidth,
                        blurHeight
                );
                Blur.INSTANCE.registerHudBlurState(HUD_SCOREBOARD, blurStateKey);
                context.draw();
                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(),
                                    0.0f, backgroundTopLocal, blurWidth, blurHeight)
                            .round(getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
                renderScoreboardBackground(module, context, gradientBackground, topInset, rightEdgeLocal, verticalPosLocal,
                        titleBgColor, rowBgColor);
                context.draw();
            } else {
                renderScoreboardBackground(module, context, gradientBackground, topInset, rightEdgeLocal, verticalPosLocal,
                        titleBgColor, rowBgColor);
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
                if (!module.showZeros.isValue() && isZeroScoreText(sidebarEntry.score())) {
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

    private static void renderScoreboardBackground(
            ScoreboardHud module,
            DrawContext context,
            boolean gradientBackground,
            int topInset,
            int rightEdgeLocal,
            int verticalPosLocal,
            int titleBgColor,
            int rowBgColor
    ) {
        if (gradientBackground) {
            if (module.showTitle.isValue()) {
                renderGradientRect(context, 0.0F, -topInset, rightEdgeLocal, topInset - 1.0F, 0,
                        module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                        module.getGradientDirection(), module.getGradientAnimationOffset(System.currentTimeMillis()),
                        module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
            }
            renderGradientRect(context, 0.0F, -1.0F, rightEdgeLocal, verticalPosLocal + 1.0F, 0,
                    module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                    module.getGradientDirection(), module.getGradientAnimationOffset(System.currentTimeMillis()),
                    module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
            context.draw();
            return;
        }
        if (module.showTitle.isValue()) {
            renderSolidHudBackground(context, 0.0F, -topInset, rightEdgeLocal, topInset - 1.0F,
                    titleBgColor, module.cornerRounding.getValue());
        }
        renderSolidHudBackground(context, 0.0F, -1.0F, rightEdgeLocal, verticalPosLocal + 1.0F,
                rowBgColor, module.cornerRounding.getValue());
    }

    private static boolean shouldBypassHudBatchingForCurrentScreen(MinecraftClient client) {
        return client != null
                && client.world != null
                && client.currentScreen != null
                && !(client.currentScreen instanceof ChatScreen)
                && !(client.currentScreen instanceof MenuScreen);
    }

    private static boolean isZeroScoreText(Text scoreText) {
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

    private static void renderDirectionHud(
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
        float scale = module.getRenderHudScale();
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

    private static void renderKeystrokesHud(
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

        float baseWidth = 54.0f;
        float baseHeight = 63.0f;
        renderRectHud(context, client, module, "", HUD_KEYSTROKES, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getRenderHudScale();
        int idleColor = module.background.isValue()
                ? RECT_BG_ANIMATED_COLOR[HUD_KEYSTROKES]
                : 0x00000000;

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z + 20.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderKeystrokeButtonBlur(context, module, scale);
        float cachedProgressScale = PHAZE_HUD_PASS.isBatchPass() ? 0.0f : 1.0f;
        if (module.background.isValue() && module.isGradientPreset()) {
            renderGradientKeyButton(context, module, 20, 0, 16, 18, KEYSTROKE_PROGRESS[KEYSTROKE_W] * cachedProgressScale);
            renderGradientKeyButton(context, module, 0, 19, 18, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A] * cachedProgressScale);
            renderGradientKeyButton(context, module, 19, 19, 16, 18, KEYSTROKE_PROGRESS[KEYSTROKE_S] * cachedProgressScale);
            renderGradientKeyButton(context, module, 36, 19, 18, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D] * cachedProgressScale);
            renderGradientKeyButton(context, module, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] * cachedProgressScale);
            renderGradientKeyButton(context, module, 0, 47, 26, 16, KEYSTROKE_PROGRESS[KEYSTROKE_LMB] * cachedProgressScale);
            renderGradientKeyButton(context, module, 28, 47, 26, 16, KEYSTROKE_PROGRESS[KEYSTROKE_RMB] * cachedProgressScale);
        } else {
            renderKeyButton(context, module, 20, 0, 16, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_W] * cachedProgressScale);
            renderKeyButton(context, module, 0, 19, 18, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_A] * cachedProgressScale);
            renderKeyButton(context, module, 19, 19, 16, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_S] * cachedProgressScale);
            renderKeyButton(context, module, 36, 19, 18, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_D] * cachedProgressScale);
            renderKeyButton(context, module, 0, 38, 54, 8, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] * cachedProgressScale);
            renderKeyButton(context, module, 0, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_LMB] * cachedProgressScale);
            renderKeyButton(context, module, 28, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_RMB] * cachedProgressScale);
        }
        context.draw();
        context.getMatrices().pop();

        renderKeyLabel(context, client, "W", x, y, 20, 4, 16, KEYSTROKE_PROGRESS[KEYSTROKE_W] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "A", x, y, 0, 23, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "S", x, y, 19, 23, 16, KEYSTROKE_PROGRESS[KEYSTROKE_S] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "D", x, y, 36, 23, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D] * cachedProgressScale, scale, module.textShadow.isValue());
        renderSpacebarLabel(context, x, y, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] * cachedProgressScale, scale);
        renderKeyLabel(context, client, "LMB", x, y, 0, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_LMB] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "RMB", x, y, 28, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_RMB] * cachedProgressScale, scale, module.textShadow.isValue());
        context.getMatrices().pop();
    }

    private static void renderPotionHud(
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

        List<StatusEffectInstance> updatedEffects = new ArrayList<>(client.player.getStatusEffects());
        boolean updatedSample = chatEditing && updatedEffects.isEmpty();
        long previewCycle = updatedSample ? System.currentTimeMillis() / 3_000L : Long.MIN_VALUE;
        if (updatedSample && potionSampleCycle != previewCycle) {
            potionSampleIndex = POTION_PREVIEW_RANDOM.nextInt(POTION_PREVIEW_SAMPLES.length);
        }
        if (!potionCacheInitialized || !chatEditing || !updatedSample
                || !potionSampleCache || potionSampleCycle != previewCycle) {
            updatedEffects.sort(Comparator.comparing(effect -> effect.getEffectType().value().getName().getString()));
            List<String> updatedNames = new ArrayList<>();
            List<String> updatedDurations = new ArrayList<>();
            if (updatedSample) {
                String[] preview = POTION_PREVIEW_SAMPLES[potionSampleIndex];
                updatedNames.add(preview[0]);
                updatedDurations.add(preview[1]);
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
            potionSampleCycle = previewCycle;
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
        float scale = module.getRenderHudScale();
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
            int nameColor = resolveHudTextColor();
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

    private static void updateKeystrokeAnimations(
            MinecraftClient client,
            boolean leftMouseDown,
            boolean rightMouseDown,
            float deltaSeconds
    ) {
        if (client == null || client.options == null) {
            return;
        }
        updateKeystroke(KEYSTROKE_W, client.options.forwardKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_A, client.options.leftKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_S, client.options.backKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_D, client.options.rightKey.isPressed(), deltaSeconds);
        updateKeystroke(KEYSTROKE_LMB, leftMouseDown && client.currentScreen == null, deltaSeconds);
        updateKeystroke(KEYSTROKE_RMB, rightMouseDown && client.currentScreen == null, deltaSeconds);
        updateKeystroke(KEYSTROKE_SPACE, client.options.jumpKey.isPressed(), deltaSeconds);
    }

    private static void updateKeystroke(int index, boolean pressed, float deltaSeconds) {
        KEYSTROKE_PROGRESS[index] = approachExp(KEYSTROKE_PROGRESS[index], pressed ? 1.0f : 0.0f, 16.0f, deltaSeconds);
    }

    /**
     * Draws only the input-dependent white press layer over the cached
     * Keystrokes HUD. The backdrop, button idle colors, labels and blur stay
     * inside BatchedHudBuffer and therefore keep obeying HudOptimizer's FPS
     * limit; this tiny overlay is the only part submitted every display frame.
     */
    private static void renderLiveKeystrokeAnimation(
            DrawContext context,
            MinecraftClient client,
            float inverseGuiScale
    ) {
        KeystrokesHud module = KeystrokesHud.getInstance();
        if (context == null || client == null || module == null || !module.isEnabled()) {
            return;
        }

        boolean hasVisibleProgress = false;
        for (float progress : KEYSTROKE_PROGRESS) {
            if (progress > 0.001f) {
                hasVisibleProgress = true;
                break;
            }
        }
        if (!hasVisibleProgress) {
            return;
        }

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getRenderHudScale();

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, HUD_RENDER_Z + 20.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        renderLiveKeystrokeButton(context, module, 20, 0, 16, 18, KEYSTROKE_PROGRESS[KEYSTROKE_W]);
        renderLiveKeystrokeButton(context, module, 0, 19, 18, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A]);
        renderLiveKeystrokeButton(context, module, 19, 19, 16, 18, KEYSTROKE_PROGRESS[KEYSTROKE_S]);
        renderLiveKeystrokeButton(context, module, 36, 19, 18, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D]);
        renderLiveKeystrokeButton(context, module, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE]);
        renderLiveKeystrokeButton(context, module, 0, 47, 26, 16, KEYSTROKE_PROGRESS[KEYSTROKE_LMB]);
        renderLiveKeystrokeButton(context, module, 28, 47, 26, 16, KEYSTROKE_PROGRESS[KEYSTROKE_RMB]);
        context.draw();
        context.getMatrices().pop();

        boolean shadow = module.textShadow.isValue();
        renderLiveKeystrokeLabel(context, client, "W", x, y, 20, 4, 16, KEYSTROKE_PROGRESS[KEYSTROKE_W], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "A", x, y, 0, 23, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "S", x, y, 19, 23, 16, KEYSTROKE_PROGRESS[KEYSTROKE_S], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "D", x, y, 36, 23, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D], scale, shadow);
        if (KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] > 0.45f) {
            renderSpacebarLabel(context, x, y, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE], scale);
        }
        renderLiveKeystrokeLabel(context, client, "LMB", x, y, 0, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_LMB], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "RMB", x, y, 28, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_RMB], scale, shadow);
        context.getMatrices().pop();
    }

    private static void renderLiveKeystrokeButton(
            DrawContext context,
            KeystrokesHud module,
            int x,
            int y,
            int width,
            int height,
            float progress
    ) {
        int alpha = MathHelper.clamp(Math.round(185.0f * progress), 0, 185);
        if (alpha > 0) {
            renderSolidHudBackground(context, x, y, width, height, withAlpha(0xFFFFFF, alpha),
                    module.cornerRounding.getValue());
        }
    }

    private static void renderLiveKeystrokeLabel(
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
        if (progress > 0.45f) {
            renderKeyLabel(context, client, label, hudX, hudY, keyX, keyY, keyWidth, progress, scale, shadow);
        }
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
        float normalizedBlurRadius = blurRadius;
        float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
        if (blurQuality <= 0.0f) {
            return;
        }
        long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, 0.0f, 0.0f, 54.0f, 63.0f);
        Blur.INSTANCE.registerHudBlurState(HUD_KEYSTROKES, blurStateKey);
        for (ShapeProperties shape : KEYSTROKE_BLUR_RECTS) {
            shape.setMatrix(context.getMatrices());
            shape.setQuality(blurQuality);
            shape.getRound().set(getClampedHudCornerRadius(
                    module.cornerRounding.getValue(), shape.getWidth(), shape.getHeight()));
        }
        Blur.INSTANCE.renderCachedBatch(KEYSTROKE_BLUR_RECTS);
    }

    private static ShapeProperties createKeystrokeBlurRect(float x, float y, float width, float height) {
        return ShapeProperties.create(null, x, y, width, height)
                .round(0.0f)
                .softness(0.0f)
                .color(0xFFFFFFFF)
                .build();
    }
    private static void renderKeyButton(DrawContext context, KeystrokesHud module, int x, int y, int width, int height, int idleColor, float progress) {
        int activeOverlay = withAlpha(0xFFFFFF, Math.round(185.0f * progress));
        int color = blendARGB(idleColor, activeOverlay);
        renderSolidHudBackground(context, x, y, width, height, color, module.cornerRounding.getValue());
    }

    private static void renderGradientKeyButton(
            DrawContext context,
            KeystrokesHud module,
            int x,
            int y,
            int width,
            int height,
            float progress
    ) {
        renderGradientRect(context, x, y, width, height, withAlpha(0xFFFFFF, Math.round(185.0F * progress)),
                module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                module.getGradientDirection(), module.getGradientAnimationOffset(System.currentTimeMillis()),
                module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
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
        int color = progress > 0.45f ? 0x111111 : resolveHudTextColor();
        renderScaledHudTextColored(context, client, label, hudX, hudY, keyX + (keyWidth - textWidth) * 0.5f, keyY, HUD_TEXT_SIZE, scale, shadow, color);
    }

    private static void renderSpacebarLabel(DrawContext context, float hudX, float hudY, float keyX, float keyY, float keyWidth, float keyHeight, float progress, float scale) {
        int color = progress > 0.45f ? 0x111111 : resolveHudTextColor();
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

    private static float getOptimizedHudBlurQuality(float normalizedBlurRadius) {
        // Blur samples screen-space textures: widget matrix scale must not divide its radius.
        float radius = Math.max(0.0f, normalizedBlurRadius);
        if (radius <= 0.001f) {
            return 0.0f;
        }

        // Direct, continuous mapping for HUD blur. The old curve
        // compressed almost the entire slider into a tiny effective
        // blur radius, so the first non-zero values looked like the
        // blur instantly "turned on" and then barely changed.
        float t = MathHelper.clamp(radius / 32.0f, 0.0f, 1.0f);
        float strength = MathHelper.lerp(t, 0.14f, 0.34f);
        return radius * strength;
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
                || TrapTimer.getInstance().isEnabled()
                || vorga.phazeclient.implement.features.modules.hud.Consumable.getInstance().isEnabled()
                || PaperDoll.getInstance().isEnabled()
                || InventoryHud.getInstance().isEnabled()
                || PackDisplayHud.getInstance().isEnabled()
                || ScoreboardHud.getInstance().isEnabled()
                || BossBarHud.getInstance().isEnabled() && MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
    }

    private static boolean isAnyHudInteractionActive() {
        for (int index = 0; index < RECT_HUD_COUNT; index++) {
            if (RECT_DRAGGING[index] || RECT_RESIZING[index]) return true;
        }
        return armorDragging || armorResizing;
    }

    /**
     * Keeps the natural resize range while making the original 1:1 HUD size
     * easy to recover. The overlay is updated only from active chat/HUD resize
     * paths, so ordinary GUI Scale sliders never produce this readout.
     */
    private static float snapAndAnnounceHudScale(Module module, float rawScale, float minScale, float maxScale) {
        float clamped = MathHelper.clamp(rawScale, minScale, maxScale);
        boolean canReachDefault = minScale <= 1.0F && maxScale >= 1.0F;
        boolean snapped = canReachDefault && clamped >= 0.92F && clamped <= 1.08F;
        float value = snapped ? 1.0F : clamped;
        ScaleSnapOverlay.show(value, snapped, module.getVisibleName() + " Scale");
        return value;
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

        if (hudVerticalGuideProgress > 0.01f && hudVerticalGuideBottom > hudVerticalGuideTop) {
            int alpha = MathHelper.clamp(Math.round(GUIDE_MAX_ALPHA * hudVerticalGuideProgress), 0, 255);
            int x = Math.round(hudVerticalGuideX);
            int top = MathHelper.floor(hudVerticalGuideTop);
            int bottom = MathHelper.ceil(hudVerticalGuideBottom);
            context.fill(x, top, x + 1, bottom, withAlpha(0xFFFFFF, alpha));
        }

        if (hudHorizontalGuideProgress > 0.01f && hudHorizontalGuideRight > hudHorizontalGuideLeft) {
            int alpha = MathHelper.clamp(Math.round(GUIDE_MAX_ALPHA * hudHorizontalGuideProgress), 0, 255);
            int y = Math.round(hudHorizontalGuideY);
            int left = MathHelper.floor(hudHorizontalGuideLeft);
            int right = MathHelper.ceil(hudHorizontalGuideRight);
            context.fill(left, y, right, y + 1, withAlpha(0xFFFFFF, alpha));
        }

        context.getMatrices().pop();
    }

    private static void rememberHudSnapBounds(
            int hudIndex,
            Module owner,
            float x,
            float y,
            float width,
            float height
    ) {
        if (hudIndex < 0 || hudIndex >= HUD_SNAP_COUNT || owner == null || width <= 0.0f || height <= 0.0f) {
            return;
        }
        HUD_SNAP_X[hudIndex] = x;
        HUD_SNAP_Y[hudIndex] = y;
        HUD_SNAP_WIDTH[hudIndex] = width;
        HUD_SNAP_HEIGHT[hudIndex] = height;
        HUD_SNAP_OWNER[hudIndex] = owner;
        HUD_SNAP_VALID[hudIndex] = true;
        MinecraftClient client = MinecraftClient.getInstance();
        float pixelScale = hudPixelScale(owner, client);
        HUD_LAYOUT_BASE_WIDTH[hudIndex] = width / pixelScale;
        HUD_LAYOUT_BASE_HEIGHT[hudIndex] = height / pixelScale;
        // Persist the final resized footprint, not the pre-resize size used at the start of rendering.
        float coordinateScale = owner instanceof PaperDoll ? (float) client.getWindow().getScaleFactor() : 1;
        if (owner instanceof RectHudModule rect) rect.setHudLayoutSize(width / coordinateScale, height / coordinateScale);
        else ((ArmorHud) owner).setHudLayoutSize(width, height);
    }

    private static float snapHudAlignmentX(
            int movingIndex,
            float x,
            float y,
            float width,
            float height,
            float maxX,
            float coordinateScale
    ) {
        float safeCoordinateScale = Math.max(0.001f, coordinateScale);
        float bestDistance = Float.MAX_VALUE;
        int bestIndex = -1;
        float bestTargetAnchor = 0.0f;
        float bestMovingAnchorOffset = 0.0f;

        for (int index = 0; index < HUD_SNAP_COUNT; index++) {
            Module owner = HUD_SNAP_OWNER[index];
            if (index == movingIndex || !HUD_SNAP_VALID[index] || owner == null || !owner.isEnabled()) {
                continue;
            }
            float targetLeft = HUD_SNAP_X[index] / safeCoordinateScale;
            float targetCenterX = (HUD_SNAP_X[index] + HUD_SNAP_WIDTH[index] * 0.5f) / safeCoordinateScale;
            float targetRight = (HUD_SNAP_X[index] + HUD_SNAP_WIDTH[index]) / safeCoordinateScale;

            // Center wins equal-distance ties, then matching outer edges.
            float distance = Math.abs((x + width * 0.5f) - targetCenterX);
            if (distance <= HUD_TO_HUD_SNAP_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
                bestTargetAnchor = targetCenterX;
                bestMovingAnchorOffset = width * 0.5f;
            }

            distance = Math.abs(x - targetLeft);
            if (distance <= HUD_TO_HUD_SNAP_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
                bestTargetAnchor = targetLeft;
                bestMovingAnchorOffset = 0.0f;
            }

            distance = Math.abs((x + width) - targetRight);
            if (distance <= HUD_TO_HUD_SNAP_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
                bestTargetAnchor = targetRight;
                bestMovingAnchorOffset = width;
            }
        }

        if (bestIndex < 0) {
            return x;
        }

        float snappedX = MathHelper.clamp(bestTargetAnchor - bestMovingAnchorOffset, 0.0f, maxX);
        if (Math.abs((snappedX + bestMovingAnchorOffset) - bestTargetAnchor) > 0.01f) {
            return x;
        }
        showHudVerticalGuideThisFrame = true;
        hudVerticalGuideX = bestTargetAnchor * safeCoordinateScale;
        hudVerticalGuideTop = Math.min(y * safeCoordinateScale, HUD_SNAP_Y[bestIndex]);
        hudVerticalGuideBottom = Math.max(
                (y + height) * safeCoordinateScale,
                HUD_SNAP_Y[bestIndex] + HUD_SNAP_HEIGHT[bestIndex]
        );
        return snappedX;
    }

    private static float snapHudAlignmentY(
            int movingIndex,
            float x,
            float y,
            float width,
            float height,
            float maxY,
            float coordinateScale
    ) {
        float safeCoordinateScale = Math.max(0.001f, coordinateScale);
        float bestDistance = Float.MAX_VALUE;
        int bestIndex = -1;
        float bestTargetAnchor = 0.0f;
        float bestMovingAnchorOffset = 0.0f;

        for (int index = 0; index < HUD_SNAP_COUNT; index++) {
            Module owner = HUD_SNAP_OWNER[index];
            if (index == movingIndex || !HUD_SNAP_VALID[index] || owner == null || !owner.isEnabled()) {
                continue;
            }
            float targetTop = HUD_SNAP_Y[index] / safeCoordinateScale;
            float targetCenterY = (HUD_SNAP_Y[index] + HUD_SNAP_HEIGHT[index] * 0.5f) / safeCoordinateScale;
            float targetBottom = (HUD_SNAP_Y[index] + HUD_SNAP_HEIGHT[index]) / safeCoordinateScale;

            float distance = Math.abs((y + height * 0.5f) - targetCenterY);
            if (distance <= HUD_TO_HUD_SNAP_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
                bestTargetAnchor = targetCenterY;
                bestMovingAnchorOffset = height * 0.5f;
            }

            distance = Math.abs(y - targetTop);
            if (distance <= HUD_TO_HUD_SNAP_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
                bestTargetAnchor = targetTop;
                bestMovingAnchorOffset = 0.0f;
            }

            distance = Math.abs((y + height) - targetBottom);
            if (distance <= HUD_TO_HUD_SNAP_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
                bestTargetAnchor = targetBottom;
                bestMovingAnchorOffset = height;
            }
        }

        if (bestIndex < 0) {
            return y;
        }

        float snappedY = MathHelper.clamp(bestTargetAnchor - bestMovingAnchorOffset, 0.0f, maxY);
        if (Math.abs((snappedY + bestMovingAnchorOffset) - bestTargetAnchor) > 0.01f) {
            return y;
        }
        showHudHorizontalGuideThisFrame = true;
        hudHorizontalGuideY = bestTargetAnchor * safeCoordinateScale;
        hudHorizontalGuideLeft = Math.min(x * safeCoordinateScale, HUD_SNAP_X[bestIndex]);
        hudHorizontalGuideRight = Math.max(
                (x + width) * safeCoordinateScale,
                HUD_SNAP_X[bestIndex] + HUD_SNAP_WIDTH[bestIndex]
        );
        return snappedY;
    }

    private static void renderMemoryHud(
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

    private static void renderTpsHud(
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

    private static void renderComboHud(
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

    private static void renderServerAddressHud(
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
            float scale = module.getRenderHudScale();
            float hudHeight = BASE_HEIGHT * scale;
            module.renderServerIcon(context, module.getHudX(), module.getHudY(), hudHeight, inverseGuiScale);
        }
    }

    private static void renderWailaHud(
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
        float scale = module.getRenderHudScale();
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

        // The rect is drawn exactly at module.getHudY(): the editor outline, resize
        // handle, drag hit-test and isolated-HUD cache all read that same coordinate.
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
    }

    private static void renderZoomLevel(DrawContext context, MinecraftClient client, float screenWidth, float screenHeight) {
        if (!Zoom.getInstance().isShowCurrentZoom() || !Zoom.isZoomActive()) {
            return;
        }

        String displayText = Zoom.getInstance().getFormattedZoomLevel();

        float textWidth = client.textRenderer.getWidth(displayText);
        float x = (screenWidth - textWidth) / 2.0f;
        float y = screenHeight - 50.0f;

        context.drawText(client.textRenderer, Text.literal(displayText), (int)x, (int)y, 0xFFFFFFFF, true);
    }

    // ---------- PaperDoll editor state ----------
    private static boolean pmWasMouseDown = false;

    // ---------- InventoryHud drag state ----------
    private static final int PHAZE_INV_SLOT = 18;
    private static final int PHAZE_INV_ICON = 16;
    private static final int PHAZE_INV_BORDER = 7;
    private static final int PHAZE_INV_SLOT_OFFSET = 8;
    private static final Identifier PHAZE_INV_PANEL_SPRITE = Identifier.of("phaze", "shulker_box_tooltip");

    // ====================================================================
    // 8) PaperDoll: shared throttled HUD dispatcher
    // ====================================================================

    private static void renderPaperDollHud(DrawContext context) {
        PaperDoll module = PaperDoll.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.options == null) return;
        if (mc.options.hudHidden) return;

        boolean chatEditing = mc.currentScreen instanceof ChatScreen;
        Entity subject = PaperDollRenderer.subject(mc);
        if (subject == null || !chatEditing && PaperDollRenderer.isHidden(mc, module, subject)) return;

        final int hudIndex = HUD_PAPER_DOLL;
        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();
        float panelW = PaperDoll.BASE_WIDTH * scale;
        float panelH = PaperDoll.BASE_HEIGHT * scale;

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
        module.setHudLayoutSize(panelW, panelH);
        module.setHudX(panelX);
        module.setHudY(panelY);

        final float hoverInsetX = 7.0F;
        float hoverX = panelX + hoverInsetX;
        float hoverW = Math.max(12.0F, panelW - hoverInsetX * 2.0F);
        int handleSize = Math.max(4, Math.min(8, Math.round(4.0F * scale)));
        float handleX = hoverX + hoverW - handleSize / 2.0F;
        float handleY = panelY + panelH - handleSize / 2.0F;
        boolean hoveredHud = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;
        boolean handleInput = !PHAZE_HUD_PASS.isBatchPass();

        if (chatEditing && handleInput) {
            hoveredHud = isHovered(mouseX, mouseY, hoverX, panelY, hoverW, panelH);
            hoveredHandle = isHovered(mouseX, mouseY, handleX, handleY, handleSize, handleSize);
            nearHud = isNearRect(mouseX, mouseY, hoverX, panelY, hoverW, panelH, Math.max(12.0F, 10.0F * scale));

            if (!mouseDown) {
                RECT_DRAGGING[hudIndex] = false;
                RECT_RESIZING[hudIndex] = false;
            } else if (!pmWasMouseDown && !isAnyHudInteractionActive()) {
                if (hoveredHandle) {
                    RECT_RESIZING[hudIndex] = true;
                    RECT_RESIZE_START_WIDTH[hudIndex] = scale;
                    RECT_RESIZE_START_MOUSE_X[hudIndex] = mouseX;
                    RECT_RESIZE_START_MOUSE_Y[hudIndex] = mouseY;
                } else if (hoveredHud) {
                    RECT_DRAGGING[hudIndex] = true;
                    RECT_DRAG_OFFSET_X[hudIndex] = mouseX - panelX;
                    RECT_DRAG_OFFSET_Y[hudIndex] = mouseY - panelY;
                }
            }

            if (mouseDown) {
                if (RECT_DRAGGING[hudIndex]) {
                    float newX = MathHelper.clamp(mouseX - RECT_DRAG_OFFSET_X[hudIndex], 0.0F, maxX);
                    float newY = MathHelper.clamp(mouseY - RECT_DRAG_OFFSET_Y[hudIndex], 0.0F, maxY);
                    float dragCenterX = newX + panelW * 0.5F;
                    float dragCenterY = newY + panelH * 0.5F;
                    boolean snappedToScreenX = false;
                    boolean snappedToScreenY = false;

                    if (Math.abs(dragCenterX - scaledScreenW * 0.5F) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(scaledScreenW * 0.5F - panelW * 0.5F, 0.0F, maxX);
                        showVerticalGuideThisFrame = true;
                        snappedToScreenX = true;
                    }
                    if (Math.abs(dragCenterY - scaledScreenH * 0.5F) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(scaledScreenH * 0.5F - panelH * 0.5F, 0.0F, maxY);
                        showHorizontalGuideThisFrame = true;
                        snappedToScreenY = true;
                    }
                    if (!snappedToScreenX) {
                        newX = snapHudAlignmentX(
                                hudIndex, newX, newY, panelW, panelH, maxX, (float) scaleFactor
                        );
                    }
                    if (!snappedToScreenY) {
                        newY = snapHudAlignmentY(
                                hudIndex, newX, newY, panelW, panelH, maxY, (float) scaleFactor
                        );
                    }

                    panelX = newX;
                    panelY = newY;
                    module.setHudX(panelX);
                    module.setHudY(panelY);
                    hoverX = panelX + hoverInsetX;
                    handleX = hoverX + hoverW - handleSize / 2.0F;
                    handleY = panelY + panelH - handleSize / 2.0F;
                } else if (RECT_RESIZING[hudIndex]) {
                    float deltaX = mouseX - RECT_RESIZE_START_MOUSE_X[hudIndex];
                    float deltaY = mouseY - RECT_RESIZE_START_MOUSE_Y[hudIndex];
                    float delta = (deltaX + deltaY) * 0.5F;
                    float newScale = (
                            RECT_RESIZE_START_WIDTH[hudIndex]
                                    + (delta * 0.9F) / PaperDoll.BASE_WIDTH
                    ) / 2.0F;
                    newScale = snapAndAnnounceHudScale(
                            module, newScale, module.getMinHudScale(), module.getMaxHudScale());
                    module.setHudScale(newScale);
                    scale = module.getRenderHudScale();
                    panelW = PaperDoll.BASE_WIDTH * scale;
                    panelH = PaperDoll.BASE_HEIGHT * scale;
                    maxX = Math.max(0.0F, scaledScreenW - panelW);
                    maxY = Math.max(0.0F, scaledScreenH - panelH);
                    panelX = MathHelper.clamp(module.getHudX(), 0.0F, maxX);
                    panelY = MathHelper.clamp(module.getHudY(), 0.0F, maxY);
                    module.setHudX(panelX);
                    module.setHudY(panelY);
                    hoverX = panelX + hoverInsetX;
                    hoverW = Math.max(12.0F, panelW - hoverInsetX * 2.0F);
                    handleSize = Math.max(4, Math.min(8, Math.round(4.0F * scale)));
                    handleX = hoverX + hoverW - handleSize / 2.0F;
                    handleY = panelY + panelH - handleSize / 2.0F;
                }
            }
        } else if (handleInput) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
        }

        if (handleInput && RECT_DRAGGING[hudIndex]) {
            float dragCenterX = panelX + panelW * 0.5F;
            float dragCenterY = panelY + panelH * 0.5F;
            if (Math.abs(dragCenterX - scaledScreenW * 0.5F) <= GUIDE_SNAP_RADIUS) {
                showVerticalGuideThisFrame = true;
            }
            if (Math.abs(dragCenterY - scaledScreenH * 0.5F) <= GUIDE_SNAP_RADIUS) {
                showHorizontalGuideThisFrame = true;
            }
        }

        if (handleInput) {
            float hoverTarget = chatEditing && hoveredHud ? 1.0F : 0.0F;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], hoverTarget, 10.0F, cachedFrameDeltaSeconds);
            pmWasMouseDown = mouseDown;
        }

        rememberHudSnapBounds(
                hudIndex,
                module,
                panelX * (float) scaleFactor,
                panelY * (float) scaleFactor,
                panelW * (float) scaleFactor,
                panelH * (float) scaleFactor
        );

        if (chatEditing && handleInput) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        PaperDollRenderer.draw(context, module, subject,
                panelX + PaperDoll.FEET_X * scale, panelY + PaperDoll.FEET_Y * scale,
                PaperDoll.BASE_DOLL_SIZE * scale, mc.getRenderTickCounter().getTickDelta(true));

        if (chatEditing && handleInput && RECT_HOVER_PROGRESS[hudIndex] > 0.05F) {
            int outlineColor = withAlpha(0xFFFFFF, (int) (165.0F * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, hoverX, panelY, hoverW, panelH, 1, outlineColor);
        }

        boolean showResizeHandle = chatEditing && handleInput && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
        if (showResizeHandle) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, 0.0F, HANDLE_RENDER_Z);
            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);
            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, withAlpha(0xFFFFFF, 220));
            }
            context.getMatrices().pop();
        }
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
    private static void renderInventoryHud(
            DrawContext context,
            MinecraftClient client,
            InventoryHud module,
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
        if (module == null || !module.isEnabled() || client == null || client.player == null || client.options == null) {
            return;
        }
        if (client.options.hudHidden) {
            return;
        }

        // Snapshot the live inventory at most once per game tick.
        // The 27 storage slots are a server-driven state - they
        // only change when packets land - so re-querying them per
        // frame plus re-evaluating cooldown / damage flags is
        // wasted work at modern HUD rates. Refresh is a no-op when
        // the tick counter hasn't advanced, so calling it every
        // frame is essentially free on the hot path.
        InventoryHud.refreshSnapshotIfStale(client);
        ItemStack[] snapshot = InventoryHud.getSnapshotStacks();
        boolean[] overlayFlags = InventoryHud.getSnapshotOverlayFlags();

        final int hudIndex = HUD_INVENTORY;
        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();

        float baseWidth = PHAZE_INV_BORDER * 2.0F + PHAZE_INV_SLOT * 9.0F;
        float baseHeight = PHAZE_INV_BORDER * 2.0F + PHAZE_INV_SLOT * 3.0F;
        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;
        float maxX = Math.max(0.0F, screenWidth - hudWidth);
        float maxY = Math.max(0.0F, screenHeight - hudHeight);
        int currentScreenWidth = Math.round(screenWidth);
        int currentScreenHeight = Math.round(screenHeight);
        float panelX = module.getHudX();
        float panelY = module.getHudY();
        module.setHudLayoutSize(hudWidth, hudHeight);
        boolean resized = RECT_LAYOUT_INITIALIZED[hudIndex]
                && (RECT_LAST_SCREEN_WIDTH[hudIndex] != currentScreenWidth
                || RECT_LAST_SCREEN_HEIGHT[hudIndex] != currentScreenHeight);

        panelX = MathHelper.clamp(panelX, 0.0F, maxX);
        panelY = MathHelper.clamp(panelY, 0.0F, maxY);
        module.setHudX(panelX);
        module.setHudY(panelY);
        RECT_POSITION_RATIO_X[hudIndex] = maxX <= 0.0F ? 0.0F : panelX / maxX;
        RECT_POSITION_RATIO_Y[hudIndex] = maxY <= 0.0F ? 0.0F : panelY / maxY;
        RECT_LAST_SCREEN_WIDTH[hudIndex] = currentScreenWidth;
        RECT_LAST_SCREEN_HEIGHT[hudIndex] = currentScreenHeight;
        RECT_LAYOUT_INITIALIZED[hudIndex] = true;

        int handleSize = Math.max(4, Math.min(10, Math.round(5.0F * scale)));
        float handleX = panelX + hudWidth - handleSize / 2.0F;
        float handleY = panelY + hudHeight - handleSize / 2.0F;
        boolean hoveredHud = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;
        boolean handleInput = !PHAZE_HUD_PASS.isBatchPass();

        if (!chatEditing && handleInput) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], 0.0F, 10.0F, deltaSeconds);
        } else if (chatEditing && handleInput) {
            hoveredHud = isHovered(mouseX, mouseY, panelX, panelY, hudWidth, hudHeight);
            hoveredHandle = isHovered(mouseX, mouseY, handleX, handleY, handleSize, handleSize);
            nearHud = isNearRect(mouseX, mouseY, panelX, panelY, hudWidth, hudHeight, Math.max(14.0F, 12.0F * scale));

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
                    RECT_DRAG_OFFSET_X[hudIndex] = (float) mouseX - panelX;
                    RECT_DRAG_OFFSET_Y[hudIndex] = (float) mouseY - panelY;
                }
            }

            if (mouseDown) {
                if (RECT_DRAGGING[hudIndex]) {
                    float newX = MathHelper.clamp((float) mouseX - RECT_DRAG_OFFSET_X[hudIndex], 0.0F, maxX);
                    float newY = MathHelper.clamp((float) mouseY - RECT_DRAG_OFFSET_Y[hudIndex], 0.0F, maxY);
                    float centerX = newX + hudWidth * 0.5F;
                    float centerY = newY + hudHeight * 0.5F;
                    boolean snappedToScreenX = false;
                    boolean snappedToScreenY = false;

                    if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                        newX = MathHelper.clamp(screenCenterX - hudWidth * 0.5F, 0.0F, maxX);
                        showVerticalGuideThisFrame = true;
                        snappedToScreenX = true;
                    }
                    if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                        newY = MathHelper.clamp(screenCenterY - hudHeight * 0.5F, 0.0F, maxY);
                        showHorizontalGuideThisFrame = true;
                        snappedToScreenY = true;
                    }
                    if (!snappedToScreenX) {
                        newX = snapHudAlignmentX(hudIndex, newX, newY, hudWidth, hudHeight, maxX, 1.0F);
                    }
                    if (!snappedToScreenY) {
                        newY = snapHudAlignmentY(hudIndex, newX, newY, hudWidth, hudHeight, maxY, 1.0F);
                    }

                    panelX = newX;
                    panelY = newY;
                    module.setHudX(panelX);
                    module.setHudY(panelY);
                    RECT_POSITION_RATIO_X[hudIndex] = maxX <= 0.0F ? 0.0F : panelX / maxX;
                    RECT_POSITION_RATIO_Y[hudIndex] = maxY <= 0.0F ? 0.0F : panelY / maxY;
                    handleX = panelX + hudWidth - handleSize / 2.0F;
                    handleY = panelY + hudHeight - handleSize / 2.0F;
                } else if (RECT_RESIZING[hudIndex]) {
                    float deltaX = (float) mouseX - RECT_RESIZE_START_MOUSE_X[hudIndex];
                    float deltaY = (float) mouseY - RECT_RESIZE_START_MOUSE_Y[hudIndex];
                    float delta = (deltaX + deltaY) * 0.5F;

                    float minWidth = baseWidth * module.getMinHudScale() * 2.0F;
                    float maxWidth = baseWidth * module.getMaxHudScale() * 2.0F;
                    float newWidth = MathHelper.clamp(RECT_RESIZE_START_WIDTH[hudIndex] + delta * 0.9F, minWidth, maxWidth);
                    float newScale = snapAndAnnounceHudScale(
                            module, newWidth / baseWidth / 2.0F,
                            module.getMinHudScale(), module.getMaxHudScale());

                    module.setHudScale(newScale);
                    scale = module.getRenderHudScale();
                    hudWidth = baseWidth * scale;
                    hudHeight = baseHeight * scale;
                    maxX = Math.max(0.0F, screenWidth - hudWidth);
                    maxY = Math.max(0.0F, screenHeight - hudHeight);
                    panelX = MathHelper.clamp(module.getHudX(), 0.0F, maxX);
                    panelY = MathHelper.clamp(module.getHudY(), 0.0F, maxY);
                    module.setHudX(panelX);
                    module.setHudY(panelY);
                    RECT_POSITION_RATIO_X[hudIndex] = maxX <= 0.0F ? 0.0F : panelX / maxX;
                    RECT_POSITION_RATIO_Y[hudIndex] = maxY <= 0.0F ? 0.0F : panelY / maxY;
                    handleSize = Math.max(4, Math.min(10, Math.round(5.0F * scale)));
                    handleX = panelX + hudWidth - handleSize / 2.0F;
                    handleY = panelY + hudHeight - handleSize / 2.0F;
                }
            }

            if (RECT_DRAGGING[hudIndex]) {
                float centerX = panelX + hudWidth * 0.5F;
                float centerY = panelY + hudHeight * 0.5F;
                if (Math.abs(centerX - screenCenterX) <= GUIDE_SNAP_RADIUS) {
                    showVerticalGuideThisFrame = true;
                }
                if (Math.abs(centerY - screenCenterY) <= GUIDE_SNAP_RADIUS) {
                    showHorizontalGuideThisFrame = true;
                }
            }

            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], hoveredHud ? 1.0F : 0.0F, 10.0F, deltaSeconds);
        }

        rememberHudSnapBounds(hudIndex, module, panelX, panelY, hudWidth, hudHeight);

        if (chatEditing && handleInput) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        int hoverOutlineThickness = Math.max(1, Math.round(BASE_HOVER_OUTLINE_THICKNESS / Math.max(1.0F, scale)));

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0F);
        context.getMatrices().push();
        context.getMatrices().translate(panelX, panelY, HUD_RENDER_Z);
        context.getMatrices().scale(scale, scale, 1.0F);

        context.drawGuiTexture(
                RenderLayer::getGuiTextured,
                PHAZE_INV_PANEL_SPRITE,
                0,
                0,
                (int) baseWidth,
                (int) baseHeight,
                0xFFFFFFFF
        );
        boolean drawCounts = module.drawCounts.isValue();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = PHAZE_INV_SLOT_OFFSET + col * PHAZE_INV_SLOT;
                int sy = PHAZE_INV_SLOT_OFFSET + row * PHAZE_INV_SLOT;
                int idx = row * 9 + col;
                ItemStack stack = snapshot[idx];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                context.drawItem(stack, sx, sy);
                if (drawCounts && overlayFlags[idx]) {
                    context.drawStackOverlay(client.textRenderer, stack, sx, sy);
                }
            }
        }
        context.draw();
        context.getMatrices().pop();

        if (chatEditing && handleInput && RECT_HOVER_PROGRESS[hudIndex] > 0.05F) {
            int outlineColor = withAlpha(0xFFFFFF, (int) (175.0F * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, panelX, panelY, hudWidth, hudHeight, hoverOutlineThickness, outlineColor);
        }

        boolean showResizeHandle = chatEditing && handleInput && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
        if (showResizeHandle) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, 0.0F, HANDLE_RENDER_Z);
            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);
            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, withAlpha(0xFFFFFF, 220));
            }
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }
}
