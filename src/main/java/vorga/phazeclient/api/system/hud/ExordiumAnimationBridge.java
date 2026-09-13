package vorga.phazeclient.api.system.hud;

import vorga.phazeclient.base.util.render.GuiMatrix;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
import java.util.function.Supplier;

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
    private static Method exordiumTextureCountSetMethod;
    private static boolean textureCountSetUnavailable;
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

    public static boolean deferBufferedComponent(Object buffer) {
        String id = COMPONENT_BY_BUFFER.get(buffer);
        if (id == null) {
            return false;
        }
        if (SCOREBOARD.equals(id) && shouldSuppressVanillaScoreboard()) {

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

    public static void renderDeferredComponents() {
        DrawContext context = frameContext;
        if (context == null || DEFERRED.isEmpty() || reflectionFailed) {
            DEFERRED.clear();
            return;
        }

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
        } finally {
            DEFERRED.clear();

        }
    }

    public static void recordHotbarSelection(
            RenderPipeline pipeline,
            Identifier texture,
            int x,
            int y,
            int width,
            int height
    ) {
        if (!isCapturingHotbar() || pipeline == null || texture == null) {
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

        int baseX = x - Math.round(hotbarTargetSlotX);
        hotbarSelector = new HotbarSelector(pipeline, texture, baseX, y, width, height, component, clip);
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
                selector.pipeline,
                selector.texture,
                drawX,
                selector.y,
                selector.width,
                selector.height
        );
        if (hotbarMirror) {
            context.drawGuiTexture(
                    selector.pipeline,
                    selector.texture,
                    drawX + hotbarMirrorOffset,
                    selector.y,
                    selector.width,
                    selector.height
            );
        }
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
        drawExordiumTexture(texture, matrix, animations.currentTabAlpha());
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

        try {
            renderPlayerList(context, texture);
            return true;
        } finally {

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
            if (model == null || shaderObject == null) {
                return;
            }

            setExordiumTextureCount(uniformObject, 1);
            exordiumModelDrawMethod.invoke(model, matrix);
        } catch (Throwable ignored) {
            reflectionFailed = true;
        }
    }

    private static void setExordiumTextureCount(Object uniform, int count) {
        if (uniform == null || textureCountSetUnavailable) {
            return;
        }
        try {
            if (exordiumTextureCountSetMethod == null) {
                exordiumTextureCountSetMethod = uniform.getClass().getMethod("set", int.class);
            }
            exordiumTextureCountSetMethod.invoke(uniform, count);
        } catch (Throwable ignored) {

            textureCountSetUnavailable = true;
        }
    }

    private static Matrix4f baseModelViewMatrix(DrawContext context) {
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        if (context != null) {
            matrix.mul(GuiMatrix.mat4(context.getMatrices()));
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
        Matrix4f matrix = GuiMatrix.mat4(context.getMatrices());
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
            RenderPipeline pipeline,
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
