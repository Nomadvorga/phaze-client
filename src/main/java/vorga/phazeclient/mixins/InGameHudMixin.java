package vorga.phazeclient.mixins;

import net.minecraft.client.gl.RenderPipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.joml.Matrix3x2f;
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
import vorga.phazeclient.api.system.cursor.HudCursorRelay;
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
import vorga.phazeclient.implement.features.modules.hud.ScoreboardSidebarEntry;
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
import vorga.phazeclient.api.system.hud.HudScaleLimits;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.shape.implement.Rectangle;
import vorga.phazeclient.base.util.render.Render2DUtil;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.joml.Vector4i;
import vorga.phazeclient.api.system.hud.ChatAnimationFrameAccess;
import vorga.phazeclient.api.system.hud.ExordiumAnimationBridge;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.settings.ScaleSnapOverlay;
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
import vorga.phazeclient.implement.features.modules.other.TrapTimer;
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
import java.util.function.Supplier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    private static final float BASE_WIDTH = 60.0f;
    private static final float BASE_HEIGHT = 20.0f;
    private static final int HUD_TEXT_COLOR = 0xFFFFFFFF;
    private static final float HUD_TEXT_SIZE = 8.0f;

    @SuppressWarnings("unused")
    private static final float HUD_TEXT_RENDER_Z = 1000.0f;
    private static final int HANDLE_COLOR = 0xFF72F7D4;
    private static final float BASE_HOVER_OUTLINE_THICKNESS = 1.0f;
    @SuppressWarnings("unused")
    private static final float HUD_RENDER_Z = 400.0f;
    @SuppressWarnings("unused")
    private static final float HANDLE_RENDER_Z = 450.0f;
    private static final float GUIDE_SNAP_RADIUS = 3.0f;
    private static final float HUD_TO_HUD_SNAP_RADIUS = 4.0f;
    private static final float GUIDE_FADE_SPEED = 14.0f;
    private static final int GUIDE_MAX_ALPHA = 140;
    private static final long HUD_TEXT_THROTTLE_MS = 50L;
    private static final Identifier DIRECTION_TRIANGLE_TEXTURE = Identifier.of("phaze", "textures/down_triangle.png");

    private static final EquipmentSlot[] PHAZE_ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

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
    private static final int HUD_PLAYER_MODEL = 24;
    private static final int HUD_TRAP_TIMER = 25;
    private static final int HUD_INVENTORY = 26;
    private static final int RECT_HUD_COUNT = 27;
    private static final int HUD_ARMOR_BLUR_SLOT = 27;

    private static float phaze$currentHudTextYOffset = 0.0F;
    private static final int HUD_SNAP_ARMOR = RECT_HUD_COUNT;
    private static final int HUD_SNAP_COUNT = RECT_HUD_COUNT + 1;
    private static final int KEYSTROKE_W = 0;
    private static final int KEYSTROKE_A = 1;
    private static final int KEYSTROKE_S = 2;
    private static final int KEYSTROKE_D = 3;
    private static final int KEYSTROKE_LMB = 4;
    private static final int KEYSTROKE_RMB = 5;
    private static final int KEYSTROKE_SPACE = 6;
    private static List<ShapeProperties> KEYSTROKE_BLUR_RECTS = rebuildKeystrokeBlurRects(3.0f);
    private static float PHAZE_KEYSTROKE_BLUR_ROUND = 3.0f;

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

    private static boolean inBatchPass = false;

    private static boolean inGradientPass = false;

    private static boolean batchIncludesBlur = false;
    private static boolean inLogicOnlyPass = false;

    private static final Map<Object, Boolean> PHAZE_LAST_BLUR_STATE = new IdentityHashMap<>();
    private static final Map<Object, Boolean> PHAZE_LAST_GRADIENT_STATE = new IdentityHashMap<>();

    private static final int[] PHAZE_GRADIENT_COLORS = new int[4];

    private static final Rectangle HUD_BACKGROUND_RECTANGLE = new Rectangle();

    private static final net.minecraft.util.Identifier PHAZE_GRADIENT_TEXTURE_ID =
            net.minecraft.util.Identifier.of("phaze", "hud/hud_gradient");
    private static NativeImageBackedTexture phaze$gradientTexture;
    private static float directionDisplayYaw = Float.NaN;
    private static final long SESSION_START_MS = System.currentTimeMillis();

    @Unique
    private static void phaze$resetGuiRenderState() {
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void beginHudFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        renderedThisFrame = false;
        inBatchPass = false;
        batchIncludesBlur = false;
        inLogicOnlyPass = false;
        HudCursorRelay.reset();
        Blur.INSTANCE.beginCachedFrame();

        ExordiumAnimationBridge.beginHudFrame(context);
        phaze$advanceHotbarSlide();
        if (this.client != null && this.client.inGameHud != null
                && this.client.inGameHud.getChatHud() instanceof ChatAnimationFrameAccess access) {
            access.phaze$tickAnimationFrame();
        }
    }

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

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true, require = 0)
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

        if (client != null && client.currentScreen instanceof MenuScreen) {
            BatchedHudBuffer.INSTANCE.invalidate();
            Blur.INSTANCE.endCachedFrame();
            return;
        }

        vorga.phazeclient.implement.menu.AnnouncementOverlay.render(context);

        if (!renderedThisFrame) {

            boolean hasLiveBlurHud = !hudHidden && phaze$prescanBlurStateFlips();
            boolean hasLiveHudAnimation = !hudHidden && phaze$prescanAnimatedGradientFlips();
            if (hasLiveBlurHud) {
                Blur.INSTANCE.captureBaseFrameForBlur();
            }

            if (hudHidden) {
                BatchedHudBuffer.INSTANCE.invalidate();
            } else if (!HudOptimizer.getInstance().isEnabled()) {
                BatchedHudBuffer.INSTANCE.invalidate();
                renderHudInternal(context);
            } else if (phaze$shouldBypassHudBatchingForCurrentScreen(client)) {

                BatchedHudBuffer.INSTANCE.invalidate();
                renderHudInternal(context);
            } else {

                int configuredRefreshRate = HudOptimizer.getInstance().refreshRate.getInt();
                int blurBackgroundRefreshRate = HudOptimizer.getInstance().blurRefreshRate.getInt();

                int backgroundRefreshRate = hasLiveBlurHud ? blurBackgroundRefreshRate : configuredRefreshRate;
                BatchedHudBuffer.INSTANCE.setTargetFps(backgroundRefreshRate);
                boolean chatEditing = client.currentScreen instanceof ChatScreen;

                boolean shouldRefresh = BatchedHudBuffer.INSTANCE.shouldRefresh(chatEditing);

                if (shouldRefresh) {

                    inBatchPass = true;
                    batchIncludesBlur = true;
                    BatchedHudBuffer.INSTANCE.beginCapture();
                    renderHudInternal(context);

                    BatchedHudBuffer.INSTANCE.endCapture();
                    batchIncludesBlur = false;
                    inBatchPass = false;
                } else {

                    inBatchPass = true;
                    batchIncludesBlur = true;
                    Blur.hudImmediateDrawsSuppressed = true;
                    try {
                        renderHudInternal(context);
                    } finally {
                        Blur.hudImmediateDrawsSuppressed = false;
                        batchIncludesBlur = false;
                        inBatchPass = false;
                    }
                }
                BatchedHudBuffer.INSTANCE.blit();

                if (hasLiveHudAnimation) {
                    inBatchPass = true;
                    inGradientPass = true;
                    renderHudInternal(context);
                    inGradientPass = false;
                    inBatchPass = false;
                }

                inLogicOnlyPass = true;
                renderHudInternal(context);
                inLogicOnlyPass = false;
            }
        }
        Blur.INSTANCE.endCachedFrame();

        if (client != null && client.options != null && !client.options.hudHidden) {

            float screenWidth = client.getWindow().getScaledWidth();
            float screenHeight = client.getWindow().getScaledHeight();
            renderZoomLevel(context, client, screenWidth, screenHeight);
        }
        if (client != null && client.currentScreen instanceof ChatScreen) {
            ScaleSnapOverlay.render(context);
        }
    }

    private void renderHudInternal(DrawContext context) {

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
            updateKeystrokeAnimations(client, mouseDown, rightMouseDown, deltaSeconds);
            showVerticalGuideThisFrame = false;
            showHorizontalGuideThisFrame = false;
            showHudVerticalGuideThisFrame = false;
            showHudHorizontalGuideThisFrame = false;
        }
        if (inLogicOnlyPass) {
            renderLiveKeystrokeAnimation(context, client, inverseGuiScale);
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

            double speed = Math.sqrt(client.player.squaredDistanceTo(new Vec3d(client.player.lastX, client.player.lastY, client.player.lastZ))) * 20.0;

            if (module.onlyUseGroundSpeed.isValue()) {
                double dx = client.player.getX() - client.player.lastX;
                double dz = client.player.getZ() - client.player.lastZ;
                speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
            }

            if (speed < 0.01) speed = 0.0;

            String value = module.getSpeedText(speed) + " m/s";

            return module.reverseOrder.isValue() ? "Speed: " + value : value;
        });
        final String speedTextWrapped = wrapTextWithBrackets(speedText, MovementSpeedHud.getInstance());

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

    private void renderBufferedHud(DrawContext context, RectHudModule module, boolean chatEditing, Runnable renderLogic) {
        boolean animatedBackground = module.hasActiveAnimatedBackground();
        phaze$trackBlurStateChange(module, module.hasActiveBackgroundBlur());
        phaze$trackGradientStateChange(module, animatedBackground);
        if (inGradientPass) {
            if (animatedBackground) {
                renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), 60, chatEditing,
                        () -> phaze$withHudTextAlignment(module, renderLogic));
            }
            return;
        }
        if (inBatchPass && animatedBackground) return;
        if (shouldSkipForCurrentPass(module.hasActiveBackgroundBlur())) return;
        renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), 60, chatEditing,
                () -> phaze$withHudTextAlignment(module, renderLogic));
    }

    private static void phaze$withHudTextAlignment(RectHudModule module, Runnable renderLogic) {
        float previousOffset = phaze$currentHudTextYOffset;
        phaze$currentHudTextYOffset = phaze$usesDefaultTextAlignment(module) ? 0.3F : 0.0F;
        try {
            renderLogic.run();
        } finally {
            phaze$currentHudTextYOffset = previousOffset;
        }
    }

    private static boolean phaze$usesDefaultTextAlignment(RectHudModule module) {
        return !(module instanceof ScoreboardHud)
                && !(module instanceof WailaHud)
                && !(module instanceof PlayerModelHud);
    }

    private static void phaze$trackBlurStateChange(Object module, boolean current) {
        Boolean prev = PHAZE_LAST_BLUR_STATE.put(module, current);
        if (prev != null && prev != current) {
            BatchedHudBuffer.INSTANCE.invalidate();
        }
    }

    private static boolean phaze$prescanBlurStateFlips() {
        Main main = Main.getInstance();
        if (main == null) {
            return false;
        }
        var provider = main.getModuleProvider();
        if (provider == null) {
            return false;
        }
        boolean anyActiveBlur = false;
        for (Module module : provider.getModules()) {
            boolean current;
            if (module instanceof RectHudModule rectModule) {
                current = rectModule.hasActiveBackgroundBlur();
            } else if (module instanceof ArmorHud armorModule) {
                current = armorModule.hasActiveBackgroundBlur();
            } else {
                continue;
            }
            anyActiveBlur |= module.isEnabled() && current;
            Boolean prev = PHAZE_LAST_BLUR_STATE.put(module, current);
            if (prev != null && prev != current) {
                BatchedHudBuffer.INSTANCE.invalidate();
            }
        }
        return anyActiveBlur;
    }

    private static boolean phaze$prescanAnimatedGradientFlips() {
        Main main = Main.getInstance();
        if (main == null) {
            return false;
        }
        var provider = main.getModuleProvider();
        if (provider == null) {
            return false;
        }
        boolean anyAnimated = false;
        for (Module module : provider.getModules()) {
            boolean current;
            if (module instanceof RectHudModule rectModule) {
                current = rectModule.hasActiveAnimatedBackground();
            } else if (module instanceof ArmorHud armorModule) {
                current = armorModule.hasActiveAnimatedBackground();
            } else {
                continue;
            }
            anyAnimated |= module.isEnabled() && current;
            Boolean prev = PHAZE_LAST_GRADIENT_STATE.put(module, current);
            if (prev != null && prev != current) {
                BatchedHudBuffer.INSTANCE.invalidate();
            }
        }
        return anyAnimated;
    }

    private static void phaze$trackGradientStateChange(Object module, boolean current) {
        Boolean previous = PHAZE_LAST_GRADIENT_STATE.put(module, current);
        if (previous != null && previous != current) {
            BatchedHudBuffer.INSTANCE.invalidate();
        }
    }

    private static float phaze$getClampedHudCornerRadius(float requestedRadius, float width, float height) {
        return net.minecraft.util.math.MathHelper.clamp(requestedRadius, 0.0F, Math.max(0.0F, Math.min(width, height) * 0.5F));
    }

    private static void phaze$renderGradientRect(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int hoverFill,
            int startColor,
            int endColor,
            String direction,
            float animationOffset,
            boolean animated,
            float requestedRadius
    ) {
        if (hoverFill != 0) {
            startColor = blendARGB(startColor, hoverFill);
            endColor = blendARGB(endColor, hoverFill);
        }
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.max(left + 1, Math.round(x + width));
        int bottom = Math.max(top + 1, Math.round(y + height));
        String selectedDirection = direction == null ? "Left to Right" : direction;
        float radius = phaze$getClampedHudCornerRadius(requestedRadius, right - left, bottom - top);

        if (radius > 0.01F) {

            if (phaze$renderTexturedRoundedGradient(context, x, y, width, height, startColor, endColor,
                    selectedDirection, animationOffset, animated, radius)) {
                return;
            }
            phaze$renderRoundedGradient(context, left, top, right, bottom, startColor, endColor,
                    selectedDirection, animationOffset, animated, radius);
            return;
        }

        if (!animated) {
            phaze$renderStaticGradient(context, left, top, right, bottom, startColor, endColor, selectedDirection);
            return;
        }

        if ("Pulse".equals(selectedDirection)) {
            context.fill(left, top, right, bottom, phaze$movingGradientColor(startColor, endColor, animationOffset));
            return;
        }

        if ("Top to Bottom".equals(selectedDirection) || "Bottom to Top".equals(selectedDirection)) {
            float directionSign = "Top to Bottom".equals(selectedDirection) ? -1.0F : 1.0F;
            phaze$renderMovingAxisGradient(context, left, top, right, bottom, startColor, endColor,
                    directionSign * animationOffset, false);
            return;
        }

        if ("Left to Right".equals(selectedDirection) || "Right to Left".equals(selectedDirection)) {
            float directionSign = "Right to Left".equals(selectedDirection) ? 1.0F : -1.0F;
            phaze$renderMovingAxisGradient(context, left, top, right, bottom, startColor, endColor,
                    directionSign * animationOffset, true);
            return;
        }

        int stripCount = 96;
        for (int strip = 0; strip < stripCount; strip++) {
            float progress = (strip + 0.5F) / stripCount;
            float stripLeft = MathHelper.lerp(strip / (float) stripCount, left, right);
            float stripRight = MathHelper.lerp((strip + 1.0F) / stripCount, left, right);

            if ("Diagonal Down".equals(selectedDirection) || "Diagonal Up".equals(selectedDirection)) {
                boolean down = "Diagonal Down".equals(selectedDirection);
                float topCoordinate = down ? progress * 0.5F : 0.5F + progress * 0.5F;
                float bottomCoordinate = down ? 0.5F + progress * 0.5F : progress * 0.5F;
                int topColor = phaze$movingGradientColor(startColor, endColor, topCoordinate - animationOffset);
                int bottomColor = phaze$movingGradientColor(startColor, endColor, bottomCoordinate - animationOffset);
                phaze$drawVerticalGradient(context, stripLeft, top, stripRight - stripLeft, bottom - top, topColor, bottomColor);
            } else {
                float directionSign = "Right to Left".equals(selectedDirection) ? 1.0F : -1.0F;
                int color = phaze$movingGradientColor(startColor, endColor, progress + directionSign * animationOffset);
                phaze$drawVerticalGradient(context, stripLeft, top, stripRight - stripLeft, bottom - top, color, color);
            }
        }
    }

    private static void phaze$renderStaticGradient(
            DrawContext context,
            int left,
            int top,
            int right,
            int bottom,
            int startColor,
            int endColor,
            String direction
    ) {
        if ("Top to Bottom".equals(direction)) {
            phaze$drawVerticalGradient(context, left, top, right - left, bottom - top, startColor, endColor);
            return;
        }
        if ("Bottom to Top".equals(direction)) {
            phaze$drawVerticalGradient(context, left, top, right - left, bottom - top, endColor, startColor);
            return;
        }
        if ("Pulse".equals(direction)) {
            context.fill(left, top, right, bottom, startColor);
            return;
        }
        if ("Left to Right".equals(direction) || "Right to Left".equals(direction)) {
            boolean reversed = "Right to Left".equals(direction);
            phaze$drawHorizontalGradient(context, left, top, right - left, bottom - top,
                    reversed ? endColor : startColor, reversed ? startColor : endColor);
            return;
        }
        int stripCount = 96;
        for (int strip = 0; strip < stripCount; strip++) {
            float progress = (strip + 0.5F) / stripCount;
            float stripLeft = MathHelper.lerp(strip / (float) stripCount, left, right);
            float stripRight = MathHelper.lerp((strip + 1.0F) / stripCount, left, right);
            if ("Diagonal Down".equals(direction) || "Diagonal Up".equals(direction)) {
                boolean down = "Diagonal Down".equals(direction);
                float topProgress = down ? progress * 0.5F : 0.5F + progress * 0.5F;
                float bottomProgress = down ? 0.5F + progress * 0.5F : progress * 0.5F;
                phaze$drawVerticalGradient(context, stripLeft, top, stripRight - stripLeft, bottom - top,
                        phaze$lerpGradientColor(startColor, endColor, topProgress),
                        phaze$lerpGradientColor(startColor, endColor, bottomProgress));
            } else {
                float colorProgress = "Right to Left".equals(direction) ? 1.0F - progress : progress;
                int color = phaze$lerpGradientColor(startColor, endColor, colorProgress);
                phaze$drawVerticalGradient(context, stripLeft, top, stripRight - stripLeft, bottom - top, color, color);
            }
        }
    }

    private static void phaze$renderMovingAxisGradient(
            DrawContext context,
            float left,
            float top,
            float right,
            float bottom,
            int startColor,
            int endColor,
            float phase,
            boolean horizontal
    ) {
        float position = 0.0F;
        for (int segment = 0; segment < 4 && position < 0.99999F; segment++) {
            float coordinate = position + phase;
            float nextStop = ((float) Math.floor(coordinate * 2.0F + 0.00001F) + 1.0F) * 0.5F - phase;
            float endPosition = MathHelper.clamp(nextStop, position + 0.0001F, 1.0F);
            int fromColor = phaze$movingLinearGradientColor(startColor, endColor, coordinate);
            int toColor = phaze$movingLinearGradientColor(startColor, endColor, endPosition + phase);

            if (horizontal) {
                float segX = MathHelper.lerp(position, left, right);
                float segWidth = MathHelper.lerp(endPosition, left, right) - segX;
                phaze$drawHorizontalGradient(context, segX, top, segWidth, bottom - top, fromColor, toColor);
            } else {
                float segY = MathHelper.lerp(position, top, bottom);
                float segHeight = MathHelper.lerp(endPosition, top, bottom) - segY;
                phaze$drawVerticalGradient(context, left, segY, right - left, segHeight, fromColor, toColor);
            }
            position = endPosition;
        }
    }

    private static boolean phaze$renderTexturedRoundedGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int startColor,
            int endColor,
            String direction,
            float animationOffset,
            boolean animated,
            float radius
    ) {
        if (inBatchPass && !BatchedHudBuffer.INSTANCE.isCaptureActive() && !inGradientPass) {
            return true;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getTextureManager() == null) {
                return false;
            }
            if (phaze$gradientTexture == null) {
                phaze$gradientTexture = new NativeImageBackedTexture("phaze/hud_gradient", 64, 64, false);
                client.getTextureManager().registerTexture(PHAZE_GRADIENT_TEXTURE_ID, phaze$gradientTexture);
            }
            NativeImageBackedTexture texture = phaze$gradientTexture;
            var image = texture.getImage();
            if (image == null) {
                return false;
            }
            for (int py = 0; py < 64; py++) {
                float yProgress = py / 63.0F;
                for (int px = 0; px < 64; px++) {
                    float xProgress = px / 63.0F;
                    image.setColorArgb(px, py, phaze$sampleHudGradientColor(startColor, endColor, direction,
                            xProgress, yProgress, animationOffset, animated));
                }
            }
            texture.upload();
            Render2DUtil.drawRoundedTexturedQuad(context.getMatrices(),
                    PHAZE_GRADIENT_TEXTURE_ID,
                    x, y, width, height, -1.0E9F, 1.0E9F, radius,
                    0.0F, 1.0F, 0.0F, 1.0F, 0xFFFFFFFF);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void phaze$renderRoundedGradient(
            DrawContext context,
            float left,
            float top,
            float right,
            float bottom,
            int startColor,
            int endColor,
            String direction,
            float animationOffset,
            boolean animated,
            float radius
    ) {
        float width = right - left;
        float height = bottom - top;
        MinecraftClient client = MinecraftClient.getInstance();
        float windowScale = client == null || client.getWindow() == null
                ? 1.0F
                : Math.max(1.0F, (float) client.getWindow().getScaleFactor());
        var matrices = context.getMatrices();
        float physicalScaleX = Math.max(0.01F, Math.abs(matrices.m00()) * windowScale);
        float physicalScaleY = Math.max(0.01F, Math.abs(matrices.m11()) * windowScale);
        float antialiasSize = Math.min(radius, 1.0F / physicalScaleY);

        float capSpan = Math.min(radius, width * 0.5F);
        int capStrips = MathHelper.clamp((int) Math.ceil(capSpan * physicalScaleX * 2.5F), 12, 128);
        int midStrips = 24;
        float capProgress = capSpan / width;
        boolean hasMiddle = capProgress < 0.4999F;
        int totalStrips = capStrips * 2 + (hasMiddle ? midStrips : capStrips);

        for (int strip = 0; strip < totalStrips; strip++) {
            float x0Progress;
            float x1Progress;
            if (strip < capStrips) {

                x0Progress = strip / (float) capStrips * capProgress;
                x1Progress = (strip + 1) / (float) capStrips * capProgress;
            } else if (hasMiddle && strip < capStrips + midStrips) {

                float m = strip - capStrips;
                x0Progress = capProgress + m / (float) midStrips * (1.0F - 2.0F * capProgress);
                x1Progress = capProgress + (m + 1) / (float) midStrips * (1.0F - 2.0F * capProgress);
            } else {

                int r = hasMiddle ? strip - capStrips - midStrips : strip - capStrips;
                x1Progress = 1.0F - r / (float) capStrips * capProgress;
                x0Progress = 1.0F - (r + 1) / (float) capStrips * capProgress;
            }
            float xProgress = (x0Progress + x1Progress) * 0.5F;
            float stripLeft = MathHelper.lerp(x0Progress, left, right);
            float stripRight = MathHelper.lerp(x1Progress, left, right);
            float edgeDistance = Math.min(xProgress * width, (1.0F - xProgress) * width);
            float inset = 0.0F;
            boolean roundedCap = edgeDistance < radius;
            if (roundedCap) {
                float dx = radius - edgeDistance;
                inset = radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - dx * dx));
            }

            float exactTop = top + inset;
            float exactBottom = bottom - inset;
            float solidTop = Math.min(exactBottom, exactTop + antialiasSize * 0.5F);
            float solidBottom = Math.max(exactTop, exactBottom - antialiasSize * 0.5F);
            if (exactBottom <= exactTop) {
                continue;
            }
            float topProgress = (solidTop - top) / height;
            float bottomProgress = (solidBottom - top) / height;
            int topColor = phaze$sampleHudGradientColor(startColor, endColor, direction,
                    xProgress, topProgress, animationOffset, animated);
            int bottomColor = phaze$sampleHudGradientColor(startColor, endColor, direction,
                    xProgress, bottomProgress, animationOffset, animated);
            phaze$drawVerticalGradient(context, stripLeft, solidTop, stripRight - stripLeft,
                    solidBottom - solidTop, topColor, bottomColor);

            if (roundedCap && antialiasSize > 0.001F) {
                int exactTopColor = phaze$sampleHudGradientColor(startColor, endColor, direction,
                        xProgress, inset / height, animationOffset, animated);
                int exactBottomColor = phaze$sampleHudGradientColor(startColor, endColor, direction,
                        xProgress, 1.0F - inset / height, animationOffset, animated);
                phaze$drawVerticalGradient(context, stripLeft, exactTop - antialiasSize * 0.5F,
                        stripRight - stripLeft, antialiasSize,
                        phaze$multiplyColorAlpha(exactTopColor, 0.0F), exactTopColor);
                phaze$drawVerticalGradient(context, stripLeft, exactBottom - antialiasSize * 0.5F,
                        stripRight - stripLeft, antialiasSize,
                        exactBottomColor, phaze$multiplyColorAlpha(exactBottomColor, 0.0F));
            }
        }
    }

    private static int phaze$sampleHudGradientColor(
            int startColor,
            int endColor,
            String direction,
            float xProgress,
            float yProgress,
            float animationOffset,
            boolean animated
    ) {
        if ("Pulse".equals(direction)) {
            return animated
                    ? phaze$movingGradientColor(startColor, endColor, animationOffset)
                    : startColor;
        }

        float coordinate;
        float phase;
        switch (direction) {
            case "Right to Left" -> {
                coordinate = 1.0F - xProgress;
                phase = animationOffset;
            }
            case "Top to Bottom" -> {
                coordinate = yProgress;
                phase = -animationOffset;
            }
            case "Bottom to Top" -> {
                coordinate = 1.0F - yProgress;
                phase = animationOffset;
            }
            case "Diagonal Down" -> {
                coordinate = (xProgress + yProgress) * 0.5F;
                phase = -animationOffset;
            }
            case "Diagonal Up" -> {
                coordinate = (xProgress + 1.0F - yProgress) * 0.5F;
                phase = -animationOffset;
            }
            default -> {
                coordinate = xProgress;
                phase = -animationOffset;
            }
        }
        return animated
                ? phaze$movingGradientColor(startColor, endColor, coordinate + phase)
                : phaze$lerpGradientColor(startColor, endColor, MathHelper.clamp(coordinate, 0.0F, 1.0F));
    }

    private static void phaze$drawVerticalGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int startColor,
            int endColor
    ) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        if (startColor == endColor) {
            context.fill(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), startColor);
            return;
        }
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(width, height);
        context.fillGradient(0, 0, 1, 1, startColor, endColor);
        context.getMatrices().popMatrix();
    }

    private static void phaze$drawHorizontalGradient(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            int startColor,
            int endColor
    ) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        if (startColor == endColor) {
            context.fill(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), startColor);
            return;
        }
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(width, height);
        context.getMatrices().rotate((float) Math.toRadians(-90.0D));
        context.fillGradient(-1, 0, 0, 1, startColor, endColor);
        context.getMatrices().popMatrix();
    }

    private static int phaze$movingLinearGradientColor(int startColor, int endColor, float coordinate) {
        float wrapped = coordinate - (float) Math.floor(coordinate);
        float blend = wrapped < 0.5F ? wrapped * 2.0F : (1.0F - wrapped) * 2.0F;
        return phaze$lerpGradientColor(startColor, endColor, blend);
    }

    private static int phaze$multiplyColorAlpha(int color, float multiplier) {
        int alpha = MathHelper.clamp(Math.round(((color >>> 24) & 0xFF) * multiplier), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int phaze$movingGradientColor(int startColor, int endColor, float coordinate) {
        float wrapped = coordinate - (float) Math.floor(coordinate);
        float blend = 0.5F - 0.5F * (float) Math.cos(wrapped * Math.PI * 2.0D);
        return phaze$lerpGradientColor(startColor, endColor, blend);
    }

    private static int phaze$lerpGradientColor(int first, int second, float progress) {
        float t = net.minecraft.util.math.MathHelper.clamp(progress, 0.0F, 1.0F);
        int a = Math.round(((first >>> 24) & 0xFF) + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * t);
        int r = Math.round(((first >>> 16) & 0xFF) + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * t);
        int g = Math.round(((first >>> 8) & 0xFF) + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * t);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderBufferedHud(DrawContext context, ArmorHud module, boolean chatEditing, Runnable renderLogic) {
        boolean animatedBackground = module.hasActiveAnimatedBackground();
        phaze$trackBlurStateChange(module, module.hasActiveBackgroundBlur());
        phaze$trackGradientStateChange(module, animatedBackground);
        if (inGradientPass) {
            if (animatedBackground) {
                renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), 60, chatEditing, () -> {
                    float previousOffset = phaze$currentHudTextYOffset;
                    phaze$currentHudTextYOffset = 0.0F;
                    try {
                        renderLogic.run();
                    } finally {
                        phaze$currentHudTextYOffset = previousOffset;
                    }
                });
            }
            return;
        }
        if (inBatchPass && animatedBackground) return;
        if (shouldSkipForCurrentPass(module.hasActiveBackgroundBlur())) return;
        renderBufferedHudInternal(context, module.isEnabled(), false, module.getHudBuffer(), 60, chatEditing, () -> {
            float previousOffset = phaze$currentHudTextYOffset;
            phaze$currentHudTextYOffset = 0.0F;
            try {
                renderLogic.run();
            } finally {
                phaze$currentHudTextYOffset = previousOffset;
            }
        });
    }

    private static boolean shouldSkipForCurrentPass(boolean hasBlur) {
        if (inLogicOnlyPass) return true;
        if (inBatchPass && hasBlur && !batchIncludesBlur) return true;
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

        renderLogic.run();
    }

    private static long hudTextThrottleMs() {
        HudOptimizer optimizer = HudOptimizer.getInstance();
        if (optimizer != null && optimizer.isEnabled()) {
            return Math.max(4L, 1000L / Math.max(1, optimizer.refreshRate.getInt()));
        }
        return HUD_TEXT_THROTTLE_MS;
    }

    private String getCachedHudText(RectHudModule module, int hudIndex, boolean chatEditing, Supplier<String> supplier) {
        long now = System.currentTimeMillis();
        long throttleMs = hudTextThrottleMs();
        boolean throttledDue = now - HUD_TEXT_CACHE_TIME_MS[hudIndex] >= throttleMs;
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
        boolean resized = RECT_LAYOUT_INITIALIZED[hudIndex]
                && (RECT_LAST_SCREEN_WIDTH[hudIndex] != currentScreenWidth
                || RECT_LAST_SCREEN_HEIGHT[hudIndex] != currentScreenHeight);
        if (resized && !RECT_DRAGGING[hudIndex] && !RECT_RESIZING[hudIndex]) {
            x = maxX <= 0.0f ? 0.0f : RECT_POSITION_RATIO_X[hudIndex] * maxX;
            y = maxY <= 0.0f ? 0.0f : RECT_POSITION_RATIO_Y[hudIndex] * maxY;
        }
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

                    float minWidth = baseWidth * module.getMinHudScale() * 2.0F;
                    float maxWidth = baseWidth * module.getMaxHudScale() * 2.0F;
                    float newWidth = MathHelper.clamp(RECT_RESIZE_START_WIDTH[hudIndex] + delta * 0.9f, minWidth, maxWidth);
                    float newScale = snapAndAnnounceHudScale(module, newWidth / baseWidth / 2.0F);

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

        if (chatEditing) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        float textWidth = getHudTextWidth(client, text, HUD_TEXT_SIZE);
        float textX = (baseWidth - textWidth) / 2.0f;
        float textY = (baseHeight - 8.0f) / 2.0f;
        int hoverOutlineThickness = Math.max(1, Math.round(BASE_HOVER_OUTLINE_THICKNESS / Math.max(1.0f, scale)));

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

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
                            .round(phaze$getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            float cornerRadius = phaze$getClampedHudCornerRadius(module.cornerRounding.getValue(), baseWidth, baseHeight);
            if (module.isGradientPreset()) {

                RECT_BG_ANIMATED_COLOR[hudIndex] = module.getResolvedGradientStartColor();
                if (hudIndex != HUD_KEYSTROKES) {
                    int hoverFill = chatEditing
                            ? withAlpha(0xFFFFFF, (int) (30.0f * RECT_HOVER_PROGRESS[hudIndex]))
                            : 0;

                    phaze$renderGradientRect(context, 0.0F, 0.0F, baseWidth, baseHeight, hoverFill,
                            module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                            module.getGradientDirection(),
                            module.getGradientAnimationOffset(System.currentTimeMillis()),
                            module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
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
                    if (cornerRadius <= 0.01F) {
                        context.fill(0, 0, Math.round(baseWidth), Math.round(baseHeight), bgColor);
                    } else if (!inBatchPass || BatchedHudBuffer.INSTANCE.isCaptureActive()) {

                        HUD_BACKGROUND_RECTANGLE.render(ShapeProperties.create(context.getMatrices(), 0.0F, 0.0F, baseWidth, baseHeight)
                                .round(cornerRadius)
                                .softness(1.0F)
                                .color(bgColor)
                                .build());
                    }
                }
            }
        }

        context.getMatrices().popMatrix();

        int textColor = resolveHudTextColor();
        if (module instanceof MemoryHud) {
            MemoryHud memoryHud = (MemoryHud) module;
            int memoryColor = memoryHud.getMemoryColor();
            if (memoryColor != HUD_TEXT_COLOR) {
                textColor = memoryColor;
            }
        } else if (module instanceof HealthIndicator) {

            int healthColor = ((HealthIndicator) module).getCurrentHpColor();
            if (healthColor != HUD_TEXT_COLOR) {
                textColor = healthColor;
            }
        } else if (module instanceof TpsHud) {

            int tpsColor = ((TpsHud) module).getColor();
            if (tpsColor != HUD_TEXT_COLOR) {
                textColor = tpsColor;
            }
        }

        renderScaledHudTextColored(context, client, text, x, y, textX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), textColor);

        if (chatEditing && RECT_HOVER_PROGRESS[hudIndex] > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (175.0f * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
        if (showResizeHandle) {
            context.getMatrices().pushMatrix();

            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);

            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                int borderColor = withAlpha(0xFFFFFF, 220);
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, borderColor);
            }
            context.getMatrices().popMatrix();
        }

        context.getMatrices().popMatrix();
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

        List<ItemStack> updatedStacks = new ArrayList<>();
        List<String> updatedDurabilityTexts = new ArrayList<>();

        for (EquipmentSlot armorSlot : PHAZE_ARMOR_SLOTS) {
            ItemStack stack = client.player.getEquippedStack(armorSlot);
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
        boolean resized = armorLayoutInitialized
                && (armorLastScreenWidth != currentScreenWidth || armorLastScreenHeight != currentScreenHeight);
        if (resized && !armorDragging && !armorResizing) {
            x = maxX <= 0.0f ? 0.0f : armorPositionRatioX * maxX;
            y = maxY <= 0.0f ? 0.0f : armorPositionRatioY * maxY;
        }
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
                    armorResizeStartScale = module.getHudScale();
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

                    float newScale = armorResizeStartScale + (delta * 0.9f) / (BASE_WIDTH * 2.0F);
                    newScale = snapAndAnnounceHudScale(module, newScale);
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

        if (chatEditing) {
            if (armorResizing) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (armorDragging) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        boolean textOnLeft = x > screenWidth * 0.5f;
        int hoverOutlineThickness = Math.max(1, Math.round(2.0f / Math.max(1.0f, scale)));

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

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
                            .round(phaze$getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            float cornerRadius = phaze$getClampedHudCornerRadius(module.cornerRounding.getValue(), baseWidth, baseHeight);
            if (module.isGradientPreset()) {
                int hoverFill = chatEditing
                        ? withAlpha(0xFFFFFF, (int) (30.0f * armorHoverProgress))
                        : 0;
                phaze$renderGradientRect(context, 0.0F, 0.0F, baseWidth, baseHeight, hoverFill,
                        module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                        module.getGradientDirection(),
                        module.getGradientAnimationOffset(System.currentTimeMillis()),
                        module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
            } else {
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
                if (cornerRadius <= 0.01F) {
                    context.fill(0, 0, Math.round(baseWidth), Math.round(baseHeight), bgColor);
                } else if (!inBatchPass || BatchedHudBuffer.INSTANCE.isCaptureActive()) {

                    HUD_BACKGROUND_RECTANGLE.render(ShapeProperties.create(context.getMatrices(), 0.0F, 0.0F, baseWidth, baseHeight)
                            .round(cornerRadius)
                            .softness(1.0F)
                            .color(bgColor)
                            .build());
                }
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
        context.getMatrices().popMatrix();

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

            float textY = rowY + 5.0f;

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

        if (chatEditing && armorHoverProgress > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (160.0f * armorHoverProgress));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && (armorResizing || hoveredHandle || hovered || nearHud);
        if (showResizeHandle) {
            context.getMatrices().pushMatrix();

            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = armorResizing ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);

            if (hoveredHandle || armorResizing) {
                int borderColor = withAlpha(0xFFFFFF, 220);
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, borderColor);
            }
            context.getMatrices().popMatrix();
        }

        context.getMatrices().popMatrix();
    }

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

        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();
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
                    RECT_RESIZE_START_WIDTH[hudIndex] = module.getHudScale();
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
                    float newScale = RECT_RESIZE_START_WIDTH[hudIndex] + (delta * 0.9f) / (BASE_WIDTH * 2.0F);
                    newScale = snapAndAnnounceHudScale(module, newScale);
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

        if (chatEditing) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        int hoverOutlineThickness = Math.max(1, Math.round(2.0f / Math.max(1.0f, scale)));

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

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
                            .round(phaze$getClampedHudCornerRadius(module.cornerRounding.getValue(), blurWidth, blurHeight))
                            .softness(0.0f)
                            .quality(blurQuality)
                            .color(0xFFFFFFFF)
                            .build());
                }
            }
        }

        if (module.background.isValue()) {
            float cornerRadius = phaze$getClampedHudCornerRadius(module.cornerRounding.getValue(), baseWidth, baseHeight);
            if (module.isGradientPreset()) {
                int hoverFill = chatEditing
                        ? withAlpha(0xFFFFFF, (int) (30.0f * RECT_HOVER_PROGRESS[hudIndex]))
                        : 0;
                phaze$renderGradientRect(context, 0.0F, 0.0F, baseWidth, baseHeight, hoverFill,
                        module.getResolvedGradientStartColor(), module.getResolvedGradientEndColor(),
                        module.getGradientDirection(),
                        module.getGradientAnimationOffset(System.currentTimeMillis()),
                        module.hasActiveAnimatedBackground(), module.cornerRounding.getValue());
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
                if (cornerRadius <= 0.01F) {
                    context.fill(0, 0, Math.round(baseWidth), Math.round(baseHeight), bgColor);
                } else if (!inBatchPass || BatchedHudBuffer.INSTANCE.isCaptureActive()) {

                    HUD_BACKGROUND_RECTANGLE.render(ShapeProperties.create(context.getMatrices(), 0.0F, 0.0F, baseWidth, baseHeight)
                            .round(cornerRadius)
                            .softness(1.0F)
                            .color(bgColor)
                            .build());
                }
            }
        }

        float padding = 3.0f;
        float itemSize = 18.0f;
        for (vorga.phazeclient.implement.features.modules.hud.Consumable.IconEntry entry : layout.entries()) {
            int iconX = Math.round(padding + entry.col() * itemSize);
            int iconY = Math.round(padding + entry.row() * itemSize);
            context.drawItem(entry.stack(), iconX, iconY);
            if (module.showCount.isValue()) {

                context.drawStackOverlay(client.textRenderer, entry.stack(), iconX, iconY);
            }
        }
        context.getMatrices().popMatrix();

        if (chatEditing && RECT_HOVER_PROGRESS[hudIndex] > 0.05f) {
            int borderColor = withAlpha(0xFFFFFF, (int) (160.0f * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, x, y, hudWidth, hudHeight, hoverOutlineThickness, borderColor);
        }

        boolean showResizeHandle = chatEditing && (RECT_RESIZING[hudIndex] || hoveredHandle || hovered || nearHud);
        if (showResizeHandle) {
            context.getMatrices().pushMatrix();

            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);
            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                int borderColor = withAlpha(0xFFFFFF, 220);
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, borderColor);
            }
            context.getMatrices().popMatrix();
        }

        context.getMatrices().popMatrix();
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

        List<String> updatedLines = new ArrayList<>();
        BlockPos pos = client.player.getBlockPos();

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
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);

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

                float maxStackHalf = baseHeight * 0.5f - 5.0f;
                if (maxStackHalf < 0.0f) {
                    maxStackHalf = 0.0f;
                }
                float signOffset = Math.min(10.0f, maxStackHalf);

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

        context.getMatrices().popMatrix();
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

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
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

        float valueX = reversed ? textX : textX + labelWidth;
        renderScaledHudTextColored(context, client, value, x, y, valueX, textY, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), pingColor);
        context.getMatrices().popMatrix();
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

    private void renderTrapTimerHud(
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

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        float textX = 7.0F;
        float textY = (BASE_HEIGHT - 9.0F) * 0.5F;
        context.drawText(client.textRenderer, text, Math.round(textX), Math.round(textY), resolveHudTextColor(), module.textShadow.isValue());
        context.getMatrices().popMatrix();
        context.getMatrices().popMatrix();
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
        float scale = module.getRenderHudScale();
        float textWidth = client.textRenderer.getWidth(text);
        float baseWidth = Math.max(48.0f, textWidth + 16.0f);
        float baseHeight = BASE_HEIGHT;

        renderRectHud(context, client, module, "", hudIndex, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        float textX = (baseWidth - textWidth) * 0.5f;
        float textY = (baseHeight - 9.0f) * 0.5f;
        context.drawText(client.textRenderer, text, Math.round(textX), Math.round(textY), resolveHudTextColor(), module.textShadow.isValue());
        context.getMatrices().popMatrix();
        context.getMatrices().popMatrix();
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

        net.minecraft.text.Text title = objective.getDisplayName();
        if (shouldHideNick) {
            title = nickHider.rewrite(title);
        }
        int titleWidth = (int)getHudTextWidth(client, title.getString(), HUD_TEXT_SIZE);
        int colonWidth = (int)getHudTextWidth(client, ":", HUD_TEXT_SIZE);
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

                maxContentWidth = Math.max(maxContentWidth, textWidth + colonWidth + scoreWidth);
            } else {
                maxContentWidth = Math.max(maxContentWidth, textWidth);
            }
        }

        float baseWidth = maxContentWidth + 4;
        int topInset = module.showTitle.isValue() ? 10 : 1;
        float baseHeight = entryCount * 9.0f + topInset;

        if (module.getHudX() <= 1.0f && module.getHudY() <= 1.0f) {
            float scale = module.getRenderHudScale();
            float hudWidth = baseWidth * scale;
            float hudHeight = baseHeight * scale;
            float vanillaX = Math.max(0.0f, screenWidth - hudWidth - 2.0f);
            float vanillaY = Math.max(0.0f, (screenHeight - hudHeight) * 0.5f);
            module.setHudX(vanillaX);
            module.setHudY(vanillaY);
        }

        boolean prevBackground = module.background.isValue();
        module.background.setValue(false);
        try {
            renderRectHud(context, client, module, "", HUD_SCOREBOARD, chatEditing, mouseX, mouseY, mouseDown,
                    deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight);
        } finally {
            module.background.setValue(prevBackground);
        }

        float hudX = module.getHudX();
        float hudY = module.getHudY();
        float hudScale = module.getRenderHudScale();

        int rightEdgeLocal = Math.round(baseWidth);
        int textLeftLocal = 2;
        int verticalPosLocal = entryCount * 9;

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

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(hudX, hudY);
        context.getMatrices().scale(hudScale, hudScale);

        context.getMatrices().translate(0.0f, topInset);

        if (module.background.isValue()) {
            int backgroundTopLocal = -topInset;
            int backgroundBottomLocal = verticalPosLocal;

            float blurRadius = Math.max(0.0f, module.backgroundBlurRadius.getValue());
            if (blurRadius > 0.0f) {

                float safeScale = Math.max(hudScale, 1.0f);
                float normalizedBlurRadius = blurRadius / safeScale;
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
                if (blurQuality > 0.0f && blurWidth > 1.5f && blurHeight > 1.5f) {
                    Blur.INSTANCE.renderCached(ShapeProperties.create(context.getMatrices(),
                                    0.0f, backgroundTopLocal, blurWidth, blurHeight)
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
            } else {
                if (module.showTitle.isValue()) {
                    context.fill(0, -topInset, rightEdgeLocal, -1, titleBgColor);
                }
                context.fill(0, -1, rightEdgeLocal, verticalPosLocal, rowBgColor);
            }
        }

        if (module.showTitle.isValue()) {
            float titleX = (rightEdgeLocal - titleWidth) / 2.0f;
            var textRenderer = client.textRenderer;
            context.drawText(textRenderer, title, (int)titleX, -topInset + 1, -1, false);
        }

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

                float scoreX = rightEdgeLocal - scoreWidth;
                context.drawText(textRenderer, sidebarEntry.score(), (int)scoreX, rowY, -1, false);
            }
        }

        context.getMatrices().popMatrix();
        context.getMatrices().popMatrix();

    }

    @Unique
    private static boolean phaze$shouldBypassHudBatchingForCurrentScreen(MinecraftClient client) {
        return client != null
                && client.world != null
                && client.currentScreen != null
                && !(client.currentScreen instanceof ChatScreen)
                && !(client.currentScreen instanceof MenuScreen);
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
        float scale = module.getRenderHudScale();
        float centerX = baseWidth * 0.5f;
        float leftPadding = 8.0f;
        float rightPadding = 8.0f;
        float visibleWidth = Math.max(64.0f, baseWidth - leftPadding - rightPadding);
        float degreesPerPixel = 90.0f / visibleWidth;

        float yawNumberY = 1.0f;
        float triangleY = 11.0f;
        float majorTickTop = 14.0f;
        float majorTickBottom = 28.0f;
        float minorTickTop = 17.0f;
        float minorTickBottom = 26.0f;
        float labelY = 30.0f;
        float baselineY = 28.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.fill(Math.round(x + leftPadding), Math.round(y + baselineY), Math.round(x + baseWidth - rightPadding), Math.round(y + baselineY + 1.0f), withAlpha(0xFFFFFF, 30));

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
        context.getMatrices().popMatrix();
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
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(hudX, hudY);
        context.getMatrices().scale(scale, scale);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, DIRECTION_TRIANGLE_TEXTURE, Math.round(localX), Math.round(localY), 0.0f, 0.0f, width, height, width, height);
        context.getMatrices().popMatrix();
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

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        renderKeystrokeButtonBlur(context, module, scale);
        float cachedProgressScale = inBatchPass ? 0.0f : 1.0f;
        float keystrokeCornerRadius = Math.min(module.cornerRounding.getValue(), 8.0f);
        renderKeyButton(context, 19, 0, 16, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_W] * cachedProgressScale, keystrokeCornerRadius);
        renderKeyButton(context, 0, 19, 18, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_A] * cachedProgressScale, keystrokeCornerRadius);
        renderKeyButton(context, 19, 19, 16, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_S] * cachedProgressScale, keystrokeCornerRadius);
        renderKeyButton(context, 36, 19, 18, 18, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_D] * cachedProgressScale, keystrokeCornerRadius);
        renderKeyButton(context, 0, 38, 54, 8, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] * cachedProgressScale, keystrokeCornerRadius);
        renderKeyButton(context, 0, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_LMB] * cachedProgressScale, keystrokeCornerRadius);
        renderKeyButton(context, 28, 47, 26, 16, idleColor, KEYSTROKE_PROGRESS[KEYSTROKE_RMB] * cachedProgressScale, keystrokeCornerRadius);
        context.getMatrices().popMatrix();

        renderKeyLabel(context, client, "W", x, y, 19, 5, 16, KEYSTROKE_PROGRESS[KEYSTROKE_W] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "A", x, y, 0, 24, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "S", x, y, 19, 24, 16, KEYSTROKE_PROGRESS[KEYSTROKE_S] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "D", x, y, 36, 24, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D] * cachedProgressScale, scale, module.textShadow.isValue());
        renderSpacebarLabel(context, x, y, 0, 39, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] * cachedProgressScale, scale);
        renderKeyLabel(context, client, "LMB", x, y, 0, 51, 26, KEYSTROKE_PROGRESS[KEYSTROKE_LMB] * cachedProgressScale, scale, module.textShadow.isValue());
        renderKeyLabel(context, client, "RMB", x, y, 28, 51, 26, KEYSTROKE_PROGRESS[KEYSTROKE_RMB] * cachedProgressScale, scale, module.textShadow.isValue());
        context.getMatrices().popMatrix();
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

        float textX = paddingX + iconSize + 3.0f;
        int rows = sample ? 1 : effects.size();
        float maxTextWidth = 0.0f;
        for (int i = 0; i < rows; i++) {
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, potionNamesCache.get(i), HUD_TEXT_SIZE));
            maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, potionDurationsCache.get(i), HUD_TEXT_SIZE));
        }

        float baseWidth = Math.max(91.0f, textX + maxTextWidth + paddingX - 3.0f);

        float baseHeight = paddingY * 2.0f + rows * rowHeight;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0f, 1.0f);
        renderRectHud(context, client, module, "", HUD_POTION, chatEditing, mouseX, mouseY, mouseDown,
                deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY, baseWidth, baseHeight - 4.0f);
        context.getMatrices().popMatrix();

        float x = module.getHudX();
        float y = module.getHudY();
        float scale = module.getRenderHudScale();
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);

        for (int i = 0; i < rows; i++) {
            float rowY = paddingY + i * rowHeight;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            if (sample) {
                context.drawItem(new ItemStack(Items.POTION), Math.round(paddingX), Math.round(rowY + 2.0f));
            } else {

                Identifier effectTexture = InGameHud.getEffectTexture(effects.get(i).getEffectType());
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectTexture, Math.round(paddingX), Math.round(rowY + 2.0f), Math.round(iconSize), Math.round(iconSize));
            }
            context.getMatrices().popMatrix();
        }
        for (int i = 0; i < rows; i++) {
            float rowY = paddingY + i * rowHeight;
            String name = potionNamesCache.get(i);
            String duration = potionDurationsCache.get(i);

            int nameColor = resolveHudTextColor();
            if (module.colorByType.isValue() && !sample && i < potionEffectsCache.size()) {
                StatusEffectInstance effect = potionEffectsCache.get(i);
                StatusEffectCategory cat = effect.getEffectType().value().getCategory();
                if (cat == StatusEffectCategory.BENEFICIAL) nameColor = 0xFF55FF55;
                else if (cat == StatusEffectCategory.HARMFUL) nameColor = 0xFFFF5555;
            }

            if (module.flashOnExpiry.isValue() && !sample && i < potionEffectsCache.size()) {
                StatusEffectInstance effect = potionEffectsCache.get(i);
                if (!effect.isInfinite() && effect.getDuration() < 200) {
                    boolean visible = (System.currentTimeMillis() / 500L) % 2L == 0L;
                    if (!visible) {
                        continue;
                    }
                }
            }

            renderScaledHudTextColored(context, client, name, x, y, textX, rowY + 2.0f, HUD_TEXT_SIZE, scale, module.textShadow.isValue(), nameColor);
            renderScaledHudText(context, client, duration, x, y, textX, rowY + 12.0f, HUD_TEXT_SIZE, scale, module.textShadow.isValue());
        }

        context.getMatrices().popMatrix();
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

        if (text.contains("A") || text.contains("a")) {
            width += 2.0f;
        }
        return width;
    }

    private static String getSprintHudText(MinecraftClient client) {

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

        if (client.currentScreen != null) {
            return "Not Sprinting";
        }

        AutoSprint autoSprint = AutoSprint.getInstance();
        boolean autoSprintActive = autoSprint.isEnabled() && autoSprint.showInSprintHud.isValue();

        if (autoSprintActive) {
            return "Sprinting (AutoSprint)";
        }

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

            return module.reverseOrder.isValue() ? "Day: 0" : "0 Days";
        }
        long days = Math.max(0L, client.world.getTime() / 24000L) + 1;
        if (module.reverseOrder.isValue()) {

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
        if (module.showPhase.isValue()) {

            TimeHud.Phase phase = module.phaseForClock(now.getHour());
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

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        float keystrokeCornerRadius = Math.min(module.cornerRounding.getValue(), 8.0f);
        renderLiveKeystrokeButton(context, 19, 0, 16, 18, KEYSTROKE_PROGRESS[KEYSTROKE_W], keystrokeCornerRadius);
        renderLiveKeystrokeButton(context, 0, 19, 18, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A], keystrokeCornerRadius);
        renderLiveKeystrokeButton(context, 19, 19, 16, 18, KEYSTROKE_PROGRESS[KEYSTROKE_S], keystrokeCornerRadius);
        renderLiveKeystrokeButton(context, 36, 19, 18, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D], keystrokeCornerRadius);
        renderLiveKeystrokeButton(context, 0, 38, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE], keystrokeCornerRadius);
        renderLiveKeystrokeButton(context, 0, 47, 26, 16, KEYSTROKE_PROGRESS[KEYSTROKE_LMB], keystrokeCornerRadius);
        renderLiveKeystrokeButton(context, 28, 47, 26, 16, KEYSTROKE_PROGRESS[KEYSTROKE_RMB], keystrokeCornerRadius);
        context.getMatrices().popMatrix();

        boolean shadow = module.textShadow.isValue();
        renderLiveKeystrokeLabel(context, client, "W", x, y, 19, 5, 16, KEYSTROKE_PROGRESS[KEYSTROKE_W], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "A", x, y, 0, 24, 18, KEYSTROKE_PROGRESS[KEYSTROKE_A], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "S", x, y, 19, 24, 16, KEYSTROKE_PROGRESS[KEYSTROKE_S], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "D", x, y, 36, 24, 18, KEYSTROKE_PROGRESS[KEYSTROKE_D], scale, shadow);
        if (KEYSTROKE_PROGRESS[KEYSTROKE_SPACE] > 0.45f) {
            renderSpacebarLabel(context, x, y, 0, 39, 54, 8, KEYSTROKE_PROGRESS[KEYSTROKE_SPACE], scale);
        }
        renderLiveKeystrokeLabel(context, client, "LMB", x, y, 0, 51, 26, KEYSTROKE_PROGRESS[KEYSTROKE_LMB], scale, shadow);
        renderLiveKeystrokeLabel(context, client, "RMB", x, y, 28, 51, 26, KEYSTROKE_PROGRESS[KEYSTROKE_RMB], scale, shadow);
        context.getMatrices().popMatrix();
    }

    private static void renderLiveKeystrokeButton(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            float progress,
            float radius
    ) {
        int alpha = MathHelper.clamp(Math.round(185.0f * progress), 0, 185);
        if (alpha > 0) {
            int color = withAlpha(0xFFFFFF, alpha);
            if (radius > 0.01F) {
                HUD_BACKGROUND_RECTANGLE.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                        .round(radius)
                        .softness(1.0F)
                        .color(color)
                        .build());
                return;
            }
            context.fill(x, y, x + width, y + height, color);
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
        float normalizedBlurRadius = blurRadius / safeScale;
        float blurQuality = getOptimizedHudBlurQuality(normalizedBlurRadius);
        if (blurQuality <= 0.0f) {
            return;
        }
        long blurStateKey = makeHudBlurStateKey(normalizedBlurRadius, safeScale, 0.0f, 0.0f, 54.0f, 63.0f);
        Blur.INSTANCE.registerHudBlurState(HUD_KEYSTROKES, blurStateKey);
        float blurRound = Math.min(Math.max(0.0f, module.cornerRounding.getValue()), 8.0f);
        List<ShapeProperties> blurRects = KEYSTROKE_BLUR_RECTS;
        if (blurRound != PHAZE_KEYSTROKE_BLUR_ROUND) {
            PHAZE_KEYSTROKE_BLUR_ROUND = blurRound;
            KEYSTROKE_BLUR_RECTS = rebuildKeystrokeBlurRects(blurRound);
            blurRects = KEYSTROKE_BLUR_RECTS;
        }
        for (ShapeProperties shape : blurRects) {

            shape.getMatrix().set(context.getMatrices());
            shape.setQuality(blurQuality);
        }
        Blur.INSTANCE.renderCachedBatch(blurRects);
    }

    private static ShapeProperties createKeystrokeBlurRect(float x, float y, float width, float height, float round) {

        return ShapeProperties.create(new Matrix3x2f(), x, y, width, height)
                .round(round)
                .softness(0.0f)
                .color(0xFFFFFFFF)
                .build();
    }

    private static List<ShapeProperties> rebuildKeystrokeBlurRects(float round) {
        return List.of(
                createKeystrokeBlurRect(19.0f, 0.0f, 16.0f, 18.0f, round),
                createKeystrokeBlurRect(0.0f, 19.0f, 18.0f, 18.0f, round),
                createKeystrokeBlurRect(19.0f, 19.0f, 16.0f, 18.0f, round),
                createKeystrokeBlurRect(36.0f, 19.0f, 18.0f, 18.0f, round),
                createKeystrokeBlurRect(0.0f, 38.0f, 54.0f, 8.0f, round),
                createKeystrokeBlurRect(0.0f, 47.0f, 26.0f, 16.0f, round),
                createKeystrokeBlurRect(28.0f, 47.0f, 26.0f, 16.0f, round)
        );
    }
    private static void renderKeyButton(DrawContext context, int x, int y, int width, int height, int idleColor, float progress, float radius) {
        int activeOverlay = withAlpha(0xFFFFFF, Math.round(185.0f * progress));
        int color = blendARGB(idleColor, activeOverlay);
        if (radius > 0.01F) {
            HUD_BACKGROUND_RECTANGLE.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                    .round(radius)
                    .softness(1.0F)
                    .color(color)
                    .build());
            return;
        }
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
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(hudX, hudY);
        context.getMatrices().scale(scale, scale);
        context.fill(Math.round(lx), Math.round(ly), Math.round(lx + lineWidth), Math.round(ly + lineThickness), lineColor);
        context.getMatrices().popMatrix();
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
        renderHudTextColoredWithAlpha(context, client, text, x, y, textSize, shadow, resolveHudTextColor() & 0x00FFFFFF, alpha);
    }

    private static int resolveHudTextColor() {
        Theme theme = Theme.getInstance();
        if (theme == null) {
            return HUD_TEXT_COLOR;
        }
        return theme.getHudTextColor();
    }

    private static void renderHudTextColoredWithAlpha(DrawContext context, MinecraftClient client, String text, float x, float y, float textSize, boolean shadow, int rgbColor, int alpha) {
        int textColor = (MathHelper.clamp(alpha, 0, 255) << 24) | (rgbColor & 0x00FFFFFF);
        context.drawText(client.textRenderer, text, Math.round(x), Math.round(y), textColor, shadow);
    }

    private static float getOptimizedHudBlurQuality(float normalizedBlurRadius) {
        float radius = Math.max(0.0f, normalizedBlurRadius);
        if (radius <= 0.001f) {
            return 0.0f;
        }

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
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(hudX, hudY);
        context.getMatrices().scale(scale, scale);
        renderHudText(context, client, text, textX, textY + phaze$currentHudTextYOffset, textSize, shadow);
        context.getMatrices().popMatrix();
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
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(hudX, hudY);
        context.getMatrices().scale(scale, scale);
        renderHudTextWithAlpha(context, client, text, textX, textY + phaze$currentHudTextYOffset, textSize, shadow, alpha);
        context.getMatrices().popMatrix();
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
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(hudX, hudY);
        context.getMatrices().scale(scale, scale);
        renderHudTextColored(context, client, text, textX, textY + phaze$currentHudTextYOffset, textSize, shadow, rgbColor);
        context.getMatrices().popMatrix();
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

        if (mouseDown && !wasLeftMouseDown) {
            LEFT_CLICKS.addLast(nowMs);
        }
        wasLeftMouseDown = mouseDown;

        while (!LEFT_CLICKS.isEmpty() && nowMs - LEFT_CLICKS.peekFirst() > 1000L) {
            LEFT_CLICKS.removeFirst();
        }

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

                || MovementSpeedHud.getInstance().isEnabled()
                || WailaHud.getInstance().isEnabled()
                || HealthIndicator.getInstance().isEnabled()
                || TrapTimer.getInstance().isEnabled()
                || vorga.phazeclient.implement.features.modules.hud.Consumable.getInstance().isEnabled()
                || PlayerModelHud.getInstance().isEnabled();
    }

    private static boolean isAnyHudInteractionActive() {

        for (int i = 0; i < RECT_HUD_COUNT; i++) {
            if (RECT_DRAGGING[i] || RECT_RESIZING[i]) {
                return true;
            }
        }
        return armorDragging || armorResizing;
    }

    private static float snapAndAnnounceHudScale(Module module, float rawScale) {
        float clamped = HudScaleLimits.clamp(rawScale);
        boolean snapped = HudScaleLimits.snapsToDefault(clamped);
        float value = snapped ? 1.0F : clamped;
        ScaleSnapOverlay.show(value, snapped, module.getVisibleName() + " Scale");
        return value;
    }

    private static void renderHudGuides(DrawContext context, float screenWidth, float screenHeight, float inverseGuiScale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);

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

        context.getMatrices().popMatrix();
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

        if (module.displayServerIcon.isValue()) {
            module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
            float scale = module.getRenderHudScale();
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

            String blockName = block.getName().getString();
            sb.append(blockName);

            if (waila.showCoordinates.isValue()
                    && !StreamerMode.getInstance().isHideCoordinatesEnabled()) {
                sb.append("\n(").append(pos.getX())
                  .append(", ").append(pos.getY())
                  .append(", ").append(pos.getZ())
                  .append(')');
            }

            if (waila.showCorrectTool.isValue()) {
                sb.append("\nCorrect Tool: ").append(phaze$resolveCorrectTool(state));
            }

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

            EntityType<?> entityType = entity.getType();
            sb.append("\nType: ").append(entityType.getName().getString());
        }

        return sb.toString();
    }

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

            return new ItemStack(Items.BARRIER);
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)hit).getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();

            Item item = block.asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        } else if (hit.getType() == HitResult.Type.ENTITY && waila.showEntities.isValue()) {
            Entity entity = ((EntityHitResult)hit).getEntity();

            if (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                net.minecraft.entity.decoration.ItemFrameEntity itemFrame = (net.minecraft.entity.decoration.ItemFrameEntity)entity;
                ItemStack heldItem = itemFrame.getHeldItemStack();
                if (!heldItem.isEmpty()) {
                    return heldItem;
                }
            }

            try {
                String entityName = entity.getType().getName().getString().toLowerCase().replace(" ", "_");
                Identifier spawnEggId = Identifier.of("minecraft", entityName + "_spawn_egg");
                Item spawnEgg = Registries.ITEM.get(spawnEggId);
                if (spawnEgg != null && spawnEgg != Items.AIR) {
                    return new ItemStack(spawnEgg);
                }
            } catch (Exception e) {

            }

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
        float scale = module.getRenderHudScale();

        float nominalIconSize = lineHeight * 2.0f / 1.1f;
        boolean noTarget = text.isEmpty() || text.contains("No target");

        String[] lines = text.split("\n");
        float maxTextWidth = 0.0f;
        for (String line : lines) {
            if (!line.isEmpty()) {
                maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, line, HUD_TEXT_SIZE));
            }
        }

        float baseHeight = noTarget ? 20.0f : lines.length * lineHeight + 2.0f;

        float effectiveIconSize = icon != null
                ? Math.max(8.0f, Math.min(nominalIconSize, baseHeight - 4.0f))
                : 0.0f;
        float iconOffset = icon != null ? effectiveIconSize + 3.0f : 0.0f;

        float baseWidth = iconOffset + maxTextWidth;

        baseWidth += 4.0f;

        baseWidth += 4.0f;

        if (noTarget) {
            baseWidth += 2.0f;
        } else {

            baseWidth += 2.0f;
        }

        boolean liftTop = !noTarget;
        if (liftTop) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0F, -4.0F);
        }

        renderRectHud(context, client, module, "", hudIndex,
                chatEditing, mouseX, mouseY, mouseDown, deltaSeconds, inverseGuiScale, screenWidth, screenHeight, screenCenterX, screenCenterY,
                baseWidth, baseHeight);

        float x = module.getHudX();
        float y = module.getHudY();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);

        float iconLocalX = 1.0f + 4.0f;

        if (icon != null) {
            float iconLocalY = (baseHeight - effectiveIconSize) * 0.5f;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(iconLocalX, iconLocalY);
            float iconDrawScale = effectiveIconSize / 16.0f;
            context.getMatrices().scale(iconDrawScale, iconDrawScale);

            context.drawItem(icon, 0, 0);

            context.getMatrices().popMatrix();
        }

        float totalTextHeight = lines.length * lineHeight;
        float verticalOffset = (baseHeight - totalTextHeight) * 0.5f;
        if (noTarget) {
            verticalOffset += 1.0f;
        } else {
            verticalOffset += 1.0f;
        }

        float textXOffset = iconOffset + 4.0f + 4.0f;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            renderScaledHudText(context, client, line, x, y, textXOffset, verticalOffset + (i * lineHeight), HUD_TEXT_SIZE, scale, true);
        }

        context.getMatrices().popMatrix();

        if (liftTop) {
            context.getMatrices().popMatrix();
        }
    }

    private void renderZoomLevel(DrawContext context, MinecraftClient client, float screenWidth, float screenHeight) {
        if (!Zoom.getInstance().isShowCurrentZoom() || !Zoom.isZoomActive()) {
            return;
        }

        String displayText = Zoom.getInstance().getFormattedZoomLevel();

        float textWidth = client.textRenderer.getWidth(displayText);
        float x = (screenWidth - textWidth) / 2.0f;
        float y = screenHeight - 50.0f;

        context.drawText(client.textRenderer, Text.literal(displayText), (int)x, (int)y, 0xFFFFFFFF, true);
    }

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private PlayerListHud playerListHud;

    @Unique private float phaze$satUnclampedFlashAlpha = 0f;
    @Unique private float phaze$satFlashAlpha = 0f;
    @Unique private byte phaze$satAlphaDir = 1;
    @Unique private final SaturationOffsetsCache phaze$satBarOffsets = new SaturationOffsetsCache();
    @Unique private final SaturationHeldFoodCache phaze$satHeldFood = new SaturationHeldFoodCache();
    @Unique private boolean phaze$bubblesLifted = false;
    @Unique private static final int PHAZE_BUBBLE_LIFT_PX = 10;

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

    @Unique private RenderPipeline phaze$hotbarLastPipeline;
    @Unique private boolean phaze$hotbarScissorOn = false;

    @Unique private boolean phaze$tabWasOpenedThisCycle = false;

    @Unique private static boolean phaze$pmWasMouseDown = false;

    @Unique private static final int PHAZE_INV_SLOT = 18;
    @Unique private static final int PHAZE_INV_ICON = 16;
    @Unique private static final int PHAZE_INV_BORDER = 7;
    @Unique private static final int PHAZE_INV_SLOT_OFFSET = 8;
    @Unique private static final Identifier PHAZE_INV_PANEL_SPRITE = Identifier.of("phaze", "shulker_box_tooltip");

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
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0F, -PHAZE_BUBBLE_LIFT_PX);
            phaze$bubblesLifted = true;
        }
    }

    @Inject(method = "renderAirBubbles", at = @At("RETURN"))
    private void phaze$liftAirBubblesReturn(DrawContext context, PlayerEntity player,
                                            int heartCount, int maxAirBubbles, int top,
                                            CallbackInfo ci) {
        if (phaze$bubblesLifted) {
            context.getMatrices().popMatrix();
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
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TextureHelper.FOOD_EMPTY_TEXTURE, x, y, iconSize, iconSize);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, foodTexture, x, y, iconSize, iconSize);
            } else {
                int hungerY = top;
                int v = 0;
                int u = 0;
                if (effectiveSaturationOfBar >= 1) u = 3 * iconSize;
                else if (effectiveSaturationOfBar > .5) u = 2 * iconSize;
                else if (effectiveSaturationOfBar > .25) u = 1 * iconSize;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, TextureHelper.MOD_ICONS, x, hungerY, u, v, iconSize, iconSize, 256, 256, alphaColor);
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
        float tickDelta = tickCounter.getTickProgress(false);
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

        int drawY = y + 1;

        context.drawText(mc.textRenderer, text, drawX, drawY, color, module.textShadow.isValue());
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void phaze$prepareHotbarSlideDraw(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        phaze$hotbarScissorOn = false;
    }

    @Unique
    private void phaze$advanceHotbarSlide() {
        phaze$hotbarShouldDrawMirror = false;
        phaze$hotbarMirrorOffsetX = 0;
        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) {
            phaze$hotbarLastSelected = -1;
            phaze$hotbarLastFrameNanos = 0L;
            ExordiumAnimationBridge.updateHotbarAnimation(0.0F, 0.0F, false, 0);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            ExordiumAnimationBridge.updateHotbarAnimation(0.0F, 0.0F, false, 0);
            return;
        }

        int selected = mc.player.getInventory().getSelectedSlot();
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
        ExordiumAnimationBridge.updateHotbarAnimation(
                phaze$hotbarCurrentSlotX,
                target,
                phaze$hotbarShouldDrawMirror,
                phaze$hotbarMirrorOffsetX
        );
    }

    @Inject(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1, shift = At.Shift.BEFORE)
    )
    private void phaze$openMirrorScissor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ExordiumAnimationBridge.isCapturingHotbar()) return;
        if (!phaze$hotbarShouldDrawMirror) return;
        int hotbarLeft = context.getScaledWindowWidth() / 2 - PHAZE_HOTBAR_BG_W / 2;
        int hotbarTop = context.getScaledWindowHeight() - PHAZE_HOTBAR_BG_H;
        context.enableScissor(hotbarLeft, hotbarTop, hotbarLeft + PHAZE_HOTBAR_BG_W, hotbarTop + PHAZE_HOTBAR_BG_H);
        phaze$hotbarScissorOn = true;
    }

    @ModifyArgs(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1)
    )
    private void phaze$slideSelectionX(Args args) {
        Animations module = Animations.getInstance();
        if (module == null || !module.isHotbarSlideEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        if (ExordiumAnimationBridge.isCapturingHotbar()) {
            ExordiumAnimationBridge.recordHotbarSelection(
                    args.<RenderPipeline>get(0),
                    args.<Identifier>get(1),
                    args.<Integer>get(2),
                    args.<Integer>get(3),
                    args.<Integer>get(4),
                    args.<Integer>get(5)
            );

            args.set(2, -10_000);
            phaze$hotbarShouldDrawMirror = false;
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
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
            phaze$hotbarLastPipeline = args.<RenderPipeline>get(0);
        }
    }

    @Inject(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1, shift = At.Shift.AFTER)
    )
    private void phaze$drawMirrorAndCloseScissor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!phaze$hotbarShouldDrawMirror || phaze$hotbarLastTexture == null || phaze$hotbarLastPipeline == null) {
            if (phaze$hotbarScissorOn) {
                context.disableScissor();
                phaze$hotbarScissorOn = false;
            }
            return;
        }
        int mirrorX = phaze$hotbarLastDrawX + phaze$hotbarMirrorOffsetX;
        context.drawGuiTexture(
                phaze$hotbarLastPipeline,
                phaze$hotbarLastTexture, mirrorX, phaze$hotbarLastDrawY,
                phaze$hotbarLastWidth, phaze$hotbarLastHeight);
        if (phaze$hotbarScissorOn) {
            context.disableScissor();
            phaze$hotbarScissorOn = false;
        }
        phaze$hotbarShouldDrawMirror = false;
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$drawHotbarUtilityHighlights(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MaceIndicator mace = MaceIndicator.getInstance();
        ItemHighlighter highlighter = ItemHighlighter.getInstance();
        HealingHelper healing = HealingHelper.getInstance();
        boolean maceEnabled = mace != null && mace.isEnabled();
        boolean highlighterEnabled = highlighter != null && highlighter.isEnabled();
        boolean healingEnabled = healing != null && healing.isEnabled();
        if (!maceEnabled && !highlighterEnabled && !healingEnabled) return;
        if (highlighterEnabled) highlighter.beginRenderPass();
        if (healingEnabled) healing.beginRenderPass();
        phaze$paintHotbarUtilityFills(
                context,
                maceEnabled ? mace : null,
                highlighterEnabled ? highlighter : null,
                healingEnabled ? healing : null
        );
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE",

                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V")
    )
    private Text phaze$hideOwnNickInSidebar(Text original) {
        NickHider hider = NickHider.getInstance();
        if (hider == null || !hider.isEnabled()) return original;
        return hider.rewrite(original);
    }

    @Inject(method = "render", at = @At("HEAD"))
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
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$tabSlideRenderTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (this.client != null && this.client.currentScreen instanceof MenuScreen) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        Animations module = Animations.getInstance();
        if (module == null || !module.isTabSlideEnabled()) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (this.client == null || this.client.options == null || this.client.options.hudHidden) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }

        boolean keyPressed = this.client.options.playerListKey.isPressed();
        if (keyPressed) {
            return;
        }
        if (!module.isTabSlideRendering(false)) {
            phaze$tabWasOpenedThisCycle = false;
            return;
        }
        if (!phaze$tabWasOpenedThisCycle) return;

        if (ExordiumAnimationBridge.renderClosingPlayerListFromCache()) {
            return;
        }

        this.playerListHud.setVisible(true);
        Scoreboard scoreboard = this.client.world == null ? null : this.client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null
                : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
        this.playerListHud.setVisible(false);
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

    @Unique
    private void phaze$paintHotbarUtilityFills(
            DrawContext context,
            MaceIndicator mace,
            ItemHighlighter highlighter,
            HealingHelper healing
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        PlayerEntity player = mc.player;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int centerX = screenW / 2;
        int itemY = screenH - 16 - 3;

        for (int n = 0; n < 9; n++) {

            ItemStack stack = player.getInventory().getMainStacks().get(n);
            int itemX = centerX - 90 + n * 20 + 2;
            if (mace != null) {
                phaze$fillHotbarOverlay(context, itemX, itemY, mace.colorForStack(stack));
            }
            if (highlighter != null) {
                phaze$fillHotbarOverlay(context, itemX, itemY, highlighter.colorForPreparedStack(stack));
            }
            if (healing != null) {
                phaze$fillHotbarOverlay(context, itemX, itemY, healing.colorForPreparedStack(stack));
            }
        }
        phaze$resetGuiRenderState();
    }

    @Unique
    private static void phaze$fillHotbarOverlay(DrawContext context, int x, int y, int color) {
        if ((color & 0xFF000000) != 0) {
            context.fill(x, y, x + 16, y + 16, color);
        }
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$flushHotbarBatch(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        phaze$resetGuiRenderState();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawPlayerModel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerModelHud module = PlayerModelHud.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        if (mc.currentScreen instanceof MenuScreen) return;
        if (mc.options.hudHidden) return;

        final int hudIndex = HUD_PLAYER_MODEL;
        ClientPlayerEntity player = mc.player;
        float baseSize = module.getBaseModelSize();
        module.setHudScale(MathHelper.clamp(module.getHudScale(), module.getMinHudScale(), module.getMaxHudScale()));
        float scale = module.getRenderHudScale();
        float panelW = 2.0F * baseSize * scale;
        float panelH = 2.0F * baseSize * scale;

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

        final float hoverInsetX = 7.0F;
        float hoverX = panelX + hoverInsetX;
        float hoverW = Math.max(12.0F, panelW - hoverInsetX * 2.0F);
        int handleSize = Math.max(4, Math.min(8, Math.round(4.0F * scale)));
        float handleX = hoverX + hoverW - handleSize / 2.0F;
        float handleY = panelY + panelH - handleSize / 2.0F;
        boolean hoveredHud = false;
        boolean hoveredHandle = false;
        boolean nearHud = false;

        if (chatEditing) {
            hoveredHud = isHovered(mouseX, mouseY, hoverX, panelY, hoverW, panelH);
            hoveredHandle = isHovered(mouseX, mouseY, handleX, handleY, handleSize, handleSize);
            nearHud = isNearRect(mouseX, mouseY, hoverX, panelY, hoverW, panelH, Math.max(12.0F, 10.0F * scale));

            if (!mouseDown) {
                RECT_DRAGGING[hudIndex] = false;
                RECT_RESIZING[hudIndex] = false;
            } else if (!phaze$pmWasMouseDown && !isAnyHudInteractionActive()) {
                if (hoveredHandle) {
                    RECT_RESIZING[hudIndex] = true;
                    RECT_RESIZE_START_WIDTH[hudIndex] = module.getHudScale();
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
                    float newScale = RECT_RESIZE_START_WIDTH[hudIndex] + (delta * 0.9F) / (baseSize * 4.0F);
                    newScale = snapAndAnnounceHudScale(module, newScale);
                    module.setHudScale(newScale);
                    scale = module.getRenderHudScale();
                    panelW = 2.0F * baseSize * scale;
                    panelH = 2.0F * baseSize * scale;
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
        } else {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
        }

        if (RECT_DRAGGING[hudIndex]) {
            float dragCenterX = panelX + panelW * 0.5F;
            float dragCenterY = panelY + panelH * 0.5F;
            if (Math.abs(dragCenterX - scaledScreenW * 0.5F) <= GUIDE_SNAP_RADIUS) {
                showVerticalGuideThisFrame = true;
            }
            if (Math.abs(dragCenterY - scaledScreenH * 0.5F) <= GUIDE_SNAP_RADIUS) {
                showHorizontalGuideThisFrame = true;
            }
        }

        float hoverTarget = chatEditing && hoveredHud ? 1.0F : 0.0F;

        RECT_HOVER_PROGRESS[hudIndex] = approachExp(
                RECT_HOVER_PROGRESS[hudIndex],
                hoverTarget,
                10.0F,
                cachedFrameDeltaSeconds
        );
        phaze$pmWasMouseDown = mouseDown;

        rememberHudSnapBounds(
                hudIndex,
                module,
                panelX * (float) scaleFactor,
                panelY * (float) scaleFactor,
                panelW * (float) scaleFactor,
                panelH * (float) scaleFactor
        );

        if (chatEditing) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        float centerX = panelX + panelW * 0.5F;

        Vector3f translation = new Vector3f(0.0F, 1.0F, 0.0F);
        Quaternionf bodyRotation;
        Quaternionf headRotation = null;

        String mode = module.mode.getSelected();
        if ("Follow Mouse".equalsIgnoreCase(mode) && !chatEditing) {
            float mx = (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
            float my = (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
            float dx = (centerX - mx) / 45.0F;
            float dy = ((panelY + panelH * 0.58F) - my) / 45.0F;
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI);
            headRotation = new Quaternionf().rotateX(dy * 20.0F * (float) (Math.PI / 180.0));
            bodyRotation.mul(new Quaternionf().rotateY(dx * 20.0F * (float) (Math.PI / 180.0)));
        } else if ("Auto Rotate".equalsIgnoreCase(mode)) {
            float speed = module.rotationSpeed.getValue();
            float angleDeg = (System.nanoTime() / 1_000_000_000.0F) * speed;
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI).rotateY(angleDeg * (float) (Math.PI / 180.0));
            headRotation = new Quaternionf();
        } else {
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI);
            headRotation = new Quaternionf();
        }

        EntityPose previousPose = player.getPose();
        EntityRenderState renderState;
        try {

            player.setPose(EntityPose.STANDING);
            renderState = phaze$captureEntityRenderState(player);
        } finally {
            player.setPose(previousPose);
        }
        context.addEntity(
                renderState,
                baseSize * scale,
                translation,
                bodyRotation,
                headRotation,
                Math.round(panelX),
                Math.round(panelY),
                Math.round(panelX + panelW),
                Math.round(panelY + panelH)
        );

        if (chatEditing && RECT_HOVER_PROGRESS[hudIndex] > 0.05F) {
            int outlineColor = withAlpha(0xFFFFFF, (int) (165.0F * RECT_HOVER_PROGRESS[hudIndex]));

            int pmOutlineThickness = Math.max(1, Math.round(BASE_HOVER_OUTLINE_THICKNESS / Math.max(1.0F, scale)));
            drawOuterOutline(context, hoverX, panelY, hoverW, panelH, pmOutlineThickness, outlineColor);
        }

        boolean showResizeHandle = chatEditing && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
        if (showResizeHandle) {
            context.getMatrices().pushMatrix();

            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);
            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, withAlpha(0xFFFFFF, 220));
            }
            context.getMatrices().popMatrix();
        }
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EntityRenderState phaze$captureEntityRenderState(LivingEntity entity) {
        EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        EntityRenderState state = renderer.getAndUpdateRenderState(entity, 1.0F);
        state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        state.shadowPieces.clear();
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        return state;
    }

    private void renderInventoryHud(
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
        boolean resized = RECT_LAYOUT_INITIALIZED[hudIndex]
                && (RECT_LAST_SCREEN_WIDTH[hudIndex] != currentScreenWidth
                || RECT_LAST_SCREEN_HEIGHT[hudIndex] != currentScreenHeight);
        if (resized && !RECT_DRAGGING[hudIndex] && !RECT_RESIZING[hudIndex]) {
            panelX = maxX <= 0.0F ? 0.0F : RECT_POSITION_RATIO_X[hudIndex] * maxX;
            panelY = maxY <= 0.0F ? 0.0F : RECT_POSITION_RATIO_Y[hudIndex] * maxY;
        }

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

        if (!chatEditing) {
            RECT_DRAGGING[hudIndex] = false;
            RECT_RESIZING[hudIndex] = false;
            RECT_HOVER_PROGRESS[hudIndex] = approachExp(RECT_HOVER_PROGRESS[hudIndex], 0.0F, 10.0F, deltaSeconds);
        } else {
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
                    float newScale = snapAndAnnounceHudScale(module, newWidth / baseWidth / 2.0F);

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

        if (chatEditing) {
            if (RECT_RESIZING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_HRESIZE, 4);
            } else if (RECT_DRAGGING[hudIndex]) {
                HudCursorRelay.defer(vorga.phazeclient.api.system.cursor.CursorManager.SHAPE_MOVE, 3);
            }
        }

        int hoverOutlineThickness = Math.max(1, Math.round(BASE_HOVER_OUTLINE_THICKNESS / Math.max(1.0F, scale)));

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(inverseGuiScale, inverseGuiScale);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(panelX, panelY);
        context.getMatrices().scale(scale, scale);

        context.drawGuiTexture(
                RenderPipelines.GUI_TEXTURED,
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
        context.getMatrices().popMatrix();

        if (chatEditing && RECT_HOVER_PROGRESS[hudIndex] > 0.05F) {
            int outlineColor = withAlpha(0xFFFFFF, (int) (175.0F * RECT_HOVER_PROGRESS[hudIndex]));
            drawOuterOutline(context, panelX, panelY, hudWidth, hudHeight, hoverOutlineThickness, outlineColor);
        }

        boolean showResizeHandle = chatEditing && (RECT_RESIZING[hudIndex] || hoveredHandle || hoveredHud || nearHud);
        if (showResizeHandle) {
            context.getMatrices().pushMatrix();

            int hX = Math.round(handleX);
            int hY = Math.round(handleY);
            int handleColor = RECT_RESIZING[hudIndex] ? withAlpha(0xFFFFFF, 255) : HANDLE_COLOR;
            context.fill(hX, hY, hX + handleSize, hY + handleSize, handleColor);
            if (hoveredHandle || RECT_RESIZING[hudIndex]) {
                drawOutlineNoOverlap(context, hX - 1, hY - 1, handleSize + 2, handleSize + 2, withAlpha(0xFFFFFF, 220));
            }
            context.getMatrices().popMatrix();
        }

        context.getMatrices().popMatrix();
    }

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

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void phaze$hideCrosshairInPhazeGui(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof MenuScreen) {
            ci.cancel();
        }
    }

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
