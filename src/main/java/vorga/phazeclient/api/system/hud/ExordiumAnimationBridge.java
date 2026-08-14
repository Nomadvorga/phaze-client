package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardHud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Optional, reflection-backed bridge for Exordium 1.4.x.
 *
 * <p>Exordium already does the expensive part well: it renders each vanilla
 * HUD component into a framebuffer only when its data changes. The problem is
 * that a cached component's vanilla render method is skipped, so animations
 * implemented inside that method advance only at Exordium's component FPS.
 *
 * <p>This bridge keeps Exordium's framebuffer and invalidation logic intact.
 * During an active animation it removes only the affected cached component
 * from Exordium's multi-texture batch and draws that already-built texture with
 * a live transform. No player-list rebuilding, item rendering, text layout, or
 * chat wrapping is repeated per display frame.
 *
 * <p>There is intentionally no compile-time dependency on Exordium. All
 * external objects enter through optional pseudo-mixins and private fields are
 * resolved once. Without Exordium installed this class is just a few inert
 * state setters.
 */
public final class ExordiumAnimationBridge {
    public static final String HOTBAR = "minecraft:hotbar";
    public static final String PLAYER_LIST = "minecraft:player_list";
    public static final String CHAT = "minecraft:chat_panel";
    public static final String SCOREBOARD = "minecraft:scoreboard";

    private static final Map<Object, String> COMPONENT_BY_BUFFER = new IdentityHashMap<>();
    private static final Map<Object, String> ID_BY_INSTANCE = new IdentityHashMap<>();
    private static final Set<Object> LISTENER_REGISTERED =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<String> FORCE_CAPTURE =
            Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final LinkedHashMap<Object, String> DEFERRED = new LinkedHashMap<>();
    private static final ThreadLocal<String> CAPTURING = new ThreadLocal<>();

    private static Field instanceIdField;
    private static Field instanceBufferField;
    private static Field instancePacingField;
    private static Field instanceCapturingField;
    private static Method registerUpdateListenerMethod;
    private static Method pacingSetCooldownMethod;
    private static Method bufferTextureMethod;
    private static Field exordiumInstanceField;
    private static Method exordiumGetShaderManagerMethod;
    private static Method exordiumGetMultiTextureShaderMethod;
    private static Method exordiumGetTextureCountUniformMethod;
    private static Method exordiumGetModelMethod;
    private static Method exordiumModelDrawMethod;
    private static Object exordiumShaderManager;
    private static boolean reflectionFailed;

    private static DrawContext frameContext;
    private static boolean lastHotbarEnabled;
    private static boolean lastTabEnabled;
    private static boolean lastChatAnimationEnabled;
    private static boolean hotbarPrepared;
    private static boolean tabPrepared;
    private static boolean chatPrepared;

    private static Object playerListBuffer;
    private static HotbarSelector hotbarSelector;
    private static Rect tabBounds;
    private static float tabPivotX;
    private static float tabPivotY;
    private static boolean tabGeometryKnown;
    private static Rect chatBounds;
    private static Rect latestChatBounds;

    private static float hotbarCurrentSlotX;
    private static float hotbarTargetSlotX;
    private static boolean hotbarMirror;
    private static int hotbarMirrorOffset;

    private static float chatDx;
    private static float chatDy;
    private static float chatAlpha = 1.0F;
    private static boolean chatAnimationActive;

    private ExordiumAnimationBridge() {
    }

    public static void beginHudFrame(DrawContext context) {
        frameContext = context;
        DEFERRED.clear();

        Animations animations = Animations.getInstance();
        boolean hotbarEnabled = animations != null && animations.isHotbarSlideEnabled();
        boolean tabEnabled = animations != null && animations.isTabSlideEnabled();
        boolean chatEnabled = animations != null
                && (animations.isChatSmoothScrollEnabled() || animations.isChatFadeEnabled());

        if (hotbarEnabled && !lastHotbarEnabled) {
            hotbarPrepared = false;
            hotbarSelector = null;
            requestImmediateCapture(HOTBAR);
        }
        if (tabEnabled && !lastTabEnabled) {
            tabPrepared = false;
            requestImmediateCapture(PLAYER_LIST);
        }
        if (chatEnabled && !lastChatAnimationEnabled) {
            chatPrepared = false;
            requestImmediateCapture(CHAT);
        }

        lastHotbarEnabled = hotbarEnabled;
        lastTabEnabled = tabEnabled;
        lastChatAnimationEnabled = chatEnabled;
    }

    public static void updateHotbarAnimation(
            float currentSlotX,
            float targetSlotX,
            boolean mirror,
            int mirrorOffset
    ) {
        hotbarCurrentSlotX = currentSlotX;
        hotbarTargetSlotX = targetSlotX;
        hotbarMirror = mirror;
        hotbarMirrorOffset = mirrorOffset;
    }

    public static void updateChatAnimation(float dx, float dy, float alpha, boolean active) {
        chatDx = dx;
        chatDy = dy;
        chatAlpha = clamp01(alpha);
        chatAnimationActive = active || chatAlpha < 0.999F;
    }

    public static void requestImmediateCapture(String id) {
        if (id != null) {
            FORCE_CAPTURE.add(id);
        }
    }

    /**
     * Called at BufferInstance#renderBuffer HEAD. Registers a one-shot update
     * listener and releases Exordium's pacing cooldown only when Phaze needs a
     * clean, unanimated source texture.
     */
    public static void beforeBufferRender(Object instance, DrawContext context) {
        if (instance == null || reflectionFailed) {
            return;
        }
        frameContext = context;
        try {
            resolveInstanceReflection(instance.getClass());
            String id = readId(instance);
            Object buffer = instanceBufferField.get(instance);
            if (id == null || buffer == null) {
                return;
            }
            ID_BY_INSTANCE.put(instance, id);
            COMPONENT_BY_BUFFER.put(buffer, id);
            if (PLAYER_LIST.equals(id)) {
                playerListBuffer = buffer;
            }

            if (LISTENER_REGISTERED.add(instance)) {
                Supplier<Boolean> listener = () -> FORCE_CAPTURE.remove(id);
                registerUpdateListenerMethod.invoke(instance, listener);
            }

            if (HOTBAR.equals(id) && lastHotbarEnabled && !hotbarPrepared) {
                FORCE_CAPTURE.add(id);
            } else if (PLAYER_LIST.equals(id) && lastTabEnabled && !tabPrepared) {
                FORCE_CAPTURE.add(id);
            } else if (CHAT.equals(id) && lastChatAnimationEnabled && !chatPrepared) {
                FORCE_CAPTURE.add(id);
            }

            if (FORCE_CAPTURE.contains(id)) {
                Object pacing = instancePacingField.get(instance);
                if (pacing != null) {
                    if (pacingSetCooldownMethod == null) {
                        pacingSetCooldownMethod = pacing.getClass().getMethod("setCooldown", long.class);
                    }
                    pacingSetCooldownMethod.invoke(pacing, 0L);
                }
            }
        } catch (Throwable ignored) {
            reflectionFailed = true;
        }
    }

    /**
     * Called at BufferInstance#renderBuffer RETURN. A false return is not
     * sufficient to identify capture (disabled components also return false),
     * so the real private isCapturing flag is sampled.
     */
    public static void afterBufferRender(Object instance) {
        if (instance == null || reflectionFailed) {
            return;
        }
        try {
            resolveInstanceReflection(instance.getClass());
            if (!instanceCapturingField.getBoolean(instance)) {
                return;
            }
            String id = ID_BY_INSTANCE.get(instance);
            if (id == null) {
                id = readId(instance);
            }
            // A screen-size change can force capture before Exordium evaluates
            // update listeners. Consume the one-shot here as well so the next
            // frame does not perform a redundant second capture.
            FORCE_CAPTURE.remove(id);
            CAPTURING.set(id);
            if (HOTBAR.equals(id)) {
                hotbarSelector = null;
            } else if (PLAYER_LIST.equals(id)) {
                tabBounds = null;
                tabGeometryKnown = false;
            } else if (CHAT.equals(id)) {
                chatBounds = null;
                latestChatBounds = null;
            }
        } catch (Throwable ignored) {
            reflectionFailed = true;
        }
    }

    public static void afterBufferPostRender(Object instance) {
        String id = CAPTURING.get();
        if (HOTBAR.equals(id)) {
            hotbarPrepared = hotbarSelector != null;
        } else if (PLAYER_LIST.equals(id)) {
            tabPrepared = tabGeometryKnown;
        } else if (CHAT.equals(id)) {
            chatPrepared = chatBounds != null;
        }
        CAPTURING.remove();
    }

    public static boolean isCapturing(String id) {
        return id != null && id.equals(CAPTURING.get());
    }

    public static boolean isCapturingHotbar() {
        return isCapturing(HOTBAR) && lastHotbarEnabled;
    }

    public static boolean isCapturingPlayerList() {
        return isCapturing(PLAYER_LIST) && lastTabEnabled;
    }

    public static boolean isCapturingChat() {
        return isCapturing(CHAT) && lastChatAnimationEnabled;
    }

    /**
     * Called from BufferedComponent#renderBuffer HEAD. Returning true removes
     * this one cached texture from Exordium's delayed multi-texture batch; it
     * will be drawn after that batch with a live transform.
     */
    public static boolean deferBufferedComponent(Object buffer) {
        String id = COMPONENT_BY_BUFFER.get(buffer);
        if (id == null) {
            return false;
        }
        if (SCOREBOARD.equals(id) && shouldSuppressVanillaScoreboard()) {
            // Exordium can serve an older cached vanilla sidebar without
            // entering InGameHud#renderScoreboardSidebar, so the regular
            // cancellable mixin never gets a chance to hide it. Dropping just
            // this cached component keeps the custom Phaze scoreboard as the
            // only visible sidebar while every other Exordium buffer remains
            // batched normally.
            return true;
        }

        boolean defer;
        if (HOTBAR.equals(id)) {
            defer = lastHotbarEnabled && hotbarSelector != null;
        } else if (PLAYER_LIST.equals(id)) {
            defer = lastTabEnabled && tabPrepared && isTabVisuallyAnimated();
        } else if (CHAT.equals(id)) {
            defer = lastChatAnimationEnabled
                    && chatPrepared
                    && chatAnimationActive
                    && latestChatBounds != null;
        } else {
            defer = false;
        }

        if (defer) {
            DEFERRED.put(buffer, id);
        }
        return defer;
    }

    /**
     * Called after Exordium has drawn its normal batch. Only cached textures
     * collected by {@link #deferBufferedComponent(Object)} are submitted.
     */
    public static void renderDeferredComponents() {
        DrawContext context = frameContext;
        if (context == null || DEFERRED.isEmpty() || reflectionFailed) {
            DEFERRED.clear();
            return;
        }

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        try {
            for (Map.Entry<Object, String> entry : DEFERRED.entrySet()) {
                int texture;
                try {
                    texture = getTextureId(entry.getKey());
                } catch (Throwable ignored) {
                    continue;
                }
                if (texture <= 0) {
                    continue;
                }
                switch (entry.getValue()) {
                    case HOTBAR -> renderHotbar(context, texture);
                    case PLAYER_LIST -> renderPlayerList(context, texture);
                    case CHAT -> renderChat(context, texture);
                    default -> {
                    }
                }
            }
            context.draw();
        } finally {
            DEFERRED.clear();
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // This hook runs immediately before Exordium's own
            // MultiStateHolder#apply. Let that captured state restore
            // blend/depth exactly as they were before the delayed HUD pass.
            // Unconditionally disabling blend here poisoned the next
            // component capture and baked opaque black TAB/chat backgrounds
            // into their cached framebuffers.
        }
    }

    public static void recordHotbarSelection(
            Function<Identifier, RenderLayer> layerFactory,
            Identifier texture,
            int x,
            int y,
            int width,
            int height
    ) {
        if (!isCapturingHotbar() || layerFactory == null || texture == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        float screenWidth = client.getWindow().getScaledWidth();
        float screenHeight = client.getWindow().getScaledHeight();
        float hotbarLeft = screenWidth * 0.5F - 91.0F;
        Rect component = new Rect(
                Math.max(0.0F, hotbarLeft - 36.0F),
                Math.max(0.0F, screenHeight - 42.0F),
                Math.min(screenWidth, hotbarLeft + 182.0F + 36.0F),
                screenHeight
        );
        Rect clip = new Rect(hotbarLeft, screenHeight - 22.0F, hotbarLeft + 182.0F, screenHeight);
        // Keep an absolute slot-zero origin. The cached selector may have
        // been captured for the previous selected slot because Exordium
        // intentionally rate-limits hotbar refreshes. Applying
        // (current-target) to that stale X causes a one-frame jump in the
        // wrong direction. The slot-zero origin stays valid across selection
        // changes, so only Phaze's display-FPS animation position is added.
        int baseX = x - Math.round(hotbarTargetSlotX);
        hotbarSelector = new HotbarSelector(layerFactory, texture, baseX, y, width, height, component, clip);
    }

    public static void recordTabGeometry(float pivotX, float pivotY) {
        if (!isCapturingPlayerList()) {
            return;
        }
        tabPivotX = pivotX;
        tabPivotY = pivotY;
        tabGeometryKnown = true;
    }

    public static void recordTabElement(
            DrawContext context,
            float x1,
            float y1,
            float x2,
            float y2
    ) {
        if (!isCapturingPlayerList()) {
            return;
        }
        tabBounds = union(tabBounds, transformRect(context, x1, y1, x2, y2));
    }

    public static void recordChatElement(
            DrawContext context,
            float x1,
            float y1,
            float x2,
            float y2,
            boolean latest
    ) {
        if (!isCapturingChat()) {
            return;
        }
        Rect rect = transformRect(context, x1, y1, x2, y2);
        chatBounds = union(chatBounds, rect);
        if (latest) {
            latestChatBounds = union(latestChatBounds, rect);
        }
    }

    private static void renderHotbar(DrawContext context, int texture) {
        HotbarSelector selector = hotbarSelector;
        if (selector == null) {
            return;
        }
        Matrix4f matrix = baseModelViewMatrix(context);
        drawExordiumTexture(texture, matrix, 1.0F);

        int drawX = selector.baseX + Math.round(hotbarCurrentSlotX);
        context.enableScissor(
                Math.round(selector.clipBounds.left),
                Math.round(selector.clipBounds.top),
                Math.round(selector.clipBounds.right),
                Math.round(selector.clipBounds.bottom)
        );
        context.drawGuiTexture(
                selector.layerFactory,
                selector.texture,
                drawX,
                selector.y,
                selector.width,
                selector.height
        );
        if (hotbarMirror) {
            context.drawGuiTexture(
                    selector.layerFactory,
                    selector.texture,
                    drawX + hotbarMirrorOffset,
                    selector.y,
                    selector.width,
                    selector.height
            );
        }
        context.draw();
        context.disableScissor();
    }

    private static void renderPlayerList(DrawContext context, int texture) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        Animations animations = Animations.getInstance();
        if (animations == null) {
            return;
        }

        Matrix4f matrix = baseModelViewMatrix(context);
        if (animations.isTabSlideStyle()) {
            matrix.translate(0.0F, animations.currentTabSlideOffset(), 0.0F);
        } else {
            float scale = Math.max(0.01F, animations.currentTabProgress());
            matrix.translate(tabPivotX, tabPivotY, 0.0F);
            matrix.scale(scale, scale, 1.0F);
            matrix.translate(-tabPivotX, -tabPivotY, 0.0F);
        }
        // Exordium stores the whole player list in one texture, so applying
        // animation alpha here also fades its vanilla background. Keep the
        // cached texture at native opacity; movement/scale still uses the same
        // single cached draw and therefore has no additional FPS cost.
        drawExordiumTexture(texture, matrix, 1.0F);
    }

    private static void renderChat(DrawContext context, int texture) {
        Rect component = chatBounds;
        Rect latest = latestChatBounds;
        if (component == null || latest == null) {
            return;
        }

        Matrix4f baseMatrix = baseModelViewMatrix(context);
        drawTextureMinusHole(context, texture, component, latest, baseMatrix);

        Rect destination = latest.offset(chatDx, chatDy);
        Rect clippedDestination = intersection(destination, component);
        if (clippedDestination != null) {
            Matrix4f movedMatrix = new Matrix4f(baseMatrix).translate(chatDx, chatDy, 0.0F);
            drawExordiumTextureClipped(context, texture, movedMatrix, chatAlpha, clippedDestination);
        }
    }

    private static boolean isTabVisuallyAnimated() {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isTabSlideEnabled()) {
            return false;
        }
        if (animations.isTabSlideStyle()) {
            return Math.abs(animations.currentTabSlideOffset()) > 0.01F
                    || animations.currentTabAlpha() < 0.999F;
        }
        return animations.currentTabProgress() < 0.999F
                || animations.currentTabAlpha() < 0.999F;
    }

    /**
     * Draws the already-captured Exordium player-list texture during Phaze's
     * close animation. This avoids calling PlayerListHud#render every display
     * frame after the key is released, which is especially expensive on
     * servers with large player lists.
     */
    public static boolean renderClosingPlayerListFromCache() {
        DrawContext context = frameContext;
        Object buffer = playerListBuffer;
        if (context == null
                || buffer == null
                || reflectionFailed
                || !lastTabEnabled
                || !tabPrepared) {
            return false;
        }

        int texture;
        try {
            texture = getTextureId(buffer);
        } catch (Throwable ignored) {
            return false;
        }
        if (texture <= 0) {
            return false;
        }

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            renderPlayerList(context, texture);
            context.draw();
            return true;
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            // Player-list close can run outside Exordium's delayed pass.
            // Leave GUI blending enabled for subsequent HUD captures instead
            // of leaking a disabled blend state into the next frame.
            RenderSystem.enableBlend();
        }
    }

    private static boolean shouldSuppressVanillaScoreboard() {
        ScoreboardHud scoreboard = ScoreboardHud.getInstance();
        if (scoreboard != null && scoreboard.isEnabled()) {
            return true;
        }
        NoRender noRender = NoRender.getInstance();
        return noRender != null && noRender.isEnabled() && noRender.scoreboard.isValue();
    }

    private static void drawTextureMinusHole(
            DrawContext context,
            int texture,
            Rect component,
            Rect hole,
            Matrix4f matrix
    ) {
        Rect clippedHole = intersection(component, hole);
        if (clippedHole == null) {
            drawExordiumTextureClipped(context, texture, matrix, 1.0F, component);
            return;
        }

        drawExordiumTextureClipped(context, texture, matrix, 1.0F,
                new Rect(component.left, component.top, component.right, clippedHole.top));
        drawExordiumTextureClipped(context, texture, matrix, 1.0F,
                new Rect(component.left, clippedHole.bottom, component.right, component.bottom));
        drawExordiumTextureClipped(context, texture, matrix, 1.0F,
                new Rect(component.left, clippedHole.top, clippedHole.left, clippedHole.bottom));
        drawExordiumTextureClipped(context, texture, matrix, 1.0F,
                new Rect(clippedHole.right, clippedHole.top, component.right, clippedHole.bottom));
    }

    /**
     * Uses Exordium's own full-screen model and shader for transformed cache
     * draws. Its framebuffer textures are authored for that exact
     * premultiplied-alpha path. Sampling a cropped quad with Minecraft's
     * generic POSITION_TEX_COLOR program made the transparent clear area
     * behave as opaque black on some drivers, which was visible while TAB or
     * a new chat row was moving. Scissoring the proven Exordium pass keeps the
     * cached rendering and only changes its display transform.
     */
    private static void drawExordiumTextureClipped(
            DrawContext context,
            int texture,
            Matrix4f matrix,
            float alpha,
            Rect clip
    ) {
        if (clip == null || clip.isEmpty() || alpha <= 0.001F) {
            return;
        }
        context.enableScissor(
                (int) Math.floor(clip.left),
                (int) Math.floor(clip.top),
                (int) Math.ceil(clip.right),
                (int) Math.ceil(clip.bottom)
        );
        try {
            drawExordiumTexture(texture, matrix, alpha);
        } finally {
            context.disableScissor();
        }
    }

    private static void drawExordiumTexture(int texture, Matrix4f matrix, float alpha) {
        if (texture <= 0 || matrix == null || alpha <= 0.001F || reflectionFailed) {
            return;
        }
        try {
            resolveExordiumRendererReflection();
            Object model = exordiumGetModelMethod.invoke(null);
            Object shaderObject = exordiumGetMultiTextureShaderMethod.invoke(exordiumShaderManager);
            Object uniformObject = exordiumGetTextureCountUniformMethod.invoke(exordiumShaderManager);
            if (!(shaderObject instanceof ShaderProgram shader)
                    || !(uniformObject instanceof GlUniform textureCount)
                    || model == null) {
                return;
            }

            RenderSystem.setShader(shader);
            textureCount.set(1);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SrcFactor.ONE,
                    GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
            );
            exordiumModelDrawMethod.invoke(model, matrix);
        } catch (Throwable ignored) {
            reflectionFailed = true;
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static Matrix4f baseModelViewMatrix(DrawContext context) {
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        if (context != null) {
            matrix.mul(context.getMatrices().peek().getPositionMatrix());
        }
        return matrix;
    }

    private static Rect transformRect(
            DrawContext context,
            float x1,
            float y1,
            float x2,
            float y2
    ) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Vector4f p1 = matrix.transform(new Vector4f(x1, y1, 0.0F, 1.0F));
        Vector4f p2 = matrix.transform(new Vector4f(x2, y1, 0.0F, 1.0F));
        Vector4f p3 = matrix.transform(new Vector4f(x2, y2, 0.0F, 1.0F));
        Vector4f p4 = matrix.transform(new Vector4f(x1, y2, 0.0F, 1.0F));
        float left = (float) Math.floor(Math.min(Math.min(p1.x, p2.x), Math.min(p3.x, p4.x)));
        float top = (float) Math.floor(Math.min(Math.min(p1.y, p2.y), Math.min(p3.y, p4.y)));
        float right = (float) Math.ceil(Math.max(Math.max(p1.x, p2.x), Math.max(p3.x, p4.x)));
        float bottom = (float) Math.ceil(Math.max(Math.max(p1.y, p2.y), Math.max(p3.y, p4.y)));
        return new Rect(left, top, right, bottom);
    }

    private static Rect union(Rect first, Rect second) {
        if (second == null || second.isEmpty()) {
            return first;
        }
        if (first == null || first.isEmpty()) {
            return second;
        }
        return new Rect(
                Math.min(first.left, second.left),
                Math.min(first.top, second.top),
                Math.max(first.right, second.right),
                Math.max(first.bottom, second.bottom)
        );
    }

    private static Rect intersection(Rect first, Rect second) {
        float left = Math.max(first.left, second.left);
        float top = Math.max(first.top, second.top);
        float right = Math.min(first.right, second.right);
        float bottom = Math.min(first.bottom, second.bottom);
        if (right <= left || bottom <= top) {
            return null;
        }
        return new Rect(left, top, right, bottom);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int getTextureId(Object buffer) throws Exception {
        if (bufferTextureMethod == null) {
            bufferTextureMethod = buffer.getClass().getMethod("getTextureId");
        }
        return ((Number) bufferTextureMethod.invoke(buffer)).intValue();
    }

    private static String readId(Object instance) throws Exception {
        Object value = instanceIdField.get(instance);
        return value == null ? null : value.toString();
    }

    private static void resolveInstanceReflection(Class<?> type) throws Exception {
        if (instanceIdField != null) {
            return;
        }
        instanceIdField = accessible(type.getDeclaredField("id"));
        instanceBufferField = accessible(type.getDeclaredField("buffer"));
        instancePacingField = accessible(type.getDeclaredField("pacing"));
        instanceCapturingField = accessible(type.getDeclaredField("isCapturing"));
        registerUpdateListenerMethod = type.getMethod("registerUpdateListener", Supplier.class);
    }

    private static void resolveExordiumRendererReflection() throws Exception {
        if (exordiumModelDrawMethod != null && exordiumShaderManager != null) {
            return;
        }

        Class<?> baseType = Class.forName("dev.tr7zw.exordium.ExordiumModBase");
        exordiumInstanceField = baseType.getField("instance");
        Object exordium = exordiumInstanceField.get(null);
        exordiumGetShaderManagerMethod = baseType.getMethod("getCustomShaderManager");
        exordiumShaderManager = exordiumGetShaderManagerMethod.invoke(exordium);

        Class<?> shaderManagerType = exordiumShaderManager.getClass();
        exordiumGetMultiTextureShaderMethod =
                shaderManagerType.getMethod("getPositionMultiTexShader");
        exordiumGetTextureCountUniformMethod =
                shaderManagerType.getMethod("getPositionMultiTexTextureCountUniform");

        Class<?> bufferedComponentType =
                Class.forName("dev.tr7zw.exordium.render.BufferedComponent");
        exordiumGetModelMethod = bufferedComponentType.getMethod("getModel");
        Class<?> modelType = Class.forName("dev.tr7zw.exordium.render.Model");
        exordiumModelDrawMethod = modelType.getMethod("draw", Matrix4f.class);
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T object) {
        object.setAccessible(true);
        return object;
    }

    private record Rect(float left, float top, float right, float bottom) {
        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }

        boolean isEmpty() {
            return width() <= 0.0F || height() <= 0.0F;
        }

        Rect offset(float x, float y) {
            return new Rect(left + x, top + y, right + x, bottom + y);
        }
    }

    private record HotbarSelector(
            Function<Identifier, RenderLayer> layerFactory,
            Identifier texture,
            int baseX,
            int y,
            int width,
            int height,
            Rect componentBounds,
            Rect clipBounds
    ) {
    }
}
