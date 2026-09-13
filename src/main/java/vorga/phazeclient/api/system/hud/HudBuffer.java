package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import vorga.phazeclient.api.system.draw.ScreenBlit;

public class HudBuffer {

    private static final int CLEAR_ARGB = 0x00000000;

    public static final int CAPTURE_ACTIVE = 1;

    public static volatile int activeCaptureTarget = -1;

    public static volatile Framebuffer activeCaptureFramebuffer = null;

    private SimpleFramebuffer framebuffer;
    private long lastRenderTimeMs = 0;
    private long lastLogicUpdateTimeMs = 0;
    private long lastDataUpdateTimeMs = 0;
    private float accumulatedLogicDeltaSeconds = 0.0f;
    private boolean hasContent = false;
    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    public boolean shouldUpdate(int targetFps) {
        if (targetFps <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long intervalMs = 1000L / targetFps;
        return !hasContent || now - lastRenderTimeMs >= intervalMs;
    }

    public float getThrottledDelta(int targetFps, float deltaSeconds) {
        if (targetFps <= 0) {
            return deltaSeconds;
        }

        long now = System.currentTimeMillis();
        long intervalMs = 1000L / targetFps;
        accumulatedLogicDeltaSeconds += deltaSeconds;
        if (lastLogicUpdateTimeMs == 0 || now - lastLogicUpdateTimeMs >= intervalMs) {
            float result = accumulatedLogicDeltaSeconds;
            accumulatedLogicDeltaSeconds = 0.0f;
            lastLogicUpdateTimeMs = now;
            return result;
        }
        return 0.0f;
    }

    public boolean shouldUpdateData(int targetFps) {
        if (targetFps <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long intervalMs = 1000L / targetFps;
        if (lastDataUpdateTimeMs == 0 || now - lastDataUpdateTimeMs >= intervalMs) {
            lastDataUpdateTimeMs = now;
            return true;
        }
        return false;
    }

    public void beginCapture() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (framebuffer == null || lastScreenWidth != width || lastScreenHeight != height) {
            if (framebuffer != null) {
                framebuffer.delete();
            }

            framebuffer = new SimpleFramebuffer("phaze/hud_buffer", width, height, true);
            lastScreenWidth = width;
            lastScreenHeight = height;
            hasContent = false;
        }

        clearCaptureTarget();
        activeCaptureFramebuffer = framebuffer;
        activeCaptureTarget = CAPTURE_ACTIVE;
    }

    public void endCapture() {
        activeCaptureTarget = -1;
        activeCaptureFramebuffer = null;

        hasContent = true;
        lastRenderTimeMs = System.currentTimeMillis();
    }

    public void bindCaptureTarget() {
        if (framebuffer != null) {
            activeCaptureFramebuffer = framebuffer;
            activeCaptureTarget = CAPTURE_ACTIVE;
        }
    }

    private void clearCaptureTarget() {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (framebuffer.getDepthAttachment() != null) {
            encoder.clearColorAndDepthTextures(
                    framebuffer.getColorAttachment(), CLEAR_ARGB,
                    framebuffer.getDepthAttachment(), 1.0);
        } else {
            encoder.clearColorTexture(framebuffer.getColorAttachment(), CLEAR_ARGB);
        }
    }

    public boolean hasContent() {
        return hasContent;
    }

    public boolean drawCached() {
        if (framebuffer == null || !hasContent) {
            return false;
        }

        ScreenBlit.blitOverMain(framebuffer);
        return true;
    }

    public void invalidate() {
        hasContent = false;
        lastLogicUpdateTimeMs = 0;
        lastDataUpdateTimeMs = 0;
        accumulatedLogicDeltaSeconds = 0.0f;
    }

    public void cleanup() {
        if (activeCaptureFramebuffer == framebuffer) {
            activeCaptureFramebuffer = null;
            activeCaptureTarget = -1;
        }
        if (framebuffer != null) {
            framebuffer.delete();
            framebuffer = null;
        }
        hasContent = false;
    }
}
