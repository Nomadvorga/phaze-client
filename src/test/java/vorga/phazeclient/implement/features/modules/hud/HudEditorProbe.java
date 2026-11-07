package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.gl.Framebuffer;
import vorga.phazeclient.api.system.hud.HudBuffer;

/** Test-only observations. No counters or instrumentation are shipped in the client jar. */
public final class HudEditorProbe {
    public static boolean recording;
    public static int batchCaptures, childCaptures, cachedItems, cachedModels, uncachedModels, blurMasks, chatRenders, capturedChat;
    public static Framebuffer batchFramebuffer, childFramebuffer;

    private HudEditorProbe() {}

    public static void start() {
        batchCaptures = childCaptures = cachedItems = cachedModels = uncachedModels = blurMasks = chatRenders = capturedChat = 0;
        batchFramebuffer = childFramebuffer = null;
        recording = true;
    }

    public static void capture(boolean child) {
        if (!recording) return;
        if (child) {
            childCaptures++;
            childFramebuffer = HudBuffer.getActiveCaptureFramebuffer();
        } else {
            batchCaptures++;
            batchFramebuffer = HudBuffer.getActiveCaptureFramebuffer();
        }
    }

    public static void model() {
        if (!recording) return;
        if (HudBuffer.activeCaptureTarget >= 0) cachedModels++;
        else uncachedModels++;
    }

    public static Counts counts() {
        return new Counts(batchCaptures, childCaptures, cachedItems, cachedModels, blurMasks);
    }

    public record Counts(int batch, int child, int items, int models, int blur) {}
}
