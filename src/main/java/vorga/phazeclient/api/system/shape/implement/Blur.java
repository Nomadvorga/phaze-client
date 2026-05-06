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

    // Cached blur kernel weights for common blur radii (optimization)
    private static final java.util.Map<Integer, float[]> BLUR_KERNEL_CACHE = new java.util.HashMap<>();
    private static final int MAX_CACHED_KERNELS = 32;

    private final DrawEngineImpl drawEngine = new DrawEngineImpl();
    private Framebuffer input;
    private Framebuffer ping;
    private Framebuffer pong;
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

    public void beginCachedFrame() {
        cachedFramePrepared = false;
        hudBatchMode = true;
        hudBatchStateApplied = false;
        hudBatchMaskShader = null;
    }

    public void endCachedFrame() {
        if (hudBatchStateApplied) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
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
        long now = System.nanoTime();
        boolean guiActive = client.currentScreen != null;
        boolean cameraMoved = hasCameraMoved(client);
        boolean resized = framebufferWidth != lastWorldCaptureWidth || framebufferHeight != lastWorldCaptureHeight;
        boolean shouldRefreshCapture = !worldCaptureInitialized
                || resized
                || guiActive
                || cameraMoved;
        if (shouldRefreshCapture) {
            captureWorldInput(client, framebufferWidth, framebufferHeight);
            worldCaptureInitialized = true;
            lastWorldCaptureNs = now;
            lastWorldCaptureWidth = framebufferWidth;
            lastWorldCaptureHeight = framebufferHeight;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawEngine.quad(matrix, buffer, x, y, width, height, color);

        RenderSystem.setShaderTexture(0, input.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        if (shader != null) {
            Theme theme = Theme.getInstance();
            int blurMode = theme.getHudBlurMode();
            // Use Kawase blur for better performance (blurMode = 1 is Kawase)
            if (blurMode == 2) {
                blurMode = 1;
            }
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
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public void renderCachedBatch(List<ShapeProperties> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        if (!prepareFramebuffers(client, true, true)) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        RenderSystem.setShaderTexture(0, input.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        hudBatchMaskShader = shader;
        if (shader == null) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            return;
        }

        for (ShapeProperties shape : shapes) {
            if (shape == null) {
                continue;
            }
            renderPreparedShapeWithBoundShader(shape, shader);
        }

        int target = HudBuffer.activeCaptureTarget >= 0 ? HudBuffer.activeCaptureTarget : client.getFramebuffer().fbo;
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderPreparedShapeWithBoundShader(ShapeProperties shape, ShaderProgram shader) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || input == null || shader == null) {
            return;
        }

        Theme theme = Theme.getInstance();
        int blurMode = theme.getHudBlurMode();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        if (blurMode == 2) {
            blurMode = 1; // fixed "Kawase" preset uses smooth Soup kernel
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
        shader.getUniformOrDefault("BlurRadius").set(blurRadius);
        shader.getUniformOrDefault("BlurMode").set(blurMode);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void render(ShapeProperties shape, boolean cacheFrame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }

        if (!prepareFramebuffers(client, cacheFrame, true)) {
            return;
        }

        Theme theme = Theme.getInstance();
        int blurMode = theme.getHudBlurMode();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        if (blurMode == 2) {
            blurMode = 1; // fixed "Kawase" preset uses smooth Soup kernel
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

        if (!renderPreparedShape(shape)) {
            if (!useHudBatch) {
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }
            return;
        }

        if (useHudBatch) {
            int target = HudBuffer.activeCaptureTarget >= 0 ? HudBuffer.activeCaptureTarget : client.getFramebuffer().fbo;
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
        }

        if (!useHudBatch) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private boolean renderPreparedShape(ShapeProperties shape) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || input == null) {
            return false;
        }

        Theme theme = Theme.getInstance();
        int blurMode = theme.getHudBlurMode();
        float blurRadius = Math.max(0.0F, shape.getQuality()) * theme.getHudBlurRadiusMultiplier();
        if (blurMode == 2) {
            blurMode = 1; // fixed "Kawase" preset uses smooth Soup kernel
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

        RenderSystem.setShaderTexture(0, input.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(MASK_SHADER_KEY);
        hudBatchMaskShader = shader;
        if (shader == null) {
            return false;
        }
        shader.getUniformOrDefault("Size").set(width, height);
        shader.getUniformOrDefault("Radius").set(round);
        shader.getUniformOrDefault("Smoothness").set(softness);
        shader.getUniformOrDefault("BlurRadius").set(blurRadius);
        shader.getUniformOrDefault("BlurMode").set(blurMode);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        return true;
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
            resized = true;
        } else if (input.textureWidth != framebufferWidth || input.textureHeight != framebufferHeight) {
            input.resize(framebufferWidth, framebufferHeight);
            input.setTexFilter(GL11C.GL_LINEAR);
            ping.resize(framebufferWidth, framebufferHeight);
            ping.setTexFilter(GL11C.GL_LINEAR);
            pong.resize(framebufferWidth, framebufferHeight);
            pong.setTexFilter(GL11C.GL_LINEAR);
            resized = true;
        }

        if (input == null || ping == null || pong == null) {
            return false;
        }

        if (cacheFrame) {
            boolean guiActive = client.currentScreen != null;
            boolean cameraMoved = hasCameraMoved(client);
            boolean shouldRefreshHudInput = forceHudRefresh || resized || guiActive || cameraMoved;

            // Disable cached frame optimization to prevent flickering
            if (shouldRefreshHudInput) {
                captureWorldInput(client, framebufferWidth, framebufferHeight);
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

        runGaussianPass(shader, input, ping, 1.0F, 0.0F, blurRadius);
        runGaussianPass(shader, ping, pong, 0.0F, 1.0F, blurRadius);
        bindMainDrawTarget(client);
        return true;
    }

    private void runGaussianPass(ShaderProgram shader, Framebuffer source, Framebuffer target, float directionX, float directionY, float blurRadius) {
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

        // Use cached kernel weights if available (optimization)
        int cacheKey = (int)(blurRadius * 100);
        float[] cachedWeights = getCachedBlurKernel(cacheKey, support, sigma);

        shader.getUniformOrDefault("Direction").set(directionX, directionY);
        shader.getUniformOrDefault("TexelSize").set(1.0F / source.textureWidth, 1.0F / source.textureHeight);
        shader.getUniformOrDefault("Support").set(support);
        shader.getUniformOrDefault("Sigma").set(sigma);
        shader.getUniformOrDefault("Brightness").set(1.0F);

        ShaderHelper.drawFullScreenQuad();
        // Ensure we finish writing to the target FBO for this pass
        target.endWrite();
    }

    private static float[] getCachedBlurKernel(int cacheKey, int support, float sigma) {
        // Check cache first
        if (BLUR_KERNEL_CACHE.containsKey(cacheKey)) {
            return BLUR_KERNEL_CACHE.get(cacheKey);
        }

        // Compute Gaussian weights
        float[] weights = new float[support];
        float sum = 0.0f;
        for (int i = 0; i < support; i++) {
            float x = i / sigma;
            weights[i] = (float) Math.exp(-0.5 * x * x);
            sum += weights[i] * (i == 0 ? 1 : 2);
        }
        // Normalize
        for (int i = 0; i < support; i++) {
            weights[i] /= sum;
        }

        // Cache it (with size limit)
        if (BLUR_KERNEL_CACHE.size() < MAX_CACHED_KERNELS) {
            BLUR_KERNEL_CACHE.put(cacheKey, weights);
        }

        return weights;
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
}
