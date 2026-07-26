package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import org.lwjgl.opengl.GL11C;

public class HudBuffer {
    public static volatile int activeCaptureTarget = -1;

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
            framebuffer = new SimpleFramebuffer(width, height, true);
            framebuffer.setTexFilter(GL11C.GL_LINEAR);
            lastScreenWidth = width;
            lastScreenHeight = height;
            hasContent = false;
        }

        framebuffer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        framebuffer.clear();
        framebuffer.beginWrite(false);
        activeCaptureTarget = framebuffer.fbo;
    }

    public void endCapture() {
        activeCaptureTarget = -1;
        framebuffer.endWrite();
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
        hasContent = true;
        lastRenderTimeMs = System.currentTimeMillis();
    }

    public void bindCaptureTarget() {
        if (framebuffer != null) {
            framebuffer.beginWrite(false);
            activeCaptureTarget = framebuffer.fbo;
        }
    }

    public boolean hasContent() {
        return hasContent;
    }

    public boolean drawCached() {
        if (framebuffer == null || !hasContent) {
            return false;
        }

        // 1.21.11: the whole imperative preamble is gone. Blend, depth,
        // cull and colour-write live on the pipeline, the render pass sets
        // its own viewport, and the screen quad is synthesised from
        // gl_VertexID - so there is no framebuffer rebind, no manual
        // viewport, no Tessellator and no state to put back afterwards.
        // VertexFormats.BLIT_SCREEN and ShaderProgramKeys no longer exist.
        vorga.phazeclient.api.system.draw.ScreenBlit.blitOverMain(framebuffer);
        return true;
    }

    public void invalidate() {
        hasContent = false;
        lastLogicUpdateTimeMs = 0;
        lastDataUpdateTimeMs = 0;
        accumulatedLogicDeltaSeconds = 0.0f;
    }

    public void cleanup() {
        if (framebuffer != null) {
            framebuffer.delete();
            framebuffer = null;
        }
        hasContent = false;
    }
}
