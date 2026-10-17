package vorga.phazeclient.base.util.render;

/**
 * Incrementally integrates glint texture time without multiplying the clock's
 * epoch by a changing speed. Not thread-safe: the mixin owns one instance and
 * asserts render-thread access. No module callbacks or background writers.
 */
public final class GlintTextureClock {
    private boolean active;
    private long previousRealMs;
    private long textureMs;
    private double fractionalMs;
    private double previousSpeed;

    /**
     * Returns vanilla time exactly while disabled. Enabling anchors to the
     * current vanilla phase; subsequent samples integrate positive elapsed time.
     * The previously observed speed governs the elapsed interval, so observing a
     * new slider value never rescales already elapsed time. Sub-ms time is kept
     * separately so slow movement survives many passes/short frame intervals.
     */
    public long sample(long realMs, boolean enabled, double speed) {
        if (!enabled) {
            active = false;
            return realMs;
        }

        double boundedSpeed = Double.isFinite(speed) ? Math.max(0.2, Math.min(4.0, speed)) : 1.0;
        if (!active) {
            active = true;
            previousRealMs = realMs;
            textureMs = realMs;
            fractionalMs = 0.0;
        } else if (realMs > previousRealMs) {
            double elapsed = (realMs - previousRealMs) * previousSpeed + fractionalMs;
            long wholeMs = (long) elapsed;
            textureMs += wholeMs;
            fractionalMs = elapsed - wholeMs;
            previousRealMs = realMs;
        }
        previousSpeed = boundedSpeed;
        return textureMs;
    }
}
