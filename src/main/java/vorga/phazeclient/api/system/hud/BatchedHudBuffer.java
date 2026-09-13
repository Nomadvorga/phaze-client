package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

public final class BatchedHudBuffer {
    public static final BatchedHudBuffer INSTANCE = new BatchedHudBuffer();

    private static final int CLEAR_ARGB = 0x00000000;

    private static final int CAPTURE_ACTIVE = 1;

    private SimpleFramebuffer fbo;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private long lastRefreshMs = 0L;
    private boolean dirty = true;
    private boolean hasContent = false;
    private int targetFps = 30;

    private Framebuffer realMainFramebuffer;

    private BatchedHudBuffer() {
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(1, Math.min(360, fps));
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

    public SimpleFramebuffer getActiveCaptureFramebuffer() {
        return (HudBuffer.activeCaptureTarget >= 0) ? fbo : null;
    }

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

                if (HudBuffer.activeCaptureFramebuffer == fbo) {
                    HudBuffer.activeCaptureFramebuffer = null;
                    HudBuffer.activeCaptureTarget = -1;
                }
                fbo.delete();
                fbo = null;
            }

            fbo = new SimpleFramebuffer("phaze/batched_hud", w, h, true);
            lastWidth = w;
            lastHeight = h;
            hasContent = false;
            dirty = true;
        }
        return true;
    }

    private boolean capturing;

    public boolean isCaptureActive() {
        return capturing;
    }

    public void beginCapture() {
        if (!ensureFramebuffer()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        realMainFramebuffer = mc != null ? mc.getFramebuffer() : null;

        clearFbo();

        HudBuffer.activeCaptureFramebuffer = fbo;
        HudBuffer.activeCaptureTarget = CAPTURE_ACTIVE;
        capturing = true;
    }

    private void clearFbo() {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (fbo.getDepthAttachment() != null) {
            encoder.clearColorAndDepthTextures(
                    fbo.getColorAttachment(), CLEAR_ARGB,
                    fbo.getDepthAttachment(), 1.0);
        } else {
            encoder.clearColorTexture(fbo.getColorAttachment(), CLEAR_ARGB);
        }
    }

    public void endCapture() {
        capturing = false;
        HudBuffer.activeCaptureTarget = -1;
        HudBuffer.activeCaptureFramebuffer = null;
        if (fbo == null) {
            return;
        }

        hasContent = true;
        dirty = false;
        lastRefreshMs = System.currentTimeMillis();
    }

    public void blit() {
        if (fbo == null || !hasContent) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) {
            return;
        }

        vorga.phazeclient.api.system.draw.ScreenBlit.blitOverMain(fbo);
    }

    public void cleanup() {

        if (HudBuffer.activeCaptureFramebuffer == fbo) {
            HudBuffer.activeCaptureFramebuffer = null;
            HudBuffer.activeCaptureTarget = -1;
        }
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
