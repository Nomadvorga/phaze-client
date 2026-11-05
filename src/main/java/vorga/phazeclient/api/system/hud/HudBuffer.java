package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11C;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class HudBuffer {
    public static volatile int activeCaptureTarget = -1;
    private static final Set<HudBuffer> BUFFERS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private HudCapture capture;

    public HudBuffer() { BUFFERS.add(this); }

    public static void cleanupAll() {
        RuntimeException failure = null;
        for (HudBuffer buffer : BUFFERS.toArray(HudBuffer[]::new)) {
            try { buffer.cleanup(); } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }

    public static void abortAllCaptures() {
        for (HudBuffer buffer : BUFFERS.toArray(HudBuffer[]::new)) buffer.abortCapture();
    }

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
        openCapture();
    }

    public HudCapture openCapture() {
        if (capture != null) throw new IllegalStateException("HUD capture already open");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return null;
        BatchedRectangle.flushIfBatching();
        Render2DState state = Render2DState.capture();
        HudCapture next = new HudCapture(state, () -> {
            capture = null;
            hasContent = true;
            lastRenderTimeMs = System.currentTimeMillis();
        }, () -> { capture = null; hasContent = false; });
        try {
            if (framebuffer == null || lastScreenWidth != width || lastScreenHeight != height) {
                if (framebuffer != null) {
                    SimpleFramebuffer old = framebuffer;
                    framebuffer = null;
                    old.delete();
                }
                framebuffer = new SimpleFramebuffer(width, height, true);
                // Native-resolution HUD caches must not soften text during composition.
                framebuffer.setTexFilter(GL11C.GL_NEAREST);
                lastScreenWidth = width;
                lastScreenHeight = height;
                hasContent = false;
            }
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._depthMask(true);
            framebuffer.setClearColor(0, 0, 0, 0);
            framebuffer.clear();
            framebuffer.beginWrite(false);
            RenderSystem.viewport(0, 0, width, height);
            BatchedRectangle.setRenderTargetFbHeight(height);
            activeCaptureTarget = framebuffer.fbo;
            capture = next;
            return next;
        } catch (RuntimeException | Error failure) {
            try { next.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void endCapture() {
        if (capture == null) return;
        capture.commit();
        capture.close();
    }

    public void abortCapture() {
        if (capture != null) capture.close();
    }

    public void bindCaptureTarget() {
        if (capture != null && framebuffer != null) {
            framebuffer.beginWrite(false);
            activeCaptureTarget = framebuffer.fbo;
        }
    }

    public boolean hasContent() {
        return hasContent;
    }

    public boolean drawCached() {
        return framebuffer != null && hasContent && HudBlit.draw(framebuffer);
    }

    public void invalidate() {
        hasContent = false;
        lastLogicUpdateTimeMs = 0;
        lastDataUpdateTimeMs = 0;
        accumulatedLogicDeltaSeconds = 0.0f;
    }

    public void cleanup() {
        abortCapture();
        invalidate();
        lastRenderTimeMs = 0L;
        lastScreenWidth = lastScreenHeight = 0;
        if (framebuffer != null) {
            SimpleFramebuffer target = framebuffer;
            framebuffer = null;
            hasContent = false;
            target.delete();
        }
        hasContent = false;
    }
}
