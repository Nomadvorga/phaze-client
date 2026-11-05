package vorga.phazeclient.api.system.hud;

import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;

/** A capture is published only after an explicit commit; close aborts partial output. */
public final class HudCapture implements AutoCloseable {
    private final Render2DState state;
    private final int previousTarget;
    private final float previousHeight;
    private final Runnable publish, abort;
    private boolean committed, closed;

    HudCapture(Render2DState state, Runnable publish, Runnable abort) {
        this.state = state;
        this.publish = publish;
        this.abort = abort;
        previousTarget = HudBuffer.activeCaptureTarget;
        previousHeight = BatchedRectangle.getRenderTargetFbHeight();
    }

    public void commit() { committed = true; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (committed) BatchedRectangle.flushIfBatching();
            else BatchedRectangle.discardPending();
        } catch (RuntimeException | Error failure) {
            committed = false;
            throw failure;
        } finally {
            HudBuffer.activeCaptureTarget = previousTarget;
            BatchedRectangle.restoreRenderTargetFbHeight(previousHeight);
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
