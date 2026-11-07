package vorga.phazeclient.api.system.hud;

import net.minecraft.client.gl.Framebuffer;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

/** A capture is published only after an explicit commit; close aborts partial output. */
public final class HudCapture implements AutoCloseable {
    private static HudCapture active;
    private final HudCapture parent;
    private final Render2DState state;
    private final int previousTarget;
    private final Framebuffer previousFramebuffer;
    private final float previousHeight;
    private final Runnable publish, abort;
    private boolean committed, closed;

    HudCapture(Render2DState state, Runnable publish, Runnable abort) {
        this.state = state;
        this.publish = publish;
        this.abort = abort;
        previousTarget = HudBuffer.activeCaptureTarget;
        previousFramebuffer = HudBuffer.getActiveCaptureFramebuffer();
        previousHeight = BatchedRectangle.getRenderTargetFbHeight();
        parent = active;
        active = this;
    }

    public void commit() { committed = true; }

    void abort() {
        committed = false;
        close();
    }

    /** Lifecycle disposal must unwind children before their framebuffer-owning parents. */
    static void abortAll() {
        Throwable failure = null;
        while (active != null) {
            try { active.abort(); } catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    @Override
    public void close() {
        if (closed) return;
        if (active != this) throw new IllegalStateException("HUD captures must close in reverse nesting order");
        closed = true;
        try {
            if (committed) BatchedRectangle.flushIfBatching();
            else BatchedRectangle.discardPending();
        } catch (RuntimeException | Error failure) {
            committed = false;
            throw failure;
        } finally {
            HudBuffer.restoreCaptureTarget(previousTarget, previousFramebuffer);
            BatchedRectangle.restoreRenderTargetFbHeight(previousHeight);
            active = parent;
            boolean restored = false;
            try {
                state.close();
                restored = true;
            } finally {
                if (committed && restored) publish.run(); else abort.run();
            }
        }
    }
}
