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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.CoordinatesHud;
import vorga.phazeclient.implement.features.modules.hud.CpsHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.features.modules.hud.DirectionHud;
import vorga.phazeclient.implement.features.modules.hud.FastSettingsHud;
import vorga.phazeclient.implement.features.modules.hud.FpsHud;
import vorga.phazeclient.implement.features.modules.hud.KeystrokesHud;
import vorga.phazeclient.implement.features.modules.hud.PingHud;
import vorga.phazeclient.implement.features.modules.hud.PotionHud;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.StatsHud;
import vorga.phazeclient.implement.features.modules.hud.SprintHud;
import vorga.phazeclient.implement.features.modules.hud.TabHud;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    private static final int BASE_WIDTH = 64;
    private static final int BASE_HEIGHT = 20;
    private static final int HUD_TEXT_COLOR = 0xFFFFFFFF;
    private static final float RECT_HUD_MSDF_SIZE = 8.13f;
    private static final float ARMOR_HUD_MSDF_SIZE = 8.0f;
    private static final float HUD_MSDF_SIZE_MATCH_MULTIPLIER = 1.28f;
    private static final float MSDF_SHADOW_OFFSET = 1.0f;
    private static final int MSDF_SHADOW_COLOR = 0xFF3F3E3E;
    private static final float HUD_MSDF_THICKNESS = 0.021f;
    private static final float HUD_MSDF_SMOOTHNESS = 0.01f;
    private static final float HUD_MSDF_GLOBAL_X_OFFSET = -0.5f;
    private static final float HUD_MSDF_GLOBAL_Y_OFFSET = -0.5f;
    private static final float ARMOR_HUD_MSDF_EXTRA_Y_OFFSET = -2.0f;
    private static final float HUD_TEXT_RENDER_Z = 1000.0f;
    private static final float RECT_HUD_MSDF_Y_OFFSET = 1.20f;
    private static final float ARMOR_HUD_MSDF_Y_OFFSET = 0.20f;
    private static final float ARMOR_TEXT_VERTICAL_NUDGE = 1.5f;
    private static final int HANDLE_COLOR = 0xFF72F7D4;
    private static final int BASE_HOVER_OUTLINE_THICKNESS = 5;
    private static final float HUD_RENDER_Z = 400.0f;
    private static final float HANDLE_RENDER_Z = 450.0f;
    private static final float GUIDE_SNAP_RADIUS = 3.0f;
    private static final float GUIDE_FADE_SPEED = 14.0f;
    private static final int GUIDE_MAX_ALPHA = 140;
    private static final long HUD_TEXT_THROTTLE_MS = 50L;

    private static final int HUD_FPS = 0;
    private static final int HUD_CPS = 1;
    private static final int HUD_REACH = 2;
    private static final int HUD_SPRINT = 3;
    private static final int HUD_COORDINATES = 4;
    private static final int HUD_PING = 5;
    private static final int HUD_KEYSTROKES = 6;
    private static final int HUD_POTION = 7;
    private static final int HUD_DAY_COUNTER = 8;
    private static final int HUD_STATS = 9;
    private static final int HUD_DIRECTION = 10;
    private static final int HUD_TAB = 11;
    private static final int HUD_NAMETAG = 12;
    private static final int HUD_TIME = 13;
    private static final int HUD_SESSION = 14;
    private static final int RECT_HUD_COUNT = 15;
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
    private static boolean coordinatesCacheInitialized = false;
    private static List<ItemStack> armorStacksCache = new ArrayList<>();
    private static List<String> armorDurabilityTextsCache = new ArrayList<>();
    private static boolean armorCacheInitialized = false;
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
    private static boolean renderedThisFrame = false;
    private static boolean wasMouseDown = false;
    private static boolean wasLeftMouseDown = false;
    private static long lastFrameNanos = -1L;
    private static float verticalGuideProgress = 0.0f;
    private static float horizontalGuideProgress = 0.0f;
    private static boolean showVerticalGuideThisFrame = false;
    private static boolean showHorizontalGuideThisFrame = false;
    private static final String[] HUD_PART_NAMES = {"FPS", "CPS", "Reach", "Armor", "Sprint", "Coords", "Ping", "Keys", "Potion", "Days"};
    private static final double[] HUD_PART_AVG_NS = new double[HUD_PART_NAMES.length];
    private static final int[] HUD_PART_ORDER = new int[HUD_PART_NAMES.length];
    private static float directionDisplayYaw = Float.NaN;
    private static final long SESSION_START_MS = System.currentTimeMillis();


    @Inject(method = "render", at = @At("HEAD"))
    private void beginHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        renderedThisFrame = false;
        Blur.INSTANCE.beginCachedFrame();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("TAIL"))
    private void renderCustomHudAfterScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        renderHudInternal(context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCustomHudFallback(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!renderedThisFrame) {
            renderHudInternal(context);
        }
        Blur.INSTANCE.endCachedFrame();
    }

    private void renderHudInternal(DrawContext context) {
        renderedThisFrame = true;
        FastSettingsHud.getInstance().applyToAllHudModulesIfDirty();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null || client.options.hudHidden) {
            return;
        }

        if (!hasAnyHudEnabled()) {
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos < 0L) {
            lastFrameNanos = now;
        }
        float deltaSeconds = MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000.0f, 0.0f, 0.10f);
        lastFrameNanos = now;

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
        boolean gameplayInput = client.currentScreen == null && client.player != null && client.world != null;
        updateClicksPerSecond(mouseDown, gameplayInput);
        showVerticalGuideThisFrame = false;
        showHorizontalGuideThisFrame = false;

        float statsSmoothing = StatsHud.getInstance().sampleSmoothing.getValue();
        String fpsText = getCachedHudText(FpsHud.getInstance(), HUD_FPS, chatEditing, () -> "FPS: " + client.getCurrentFps());
        timeHudPart(0, statsSmoothing, () -> renderBufferedHud(context, FpsHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, FpsHud.getInstance(), fpsText, HUD_FPS,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(FpsHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        String cpsText = getCachedHudText(CpsHud.getInstance(), HUD_CPS, chatEditing, () -> "CPS: " + LEFT_CLICKS.size());
        timeHudPart(1, statsSmoothing, () -> renderBufferedHud(context, CpsHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, CpsHud.getInstance(), cpsText, HUD_CPS,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(CpsHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        String reachText = getCachedHudText(ReachHud.getInstance(), HUD_REACH, chatEditing, () -> ReachHud.getInstance().getFormattedReach());
        timeHudPart(2, statsSmoothing, () -> renderBufferedHud(context, ReachHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, ReachHud.getInstance(), reachText, HUD_REACH,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(ReachHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        timeHudPart(3, statsSmoothing, () -> renderBufferedHud(context, ArmorHud.getInstance(), chatEditing, () ->
                renderArmorHud(context, client, ArmorHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(ArmorHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        String sprintText = getCachedHudText(SprintHud.getInstance(), HUD_SPRINT, chatEditing, () -> getSprintHudText(client));
        timeHudPart(4, statsSmoothing, () -> renderBufferedHud(context, SprintHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, SprintHud.getInstance(), sprintText, HUD_SPRINT,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(SprintHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                        getTextHudBaseWidth(client, sprintText), BASE_HEIGHT)));
        timeHudPart(5, statsSmoothing, () -> renderBufferedHud(context, CoordinatesHud.getInstance(), chatEditing, () ->
                renderCoordinatesHud(context, client, CoordinatesHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(CoordinatesHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        timeHudPart(6, statsSmoothing, () -> renderBufferedHud(context, PingHud.getInstance(), chatEditing, () ->
                renderPingHud(context, client, PingHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(PingHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        timeHudPart(7, statsSmoothing, () -> renderBufferedHud(context, KeystrokesHud.getInstance(), chatEditing, () ->
                renderKeystrokesHud(context, client, KeystrokesHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(KeystrokesHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        timeHudPart(8, statsSmoothing, () -> renderBufferedHud(context, PotionHud.getInstance(), chatEditing, () ->
                renderPotionHud(context, client, PotionHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown, getHudDelta(PotionHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY)));
        String dayText = getCachedHudText(DayCounterHud.getInstance(), HUD_DAY_COUNTER, chatEditing, () -> getDayCounterText(client));

        timeHudPart(9, statsSmoothing, () -> renderBufferedHud(context, DayCounterHud.getInstance(), chatEditing, () ->
                renderRectHud(context, client, DayCounterHud.getInstance(), dayText, HUD_DAY_COUNTER,
                        chatEditing, mouseX, mouseY, mouseDown, getHudDelta(DayCounterHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                        getTextHudBaseWidth(client, dayText), BASE_HEIGHT)));
        renderBufferedHud(context, StatsHud.getInstance(), chatEditing, () ->
                renderStatsHud(context, client, StatsHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(StatsHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        renderBufferedHud(context, DirectionHud.getInstance(), chatEditing, () ->
                renderDirectionHud(context, client, DirectionHud.getInstance(), chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(DirectionHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        // TabHud/NametagHud now affect vanilla TAB list and world nametags directly via dedicated mixins.
        String timeText = getTimeHudText(TimeHud.getInstance());
        renderBufferedHud(context, TimeHud.getInstance(), chatEditing, () ->
                renderSimpleTextHud(context, client, TimeHud.getInstance(), timeText, HUD_TIME, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(TimeHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));
        String sessionText = getSessionText(SessionTimeHud.getInstance());
        renderBufferedHud(context, SessionTimeHud.getInstance(), chatEditing, () ->
                renderSimpleTextHud(context, client, SessionTimeHud.getInstance(), sessionText, HUD_SESSION, chatEditing, mouseX, mouseY, mouseDown,
                        getHudDelta(SessionTimeHud.getInstance(), chatEditing, deltaSeconds), inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY));

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

    private void renderBufferedHud(DrawContext context, RectHudModule module, boolean chatEditing, Runnable renderLogic) {
        renderBufferedHudInternal(context, module.isEnabled(), module.hudBatching.isValue() && isFboBatchSafe(module), module.getHudBuffer(), module.hudFps.getInt(), chatEditing, renderLogic);
    }

    private void renderBufferedHud(DrawContext context, ArmorHud module, boolean chatEditing, Runnable renderLogic) {
        renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), module.hudFps.getInt(), chatEditing, renderLogic);
    }

    private boolean isFboBatchSafe(RectHudModule module) {
        if (module.background.isValue() && module.backgroundBlurRadius.getValue() > 0.0f) {
            return false;
        }
        return module == FpsHud.getInstance()
                || module == CpsHud.getInstance()
                || module == ReachHud.getInstance()
                || module == SprintHud.getInstance()
                || module == PingHud.getInstance()
                || module == DayCounterHud.getInstance();
    }

    private void renderBufferedHudInternal(DrawContext context, boolean enabled, boolean batching, HudBuffer buffer, int targetFps, boolean chatEditing, Runnable renderLogic) {
        if (!enabled) {
            buffer.invalidate();
            return;
        }

        renderLogic.run();
    }

    private float getHudDelta(RectHudModule module, boolean chatEditing, float deltaSeconds) {
        if (chatEditing || !module.hudBatching.isValue() || module.forceHudUpdate.isValue()) {
            return deltaSeconds;
        }
        return module.getHudBuffer().getThrottledDelta(module.hudFps.getInt(), deltaSeconds);
    }

    private float getHudDelta(ArmorHud module, boolean chatEditing, float deltaSeconds) {
        if (chatEditing || !module.hudBatching.isValue() || module.forceHudUpdate.isValue()) {
            return deltaSeconds;
        }
        return module.getHudBuffer().getThrottledDelta(module.hudFps.getInt(), deltaSeconds);
    }

    private boolean shouldUpdateHudData(RectHudModule module, boolean chatEditing) {
        return chatEditing || !module.hudBatching.isValue() || module.forceHudUpdate.isValue() || module.getHudBuffer().shouldUpdateData(module.hudFps.getInt());
    }

    private boolean shouldUpdateHudData(ArmorHud module, boolean chatEditing) {
        return chatEditing || !module.hudBatching.isValue() || module.forceHudUpdate.isValue() || module.getHudBuffer().shouldUpdateData(module.hudFps.getInt());
    }

    private String getCachedHudText(RectHudModule module, int hudIndex, boolean chatEditing, Supplier<String> supplier) {
        long now = System.currentTimeMillis();
        boolean throttledDue = now - HUD_TEXT_CACHE_TIME_MS[hudIndex] >= HUD_TEXT_THROTTLE_MS;
        if (HUD_TEXT_CACHE[hudIndex] == null || shouldUpdateHudData(module, chatEditing) || throttledDue) {
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

        float textWidth = getHudTextWidth(client, text, RECT_HUD_MSDF_SIZE);
        float textX = (baseWidth - textWidth) / 2.0f;
        float textY = Theme.getInstance().useMsdfHudFont()
                ? (baseHeight - RECT_HUD_MSDF_SIZE) / 2.0f + RECT_HUD_MSDF_Y_OFFSET
                : (baseHeight - 8.0f) / 2.0f;
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

        renderScaledHudText(context, client, text, x, y, textX, textY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());

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

        if (!armorCacheInitialized || shouldUpdateHudData(module, chatEditing)) {
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
            armorCacheInitialized = true;
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
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, text, ARMOR_HUD_MSDF_SIZE));
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
            float textWidth = getHudTextWidth(client, durabilityText, ARMOR_HUD_MSDF_SIZE);

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
            float rowY = i * rowHeight;
            float textWidth = getHudTextWidth(client, durabilityText, ARMOR_HUD_MSDF_SIZE);

            float textX;
            if (textOnLeft) {
                textX = numberSidePadding + (maxTextWidth - textWidth);
            } else {
                textX = iconSize + textGap;
            }

            float textY = Theme.getInstance().useMsdfHudFont()
                    ? rowY + (iconSize - ARMOR_HUD_MSDF_SIZE) / 2.0f + ARMOR_HUD_MSDF_Y_OFFSET + 3.0f + ARMOR_TEXT_VERTICAL_NUDGE + ARMOR_HUD_MSDF_EXTRA_Y_OFFSET
                    : rowY + 4.0f + ARMOR_TEXT_VERTICAL_NUDGE;

            renderScaledHudText(context, client, durabilityText, x, y, textX, textY, ARMOR_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
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

        if (!coordinatesCacheInitialized || shouldUpdateHudData(module, chatEditing)) {
            List<String> updatedLines = new ArrayList<>();
            BlockPos pos = client.player.getBlockPos();
            if (module.showX.isValue()) {
                updatedLines.add("X: " + pos.getX());
            }
            if (module.showY.isValue()) {
                updatedLines.add("Y: " + pos.getY());
            }
            if (module.showZ.isValue()) {
                updatedLines.add("Z: " + pos.getZ());
            }
            if (module.showChunk.isValue()) {
                updatedLines.add("C: " + ChunkSectionPos.getLocalCoord(pos.getX()) + "/" + ChunkSectionPos.getLocalCoord(pos.getZ()));
            }
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
            coordinatesCacheInitialized = true;
        }

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
            maxLineWidth = Math.max(maxLineWidth, getHudTextWidth(client, line, RECT_HUD_MSDF_SIZE));
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

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float textY = paddingY + i * lineHeight;
            if (module.showBiome.isValue() && line.startsWith("Biome: ")) {
                String label = "Biome: ";
                renderScaledHudTextColored(context, client, label, x, y, paddingX, textY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue(), 0xFFFFFF);
                float labelWidth = getHudTextWidth(client, label, RECT_HUD_MSDF_SIZE);
                renderScaledHudTextColored(context, client, biomeName, x, y, paddingX + labelWidth, textY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue(), coordinatesBiomeColorCache);
            } else {
                renderScaledHudText(context, client, line, x, y, paddingX, textY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
            }
        }

        if (showDirection) {
            String facing = coordinatesFacingCache;
            float facingWidth = getHudTextWidth(client, facing, RECT_HUD_MSDF_SIZE);
            float facingCenterX = baseWidth - directionColumn * 0.5f;
            float facingX = facingCenterX - facingWidth * 0.5f;
            float facingY = baseHeight * 0.5f - 4.0f;
            if (module.showAxisSigns.isValue()) {
                String topSign = coordinatesTopSignCache;
                String bottomSign = coordinatesBottomSignCache;
                if (!topSign.isEmpty()) {
                    float signWidth = getHudTextWidth(client, topSign, RECT_HUD_MSDF_SIZE);
                    renderScaledHudText(context, client, topSign, x, y, facingCenterX - signWidth * 0.5f, facingY - 10.0f, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
                }
                renderScaledHudText(context, client, facing, x, y, facingX, facingY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
                if (!bottomSign.isEmpty()) {
                    float signWidth = getHudTextWidth(client, bottomSign, RECT_HUD_MSDF_SIZE);
                    renderScaledHudText(context, client, bottomSign, x, y, facingCenterX - signWidth * 0.5f, facingY + 10.0f, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
                }
            } else {
                renderScaledHudText(context, client, facing, x, y, facingX, facingY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
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
        String label = "Ping: ";
        String value = getCachedHudText(module, HUD_PING, chatEditing, () -> {
            if (local) {
                module.resetPingCache();
                return "... ms";
            }
            int rawPing = getServerPing(client);
            module.updatePing(rawPing);
            int displayPing = module.getCachedPing();
            return displayPing > 0 ? displayPing + " ms" : "... ms";
        });
        float baseWidth = Math.max(48.0f, getHudTextWidth(client, label + value, RECT_HUD_MSDF_SIZE) + 12.0f);

        renderRectHud(context, client, module, "", HUD_PING, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, BASE_HEIGHT);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        float totalWidth = getHudTextWidth(client, label + value, RECT_HUD_MSDF_SIZE);
        float textX = (baseWidth - totalWidth) * 0.5f;
        float textY = Theme.getInstance().useMsdfHudFont()
                ? (BASE_HEIGHT - RECT_HUD_MSDF_SIZE) / 2.0f + RECT_HUD_MSDF_Y_OFFSET
                : (BASE_HEIGHT - 8.0f) / 2.0f;

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        renderScaledHudText(context, client, label, x, y, textX, textY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
        float labelWidth = getHudTextWidth(client, label, RECT_HUD_MSDF_SIZE);
        renderScaledHudTextColored(context, client, value, x, y, textX + labelWidth, textY, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue(), local ? 0xFFFF55 : 0xFFFFFF);
        context.getMatrices().pop();
    }

    private void renderStatsHud(
            DrawContext context,
            MinecraftClient client,
            StatsHud module,
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

        int top = MathHelper.clamp(module.topCount.getInt(), 1, HUD_PART_NAMES.length);
        for (int i = 0; i < HUD_PART_ORDER.length; i++) {
            HUD_PART_ORDER[i] = i;
        }
        for (int i = 0; i < HUD_PART_ORDER.length - 1; i++) {
            for (int j = i + 1; j < HUD_PART_ORDER.length; j++) {
                if (HUD_PART_AVG_NS[HUD_PART_ORDER[j]] > HUD_PART_AVG_NS[HUD_PART_ORDER[i]]) {
                    int t = HUD_PART_ORDER[i];
                    HUD_PART_ORDER[i] = HUD_PART_ORDER[j];
                    HUD_PART_ORDER[j] = t;
                }
            }
        }

        double totalNs = 0.0;
        for (double v : HUD_PART_AVG_NS) {
            totalNs += v;
        }
        totalNs = Math.max(totalNs, 1.0);

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < top; i++) {
            int idx = HUD_PART_ORDER[i];
            double ms = HUD_PART_AVG_NS[idx] / 1_000_000.0;
            double pct = (HUD_PART_AVG_NS[idx] / totalNs) * 100.0;
            StringBuilder sb = new StringBuilder(HUD_PART_NAMES[idx]).append(": ");
            if (module.showMs.isValue()) {
                sb.append(String.format(Locale.US, "%.2fms", ms));
            }
            if (module.showPercent.isValue()) {
                if (module.showMs.isValue()) sb.append(" ");
                sb.append(String.format(Locale.US, "(%.0f%%)", pct));
            }
            lines.add(sb.toString());
        }

        float paddingX = 5.0f;
        float paddingY = 4.0f;
        float lineHeight = 10.0f;
        float maxLineWidth = 0.0f;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, getHudTextWidth(client, line, RECT_HUD_MSDF_SIZE));
        }
        float baseWidth = Math.max(90.0f, paddingX * 2.0f + maxLineWidth);
        float baseHeight = Math.max(BASE_HEIGHT, paddingY * 2.0f + lines.size() * lineHeight);

        renderRectHud(context, client, module, "", HUD_STATS, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getHudScale();
        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        for (int i = 0; i < lines.size(); i++) {
            renderScaledHudText(context, client, lines.get(i), x, y, paddingX, paddingY + i * lineHeight, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
        }
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
        float baseWidth = Math.max(48.0f, getHudTextWidth(client, text, RECT_HUD_MSDF_SIZE) + 14.0f);
        renderRectHud(context, client, module, text, hudIndex, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, BASE_HEIGHT);
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
        float baseHeight = 26.0f;
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
        float degreesPerPixel = 90.0f / Math.max(80.0f, baseWidth);

        context.getMatrices().push();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale, 1.0f);
        for (int deg = 0; deg < 360; deg += 15) {
            float delta = shortestAngleDeg(deg, directionDisplayYaw);
            float px = centerX + (delta / degreesPerPixel);
            if (px < 4.0f || px > baseWidth - 4.0f) continue;

            int alpha = MathHelper.clamp(Math.round(255.0f * directionEdgeFade(px, baseWidth)), 0, 255);
            if (alpha <= 2) {
                continue;
            }

            String label = directionLabel(deg, module.showIntermediate.isValue());
            if (label.isEmpty()) {
                context.fill(Math.round(x + px), Math.round(y + 7.0f), Math.round(x + px + 1.0f), Math.round(y + 12.0f), withAlpha(0xFFFFFF, Math.round(153.0f * alpha / 255.0f)));
            } else {
                float tw = getHudTextWidth(client, label, RECT_HUD_MSDF_SIZE);
                renderScaledHudTextWithAlpha(context, client, label, x, y, px - tw * 0.5f, 10.0f, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue(), alpha);
                context.fill(Math.round(x + px), Math.round(y + 5.0f), Math.round(x + px + 1.0f), Math.round(y + 9.0f), withAlpha(0xFFFFFF, Math.round(204.0f * alpha / 255.0f)));
            }
        }
        if (module.showDegreeNumber.isValue()) {
            String degree = String.valueOf(Math.round(directionDisplayYaw));
            float tw = getHudTextWidth(client, degree, RECT_HUD_MSDF_SIZE);
            renderScaledHudText(context, client, degree, x, y, centerX - tw * 0.5f, 1.5f, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
        }
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
        renderKeyButton(context, 18, 0, 17, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_W]);
        renderKeyButton(context, 0, 19, 17, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_A]);
        renderKeyButton(context, 18, 19, 17, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_S]);
        renderKeyButton(context, 36, 19, 17, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_D]);
        renderKeyButton(context, 0, 38, 54, 8, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE]);
        renderKeyButton(context, 0, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_LMB]);
        renderKeyButton(context, 27, 47, 27, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_RMB]);
        context.draw();
        context.getMatrices().pop();

        renderKeyLabel(context, client, getKeyLabel(client.options.forwardKey.getBoundKeyLocalizedText()), x, y, 18, 4, 17, KEYSTROKE_PROGRESS[KEYSTROKE_W], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, getKeyLabel(client.options.leftKey.getBoundKeyLocalizedText()), x, y, 0, 23, 17, KEYSTROKE_PROGRESS[KEYSTROKE_A], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, getKeyLabel(client.options.backKey.getBoundKeyLocalizedText()), x, y, 18, 23, 17, KEYSTROKE_PROGRESS[KEYSTROKE_S], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, getKeyLabel(client.options.rightKey.getBoundKeyLocalizedText()), x, y, 36, 23, 17, KEYSTROKE_PROGRESS[KEYSTROKE_D], scale, module.textShadow.isValue());
        renderSpacebarLabel(context, x, y, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE], scale);
        renderKeyLabel(context, client, "LMB", x, y, 0, 50, 26, KEYSTROKE_PROGRESS[KEYSTROKE_LMB], scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "RMB", x, y, 27, 50, 27, KEYSTROKE_PROGRESS[KEYSTROKE_RMB], scale, module.textShadow.isValue());
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

        if (!potionCacheInitialized || shouldUpdateHudData(module, chatEditing) || (potionSampleCache && !chatEditing)) {
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
            if (!chatEditing) {
                return;
            }
        }

        float rowHeight = 24.0f;
        float iconSize = 18.0f;
        float paddingX = 6.0f;
        float paddingY = 4.0f;
        float textX = paddingX + iconSize + 5.0f;
        int rows = sample ? 1 : effects.size();
        float maxTextWidth = 0.0f;
        for (int i = 0; i < rows; i++) {
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, potionNamesCache.get(i), RECT_HUD_MSDF_SIZE));
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, potionDurationsCache.get(i), RECT_HUD_MSDF_SIZE));
        }

        float baseWidth = Math.max(94.0f, textX + maxTextWidth + paddingX);
        float baseHeight = paddingY * 2.0f + rows * rowHeight;
        renderRectHud(context, client, module, "", HUD_POTION, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

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
            renderScaledHudText(context, client, name, x, y, textX, rowY + 2.0f, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
            renderScaledHudText(context, client, duration, x, y, textX, rowY + 12.0f, RECT_HUD_MSDF_SIZE, scale, module.textShadow.isValue());
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
        return Math.max(44.0f, getHudTextWidth(client, text, RECT_HUD_MSDF_SIZE) + 14.0f);
    }

    private static String getSprintHudText(MinecraftClient client) {
        if (client.player == null || client.options == null) {
            return "[Not Sprinting]";
        }
        if (client.player.getAbilities().flying && client.options.sneakKey.isPressed()) {
            return "[Flying Descending]";
        }
        if (client.player.getAbilities().flying) {
            return "[Flying]";
        }
        if (client.options.sneakKey.isPressed() || client.player.isSneaking()) {
            return "[Sneaking (Key Held)]";
        }
        if (client.options.sprintKey.isPressed()) {
            return "[Sprinting (Key Held)]";
        }
        if (client.player.isSprinting()) {
            AutoSprint autoSprint = AutoSprint.getInstance();
            if (autoSprint.isEnabled() && autoSprint.showInSprintHud.isValue()) {
                return "[Sprinting (AutoSprint)]";
            }
            return "[Sprinting (Vanilla)]";
        }
        return "[Not Sprinting]";
    }

    private static String getDayCounterText(MinecraftClient client) {
        if (client.world == null) {
            return "0 Days";
        }
        long days = Math.max(0L, client.world.getTime() / 24000L) + 1;
        return days + (days == 1L ? " Day" : " Days");
    }

    private static String getTimeHudText(TimeHud module) {
        LocalTime now = LocalTime.now();
        if (module.hour24.isValue()) {
            return now.format(DateTimeFormatter.ofPattern("H:mm"));
        }
        if (module.showAmPm.isValue()) {
            return now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
        }
        return now.format(DateTimeFormatter.ofPattern("h:mm", Locale.US));
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
        Blur.INSTANCE.renderCached(createKeystrokeBlurRect(context, 0.0f, 0.0f, 54.0f, 63.0f, blurQuality));
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
        float textWidth = getHudTextWidth(client, label, RECT_HUD_MSDF_SIZE);
        int color = progress > 0.45f ? 0x111111 : 0xFFFFFF;
        renderScaledHudTextColored(context, client, label, hudX, hudY, keyX + (keyWidth - textWidth) * 0.5f, keyY, RECT_HUD_MSDF_SIZE, scale, shadow, color);
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
            return "**:**";
        }
        int totalSeconds = Math.max(0, effect.getDuration() / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static float getHudTextWidth(MinecraftClient client, String text, float msdfSize) {
        boolean msdf = Theme.getInstance().useMsdfHudFont();
        String cacheKey = (msdf ? "M|" : "V|") + msdfSize + "|" + text;
        Float cached = HUD_TEXT_WIDTH_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        float width = msdf
                ? MsdfFonts.hud().getWidth(text, getMatchedMsdfSize(msdfSize))
                : client.textRenderer.getWidth(text);
        HUD_TEXT_WIDTH_CACHE.put(cacheKey, width);
        return width;
    }

    private static void renderHudTextWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float msdfSize, boolean shadow, int alpha) {
        renderHudTextColoredWithAlpha(context, client, text, x, y, msdfSize, shadow, 0x00FFFFFF, alpha);
    }

    private static void renderHudTextColoredWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float msdfSize, boolean shadow, int rgbColor, int alpha) {
        int textColor = (MathHelper.clamp(alpha, 0, 255) << 24) | (rgbColor & 0x00FFFFFF);
        if (Theme.getInstance().useMsdfHudFont()) {
            float matchedMsdfSize = getMatchedMsdfSize(msdfSize);
            float msdfSmoothness = getHudMsdfSmoothness(msdfSize);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            if (shadow) {
                float shadowThickness = HUD_MSDF_THICKNESS + 0.0025f;
                float shadowSmoothness = Math.max(0.008f, msdfSmoothness * 0.80f);
                int shadowColor = withAlpha(MSDF_SHADOW_COLOR, MathHelper.clamp(alpha, 0, 255));
                MsdfRenderer.renderText(
                        MsdfFonts.hud(),
                        text,
                        matchedMsdfSize,
                        shadowColor,
                        context.getMatrices().peek().getPositionMatrix(),
                        x + HUD_MSDF_GLOBAL_X_OFFSET + MSDF_SHADOW_OFFSET,
                        y + HUD_MSDF_GLOBAL_Y_OFFSET + MSDF_SHADOW_OFFSET,
                        HUD_TEXT_RENDER_Z,
                        shadowThickness,
                        shadowSmoothness
                );
            }
            MsdfRenderer.renderText(
                MsdfFonts.hud(),
                text,
                matchedMsdfSize,
                textColor,
                context.getMatrices().peek().getPositionMatrix(),
                x + HUD_MSDF_GLOBAL_X_OFFSET,
                y + HUD_MSDF_GLOBAL_Y_OFFSET,
                HUD_TEXT_RENDER_Z,
                HUD_MSDF_THICKNESS,
                msdfSmoothness
            );
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            return;
        }

        context.drawText(client.textRenderer, text, Math.round(x), Math.round(y), textColor, shadow);
    }

    private static float getMatchedMsdfSize(float baseMsdfSize) {
        return baseMsdfSize * HUD_MSDF_SIZE_MATCH_MULTIPLIER;
    }

    private static float getHudMsdfSmoothness(float msdfSize) {
        float smallTextBoost = MathHelper.clamp((RECT_HUD_MSDF_SIZE - msdfSize) / RECT_HUD_MSDF_SIZE, 0.0f, 1.0f);
        return HUD_MSDF_SMOOTHNESS + smallTextBoost * 0.018f;
    }

    private static float getOptimizedHudBlurQuality(float normalizedBlurRadius) {
        float radius = Math.max(0.0f, normalizedBlurRadius);
        if (radius < 0.10f) {
            return 0.0f;
        }

        // Ease-in curve: lower blur strength on small values, near old behavior on high values.
        float lowRadiusAttenuation = 0.55f + 0.45f * MathHelper.clamp(radius / 18.0f, 0.0f, 1.0f);
        float baseQuality = radius * 4.0f * lowRadiusAttenuation;
        if (radius <= 20.0f) {
            return baseQuality * Theme.getInstance().getHudBlurQualityMultiplier();
        }
        // Soft curve after 20+: tiny quality reduction at first, stronger only at very high radius.
        float over = radius - 20.0f;
        float t = MathHelper.clamp(over / 16.0f, 0.0f, 1.0f);
        float factor = 1.0f - (0.14f * t * t);
        return baseQuality * factor * Theme.getInstance().getHudBlurQualityMultiplier();
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
            float msdfSize,
            float scale,
            boolean shadow
    ) {
        context.getMatrices().push();
        if (Theme.getInstance().useMsdfHudFont()) {
            renderHudText(context, client, text, hudX + textX * scale, hudY + textY * scale, msdfSize * scale, shadow);
        } else {
            context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
            context.getMatrices().scale(scale, scale, 1.0f);
            renderHudText(context, client, text, textX, textY, msdfSize, shadow);
        }
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
            float msdfSize,
            float scale,
            boolean shadow,
            int alpha
    ) {
        context.getMatrices().push();
        if (Theme.getInstance().useMsdfHudFont()) {
            renderHudTextWithAlpha(context, client, text, hudX + textX * scale, hudY + textY * scale, msdfSize * scale, shadow, alpha);
        } else {
            context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
            context.getMatrices().scale(scale, scale, 1.0f);
            renderHudTextWithAlpha(context, client, text, textX, textY, msdfSize, shadow, alpha);
        }
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
            float msdfSize,
            float scale,
            boolean shadow,
            int rgbColor
    ) {
        context.getMatrices().push();
        if (Theme.getInstance().useMsdfHudFont()) {
            renderHudTextColored(context, client, text, hudX + textX * scale, hudY + textY * scale, msdfSize * scale, shadow, rgbColor);
        } else {
            context.getMatrices().translate(hudX, hudY, HUD_RENDER_Z + 25.0f);
            context.getMatrices().scale(scale, scale, 1.0f);
            renderHudTextColored(context, client, text, textX, textY, msdfSize, shadow, rgbColor);
        }
        context.getMatrices().pop();
    }

    private static void updateClicksPerSecond(boolean mouseDown, boolean gameplayInput) {
        if (!gameplayInput) {
            LEFT_CLICKS.clear();
            wasLeftMouseDown = false;
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (mouseDown && !wasLeftMouseDown) {
            LEFT_CLICKS.addLast(nowMs);
        }
        wasLeftMouseDown = mouseDown;

        while (!LEFT_CLICKS.isEmpty() && nowMs - LEFT_CLICKS.peekFirst() > 1000L) {
            LEFT_CLICKS.removeFirst();
        }
    }

    private static void timeHudPart(int index, float smoothingSetting, Runnable task) {
        long start = System.nanoTime();
        task.run();
        long elapsed = System.nanoTime() - start;
        double keep = MathHelper.clamp(smoothingSetting / 100.0f, 0.05f, 0.95f);
        HUD_PART_AVG_NS[index] = HUD_PART_AVG_NS[index] * keep + elapsed * (1.0 - keep);
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
                || StatsHud.getInstance().isEnabled()
                || DirectionHud.getInstance().isEnabled()
                || TabHud.getInstance().isEnabled()
                || NametagHud.getInstance().isEnabled()
                || TimeHud.getInstance().isEnabled()
                || SessionTimeHud.getInstance().isEnabled();
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
                || RECT_DRAGGING[HUD_STATS] || RECT_RESIZING[HUD_STATS]
                || RECT_DRAGGING[HUD_DIRECTION] || RECT_RESIZING[HUD_DIRECTION]
                || RECT_DRAGGING[HUD_TAB] || RECT_RESIZING[HUD_TAB]
                || RECT_DRAGGING[HUD_NAMETAG] || RECT_RESIZING[HUD_NAMETAG]
                || RECT_DRAGGING[HUD_TIME] || RECT_RESIZING[HUD_TIME]
                || RECT_DRAGGING[HUD_SESSION] || RECT_RESIZING[HUD_SESSION]
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
}
