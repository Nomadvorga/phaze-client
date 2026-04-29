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
    private final long[] hudStateKeys = new long[8];
    private final boolean[] hudStateInitialized = new boolean[8];

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

    public void renderCachedBatch(List<ShapeProperties> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getFramebuffer() == null) {
            return;
        }
        if (!prepareFramebuffers(client, true)) {
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

        if (!prepareFramebuffers(client, cacheFrame)) {
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

    private boolean prepareFramebuffers(MinecraftClient client, boolean cacheFrame) {
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

        boolean guiActive = client.currentScreen != null;
        boolean cameraMoved = hasCameraMoved(client);
        boolean shouldRefreshHudInput = !cacheFrame
                || forceHudRefresh
                || resized
                || guiActive
                || cameraMoved;

        // Cached HUD path: refresh only once per frame (beginCachedFrame resets the flag).
        if (cacheFrame && cachedFramePrepared) {
            shouldRefreshHudInput = false;
        }

        if (cacheFrame && cachedFramePrepared && !shouldRefreshHudInput) {
            return true;
        }

        if (shouldRefreshHudInput) {
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
            if (cacheFrame) {
                forceHudRefresh = false;
            }
        }

        if (cacheFrame) {
            cachedFramePrepared = true;
        }
        return true;
    }

    private boolean hasCameraMoved(MinecraftClient client) {
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return false;
        }
        var camera = client.gameRenderer.getCamera();
        var pos = camera.getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        boolean moved = Double.isNaN(lastCameraX)
                || Math.abs(pos.x - lastCameraX) > 1.0E-6
                || Math.abs(pos.y - lastCameraY) > 1.0E-6
                || Math.abs(pos.z - lastCameraZ) > 1.0E-6
                || Math.abs(yaw - lastYaw) > 1.0E-4f
                || Math.abs(pitch - lastPitch) > 1.0E-4f;

        lastCameraX = pos.x;
        lastCameraY = pos.y;
        lastCameraZ = pos.z;
        lastYaw = yaw;
        lastPitch = pitch;
        return moved;
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

        shader.getUniformOrDefault("Direction").set(directionX, directionY);
        shader.getUniformOrDefault("TexelSize").set(1.0F / source.textureWidth, 1.0F / source.textureHeight);
        shader.getUniformOrDefault("Support").set(support);
        shader.getUniformOrDefault("Sigma").set(sigma);
        shader.getUniformOrDefault("Brightness").set(1.0F);

        ShaderHelper.drawFullScreenQuad();
        // Ensure we finish writing to the target FBO for this pass
        target.endWrite();
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
