package vorga.phazeclient.api.system.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;
import vorga.phazeclient.api.system.shape.implement.Blur;

/** Unified reload/shutdown hook; call on the render thread before the GL context is destroyed. */
public final class HudRenderLifecycle {
    private HudRenderLifecycle() {}

    public static void cleanup() {
        RenderSystem.assertOnRenderThread();
        // Abort before snapshotting, so no deleted capture target is restored by the lifecycle boundary.
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
                            HudBuffer.resetCaptureTarget();
                            try {
                                Render2DBuffers.cleanup();
                            } finally {
                                GuiBatch.close();
                            }
                        }
                    }
                }
            }
        }
    }
}
