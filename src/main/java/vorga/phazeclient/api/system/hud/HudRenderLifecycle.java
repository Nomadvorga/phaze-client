package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.implement.Blur;

/** Unified reload/shutdown hook; call on the render thread before the GL context is destroyed. */
public final class HudRenderLifecycle {
    private HudRenderLifecycle() {}

    public static void cleanup() {
        RenderSystem.assertOnRenderThread();
        // Abort before snapshotting, so no deleted capture target is restored by the lifecycle boundary.
        BatchedHudBuffer.INSTANCE.abortCapture();
        HudBuffer.abortAllCaptures();
        BatchedRectangle.cleanup();
        try (Render2DState state = Render2DState.capture()) {
            try {
                HudBuffer.cleanupAll();
            } finally {
                try {
                    BatchedHudBuffer.INSTANCE.cleanup();
                } finally {
                    try {
                        Blur.INSTANCE.cleanup();
                    } finally {
                        try {
                            BatchedRectangle.cleanup();
                        } finally {
                            HudBuffer.activeCaptureTarget = -1;
                            Render2DBuffers.cleanup();
                        }
                    }
                }
            }
        }
    }
}
