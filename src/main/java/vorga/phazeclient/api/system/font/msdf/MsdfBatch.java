package vorga.phazeclient.api.system.font.msdf;

import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.api.system.shape.batched.GuiBatch;

/**
 * Deferred MSDF quads (text and UI icons) inside a {@link BatchedRectangle} scope. They join the
 * shared {@link GuiBatch} stream, so they keep exact submission order relative to rectangles.
 */
public final class MsdfBatch {
    private static boolean enabled = true;

    private MsdfBatch() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** A/B switch for benchmarks; disabling drains anything pending. */
    public static void setEnabled(boolean value) {
        if (enabled && !value) BatchedRectangle.flushIfBatching();
        enabled = value;
    }

    public static boolean canDefer() {
        return enabled && BatchedRectangle.isBatching();
    }

    /** Run the caller appends one string's glyph quads to. */
    static GuiBatch.Run begin(int texture, float range, float thickness, float smoothness,
                              float fadeLeft, float fadeRight, float fadeWidth,
                              float fadeLeftStrength, float fadeRightStrength) {
        return GuiBatch.msdf(texture, range, thickness, smoothness,
                fadeLeft, fadeRight, fadeWidth, fadeLeftStrength, fadeRightStrength);
    }

    /** {@link #begin} for UI icon quads, which share the MSDF shading but use their own atlas. */
    public static GuiBatch.Run beginIcon(int texture, float range, float thickness, float smoothness) {
        return GuiBatch.msdf(texture, range, thickness, smoothness, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }
}
