package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11C;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

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
    private final HudAnimationRefreshClock refreshClock = new HudAnimationRefreshClock(30);
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
    private HudCapture capture;

    private BatchedHudBuffer() {
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(1, Math.min(360, fps));
        refreshClock.setFps(this.targetFps);
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
        return fbo != null && HudBuffer.activeCaptureTarget == fbo.fbo ? fbo : null;
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
        MinecraftClient client = MinecraftClient.getInstance();
        boolean resized = client != null && client.getWindow() != null
                && (lastWidth != client.getWindow().getFramebufferWidth()
                || lastHeight != client.getWindow().getFramebufferHeight());
        if (force || dirty || !hasContent || resized) {
            return true;
        }
        return refreshClock.due(System.nanoTime());
    }

    /**
     * Allocates / resizes the framebuffer if the window size changed. Returns
     * {@code true} if the framebuffer is ready to use, {@code false} on invalid
     * window state.
     */
    public boolean ensureFramebuffer() {
        try (Render2DState state = Render2DState.capture()) {
            return ensureFramebufferInternal();
        }
    }

    private boolean ensureFramebufferInternal() {
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
                SimpleFramebuffer old = fbo;
                fbo = null;
                old.delete();
            }
            fbo = new SimpleFramebuffer(w, h, true);
            // This is a full-resolution HUD cache, not a blur surface. Keep
            // text and 1px HUD strokes crisp when the cached quad is reused
            // after opening/closing a Screen.
            fbo.setTexFilter(GL11C.GL_NEAREST);
            lastWidth = w;
            lastHeight = h;
            hasContent = false;
            dirty = true;
        }
        return true;
    }

    public void beginCapture() {
        openCapture();
    }

    /** Null on an invalid/minimized window. The scope restores state even if allocation/clear fails. */
    public HudCapture openCapture() {
        if (capture != null) throw new IllegalStateException("HUD capture already open");
        BatchedRectangle.flushIfBatching();
        Render2DState state = Render2DState.capture();
        long refreshStarted = System.nanoTime();
        HudCapture next = new HudCapture(state, () -> {
            capture = null;
            hasContent = true;
            dirty = false;
            refreshClock.captured(refreshStarted);
        }, () -> {
            capture = null;
            hasContent = false;
            dirty = true;
        });
        try {
            if (!ensureFramebufferInternal()) {
                next.close();
                return null;
            }
            // Snapshot REAL main framebuffer BEFORE enabling the redirect flag.
            MinecraftClient mc = MinecraftClient.getInstance();
            if (HudBuffer.activeCaptureTarget < 0) realMainFramebuffer = mc != null ? mc.getFramebuffer() : null;
            clearCaptureTarget();
            capture = next;
            return next;
        } catch (RuntimeException | Error failure) {
            try { next.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void clearCaptureTarget() {
        // Defensive GL-state reset before clearing the cached FBO.
        // glClear is gated by THREE pieces of state that vanilla MC and
        // Phaze freely toggle during world / vanilla-HUD rendering:
        //
        //   1. GL_SCISSOR_TEST + glScissor(...). When enabled, glClear
        //      only touches pixels INSIDE the scissor box. If anything
        //      upstream (vanilla chat clipping in some 1.21.x builds,
        //      ScissorManager paths from menu/tooltip rendering that
        //      didn't fully unwind, OR a future feature that forgets to
        //      pop) leaves a scissor smaller than the framebuffer
        //      enabled, our (0,0,0,0) clear only erases that sub-region
        //      and pixels OUTSIDE it keep the previous frame's HUD
        //      contents.
        //
        //   2. glColorMask. If any channel was disabled (e.g. an alpha-
        //      only stencil pass that masked RGB) the corresponding
        //      channels are skipped during the clear, leaving stale
        //      values that "burn through" subsequent renders.
        //
        //   3. glDepthMask. SimpleFramebuffer.clear() always issues a
        //      depth-buffer-bit clear when depth attachment is present,
        //      but a depthMask(false) leftover would silently no-op it,
        //      causing later depth-tested HUD geometry to either reject
        //      every pixel or accumulate Z-fight against last frame's
        //      depth values.
        //
        // The user-visible failure mode of (1) is exactly what the
        // current ticket describes: when the HUD text changes, the OLD
        // glyph shapes "burn through" the HUD background because the
        // FBO's old text pixels were never cleared - the new bg-fill
        // alpha-blends over them but, with TRANSLUCENT_TRANSPARENCY's
        // alpha = src.alpha + dst.alpha*(1-src.alpha), bg-fill at
        // alpha=0.5 over an old-text pixel at alpha=1.0 yields
        // result.alpha=1.0, locking the old glyph mask into the FBO
        // forever and producing the user's "the background becomes
        // transparent in the shape of old digits" effect.
        //
        // Forcing the three masks open + scissor disabled here costs a
        // handful of GL state-cache writes per cache refresh (i.e. once
        // every ~33 ms at the default 30 Hz) and absolutely guarantees
        // glClear hits every pixel of the FBO.
        GlStateManager._disableScissorTest();
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._depthMask(true);

        fbo.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        fbo.clear();
        fbo.beginWrite(false);
        RenderSystem.viewport(0, 0, fbo.textureWidth, fbo.textureHeight);
        BatchedRectangle.setRenderTargetFbHeight(fbo.textureHeight);
        HudBuffer.activateCaptureTarget(fbo);
    }

    public void endCapture() {
        if (capture == null) return;
        capture.commit();
        capture.close();
    }

    public void abortCapture() {
        if (capture != null) capture.abort();
    }

    /**
     * Blits the cached HUD texture onto the main framebuffer using
     * premultiplied alpha. Safe to call every frame.
     */
    public void blit() {
        if (fbo != null && hasContent) HudBlit.draw(fbo);
    }

    public void cleanup() {
        abortCapture();
        realMainFramebuffer = null;
        refreshClock.reset();
        hasContent = false;
        dirty = true;
        lastWidth = -1;
        lastHeight = -1;
        if (fbo != null) {
            SimpleFramebuffer target = fbo;
            fbo = null;
            target.delete();
        }
    }
}
