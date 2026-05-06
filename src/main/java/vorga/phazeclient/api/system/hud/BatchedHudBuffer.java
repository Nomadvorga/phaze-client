package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11C;

/**
 * Single global FBO that captures all of our 2D HUD rendering and blits the
 * cached texture onto the main framebuffer. Modeled after Exordium's
 * BufferedComponent: one capture per refresh interval, blit cached texture on
 * skipped frames. Uses {@link HudBuffer#activeCaptureTarget} as the global flag
 * so existing render hooks (e.g. {@code Blur}) automatically redirect their
 * writes into this FBO without modification.
 */
public final class BatchedHudBuffer {
    public static final BatchedHudBuffer INSTANCE = new BatchedHudBuffer();

    private SimpleFramebuffer fbo;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private long lastRefreshMs = 0L;
    private boolean dirty = true;
    private boolean hasContent = false;
    private int targetFps = 30;
    /**
     * Cached real main framebuffer reference, snapshot at the start of
     * {@link #beginCapture()} BEFORE the redirect mixin activates. Used by
     * code (e.g. {@code Blur#captureWorldInput}) that must read the actual
     * world framebuffer rather than our HUD capture FBO.
     */
    private Framebuffer realMainFramebuffer;

    private BatchedHudBuffer() {
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(1, Math.min(240, fps));
    }

    public int getTargetFps() {
        return targetFps;
    }

    public void invalidate() {
        dirty = true;
    }

    public boolean hasContent() {
        return hasContent;
    }

    /**
     * Returns the active capture framebuffer if a capture is currently in
     * progress, otherwise {@code null}. Used by the {@code MinecraftClient.getFramebuffer}
     * mixin to redirect render-layer framebuffer rebinds into our FBO during
     * HUD capture (Exordium-style {@code Minecraft#getMainRenderTarget} redirect).
     */
    public SimpleFramebuffer getActiveCaptureFramebuffer() {
        return (HudBuffer.activeCaptureTarget >= 0) ? fbo : null;
    }

    /**
     * Returns the real main framebuffer (bypassing the
     * {@link MinecraftClient#getFramebuffer()} redirect mixin). Returns
     * {@code null} if no capture has been performed yet. Intended for code
     * that must read the actual world content while a HUD batch capture is
     * in progress.
     */
    public Framebuffer getRealMainFramebuffer() {
        return realMainFramebuffer;
    }

    public boolean shouldRefresh(boolean force) {
        if (force || dirty || !hasContent) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastRefreshMs;
        return elapsed >= (1000L / targetFps);
    }

    /**
     * Allocates / resizes the framebuffer if the window size changed. Returns
     * {@code true} if the framebuffer is ready to use, {@code false} on invalid
     * window state.
     */
    public boolean ensureFramebuffer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }

        if (fbo == null || w != lastWidth || h != lastHeight) {
            if (fbo != null) {
                fbo.delete();
                fbo = null;
            }
            fbo = new SimpleFramebuffer(w, h, true);
            fbo.setTexFilter(GL11C.GL_LINEAR);
            lastWidth = w;
            lastHeight = h;
            hasContent = false;
            dirty = true;
        }
        return true;
    }

    public void beginCapture() {
        if (!ensureFramebuffer()) {
            return;
        }
        // Snapshot REAL main framebuffer BEFORE enabling redirect flag.
        // Once activeCaptureTarget >= 0, MinecraftClientFramebufferMixin
        // returns our FBO from mc.getFramebuffer(), which would break any
        // code that needs to read the actual world (e.g. Blur world capture).
        MinecraftClient mc = MinecraftClient.getInstance();
        realMainFramebuffer = mc != null ? mc.getFramebuffer() : null;

        fbo.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        fbo.clear();
        fbo.beginWrite(false);
        HudBuffer.activeCaptureTarget = fbo.fbo;
    }

    public void endCapture() {
        if (fbo == null) {
            HudBuffer.activeCaptureTarget = -1;
            return;
        }
        HudBuffer.activeCaptureTarget = -1;
        fbo.endWrite();
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
        hasContent = true;
        dirty = false;
        lastRefreshMs = System.currentTimeMillis();
    }

    /**
     * Blits the cached HUD texture onto the main framebuffer using
     * premultiplied alpha. Safe to call every frame.
     */
    public void blit() {
        if (fbo == null || !hasContent) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) {
            return;
        }

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        mc.getFramebuffer().beginWrite(false);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager._viewport(0, 0, width, height);

        ShaderProgram shader = RenderSystem.setShader(ShaderProgramKeys.BLIT_SCREEN);
        shader.addSamplerTexture("InSampler", fbo.getColorAttachment());

        BufferBuilder bb = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.BLIT_SCREEN
        );
        bb.vertex(0.0F, 0.0F, 0.0F);
        bb.vertex(1.0F, 0.0F, 0.0F);
        bb.vertex(1.0F, 1.0F, 0.0F);
        bb.vertex(0.0F, 1.0F, 0.0F);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();

        mc.getFramebuffer().beginWrite(true);
    }

    public void cleanup() {
        if (fbo != null) {
            fbo.delete();
            fbo = null;
        }
        hasContent = false;
        dirty = true;
        lastWidth = -1;
        lastHeight = -1;
    }
}
