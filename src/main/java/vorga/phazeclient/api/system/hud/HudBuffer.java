package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import vorga.phazeclient.api.system.draw.ScreenBlit;

public class HudBuffer {
    /** ARGB clear value for the capture target: fully transparent black. */
    private static final int CLEAR_ARGB = 0x00000000;

    /**
     * Sentinel written into {@link #activeCaptureTarget} while a capture is
     * open. Public so {@code BatchedHudBuffer} (which used to assign
     * {@code fbo.fbo}) has a name to assign instead of a magic number.
     */
    public static final int CAPTURE_ACTIVE = 1;

    /**
     * 1.21.11: {@code Framebuffer.fbo} (the raw GL handle) no longer exists -
     * render targets are chosen per render pass, not by binding an int. This
     * field therefore degenerates from "the GL FBO id being captured into" to
     * a plain "capture in progress" flag: {@code >= 0} while capturing,
     * {@code -1} otherwise.
     *
     * <p>It is deliberately still an {@code int} so the existing
     * {@code activeCaptureTarget >= 0} / {@code < 0} tests in
     * {@code MinecraftClientMixin}, {@code BatchedHudBuffer} and {@code Blur}
     * keep working unchanged. Anything that needs the actual target must read
     * {@link #activeCaptureFramebuffer}.
     *
     * <p>TODO(1.21.11): per plan J-7 this should collapse into
     * {@link #activeCaptureFramebuffer} once every consumer has been ported.
     */
    public static volatile int activeCaptureTarget = -1;

    /**
     * The framebuffer a capture is currently writing into, or {@code null}.
     * Replaces the int FBO handle that {@link #activeCaptureTarget} used to
     * carry - this is what callers must render into / restore now.
     */
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

    /**
     * 1.21.11: there is nothing to "bind". {@code beginWrite}/{@code endWrite}
     * are gone; a draw picks its target when it opens a render pass, so all
     * this can do is clear the capture texture and publish the target so
     * downstream code (and the {@code MinecraftClient#getFramebuffer} redirect
     * mixin) routes its passes into it.
     *
     * <p>NOTE (plan J-6): {@code clearColorAndDepthTextures} throws if a render
     * pass is already open, so this must run OUTSIDE the GUI batch pass.
     */
    public void beginCapture() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (framebuffer == null || lastScreenWidth != width || lastScreenHeight != height) {
            if (framebuffer != null) {
                framebuffer.delete();
            }
            // 1.21.11: the debug name is the FIRST ctor arg now.
            framebuffer = new SimpleFramebuffer("phaze/hud_buffer", width, height, true);
            lastScreenWidth = width;
            lastScreenHeight = height;
            hasContent = false;
        }

        // setClearColor + clear() -> a single encoder clear with an ARGB int.
        clearCaptureTarget();
        activeCaptureFramebuffer = framebuffer;
        activeCaptureTarget = CAPTURE_ACTIVE;
    }

    public void endCapture() {
        activeCaptureTarget = -1;
        activeCaptureFramebuffer = null;
        // 1.21.11: no endWrite(), and nothing to re-bind afterwards - the main
        // framebuffer is simply whatever the next render pass names.
        hasContent = true;
        lastRenderTimeMs = System.currentTimeMillis();
    }

    /**
     * Re-publishes this buffer as the active capture target without clearing
     * it (the old {@code beginWrite} re-bind). Purely a flag update now.
     */
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

        // 1.21.11: the whole imperative preamble is gone. Blend, depth,
        // cull and colour-write live on the pipeline, the render pass sets
        // its own viewport, and the screen quad is synthesised from
        // gl_VertexID - so there is no framebuffer rebind, no manual
        // viewport, no Tessellator and no state to put back afterwards.
        // VertexFormats.BLIT_SCREEN and ShaderProgramKeys no longer exist.
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
