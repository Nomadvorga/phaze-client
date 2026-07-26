package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

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

    /** ARGB clear value for the capture target: fully transparent black. */
    private static final int CLEAR_ARGB = 0x00000000;

    /**
     * 1.21.11: {@code Framebuffer.fbo} (the raw GL handle) is gone, so
     * {@link HudBuffer#activeCaptureTarget} degenerated into a plain
     * "capture in progress" flag ({@code >= 0} while capturing). This is the
     * value written into it; the actual target now travels in
     * {@link HudBuffer#activeCaptureFramebuffer}.
     */
    private static final int CAPTURE_ACTIVE = 1;

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
                // 1.21.11: un-publish before deleting - the capture target is a
                // live Framebuffer reference now, not an int handle.
                if (HudBuffer.activeCaptureFramebuffer == fbo) {
                    HudBuffer.activeCaptureFramebuffer = null;
                    HudBuffer.activeCaptureTarget = -1;
                }
                fbo.delete();
                fbo = null;
            }
            // 1.21.11: the debug name is the FIRST ctor arg now.
            fbo = new SimpleFramebuffer("phaze/batched_hud", w, h, true);
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

        // 1.21.11: the defensive GL-state reset that used to live here is now
        // redundant and has been deleted.
        //
        // It existed because glClear is gated by GL_SCISSOR_TEST, glColorMask
        // and glDepthMask: a leaked scissor box or a masked-off channel meant
        // the (0,0,0,0) clear only erased part of the FBO, so last frame's
        // glyph pixels survived. With TRANSLUCENT alpha blending
        // (a = src.a + dst.a*(1-src.a)) a surviving alpha=1.0 text pixel stays
        // at 1.0 under the new alpha=0.5 background fill, which is exactly the
        // "background goes transparent in the shape of the OLD digits" bug.
        //
        // setClearColor + clear() are gone; the clear is a CommandEncoder
        // operation now, and GlCommandEncoder.clearColorAndDepthTextures does
        // _disableScissorTest() + _depthMask(true) + _colorMask(true,true,true,true)
        // itself immediately before _clear (verified in bytecode). Re-doing it
        // from mod code would only write behind the backend's own state cache.
        clearFbo();

        // beginWrite() is gone - a draw picks its target when it opens a render
        // pass, so all we can do is publish the target and let the
        // MinecraftClient#getFramebuffer redirect mixin route passes into it.
        HudBuffer.activeCaptureFramebuffer = fbo;
        HudBuffer.activeCaptureTarget = CAPTURE_ACTIVE;
    }

    /**
     * Clears the capture FBO to fully transparent black.
     *
     * <p>NOTE (plan J-6): the encoder throws
     * {@code IllegalStateException("Close the existing render pass before
     * creating a new one!")} if a render pass is open, so this - and therefore
     * {@link #beginCapture()} - must run OUTSIDE the GUI batch pass.
     */
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
        HudBuffer.activeCaptureTarget = -1;
        HudBuffer.activeCaptureFramebuffer = null;
        if (fbo == null) {
            return;
        }
        // 1.21.11: no endWrite(), and nothing to re-bind afterwards - the main
        // framebuffer is simply whatever the next render pass names.
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

        // See ScreenBlit: blend / depth / cull / colour-write moved onto the
        // pipeline, the pass sets its own viewport, and the quad comes from
        // gl_VertexID. Premultiplied-alpha blend is preserved, which is why
        // this does not use Framebuffer.drawBlit - that runs
        // ENTITY_OUTLINE_BLIT with straight-alpha blending and no
        // destination alpha write.
        vorga.phazeclient.api.system.draw.ScreenBlit.blitOverMain(fbo);
    }

    public void cleanup() {
        // 1.21.11: the capture target is now a live Framebuffer reference, not
        // an int handle, so it must be un-published before the FBO is deleted -
        // otherwise HudBuffer.activeCaptureFramebuffer dangles.
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
