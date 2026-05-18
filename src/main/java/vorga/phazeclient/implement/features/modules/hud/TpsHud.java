package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Server tick-rate visualiser. Vanilla doesn't push an explicit TPS
 * number to the client, so we infer it client-side by watching how
 * fast {@code World.getTime()} advances against wall-clock time:
 * each game tick advances world time by 1, so {@code (deltaTicks /
 * deltaSeconds)} approximates TPS as observed by this client.
 *
 * <h3>Smoothing</h3>
 * The raw measurement jitters because a single laggy server frame
 * can stall world-time updates for hundreds of milliseconds. We
 * keep a rolling window of the last {@link #SAMPLE_WINDOW} samples
 * and report the average; the bar widget plots the per-sample raw
 * values so users still see the spikes.
 *
 * <h3>Why a 200ms tick</h3>
 * Sampling at 200ms means each window contains ~5 samples per
 * second. Over the configured 60-sample window that's a 12-second
 * trailing average, which feels representative without being
 * laggy when the user toggles between low- and high-tps servers.
 */
public final class TpsHud extends RectHudModule {
    private static final TpsHud INSTANCE = new TpsHud();

    /** How many raw samples the rolling window holds. */
    public static final int SAMPLE_WINDOW = 60;

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting showBar = new BooleanSetting(
            "Show Bar",
            "Render a 60-sample history bar showing TPS over the last ~12 seconds"
    ).setValue(true);
    public final BooleanSetting showNumeric = new BooleanSetting(
            "Show Numeric",
            "Show the current smoothed TPS reading as a number"
    ).setValue(true);
    public final BooleanSetting colorByTps = new BooleanSetting(
            "Color By TPS",
            "Tint the numeric reading green / yellow / red based on tier"
    ).setValue(true);

    /** Rolling raw-sample buffer. New samples added at the head;
     *  oldest sample drops off the tail when SAMPLE_WINDOW is hit. */
    private final java.util.ArrayDeque<Double> samples = new java.util.ArrayDeque<>(SAMPLE_WINDOW);
    private long lastSampleNanos = 0L;
    private long lastWorldTime = 0L;

    private TpsHud() {
        super("tps_hud", "TPS", 22.0F, 22.0F, 1.5F);
        showBar.setFullWidth(true);
        showNumeric.setFullWidth(true);
        colorByTps.setFullWidth(true);
        setup(otherSection, showBar, showNumeric, colorByTps);
    }

    public static TpsHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Estimates and visualises server TPS based on world-time advancement";
    }

    @Override
    public String getIcon() {
        return "tps_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Push a fresh TPS sample. Called from
     * {@code ClientPlayerEntityMixin.onTick} (the same per-tick
     * hook other lifetime modules use). Computes the inferred TPS
     * from the world-time advancement since the previous sample and
     * appends to the rolling window.
     */
    public void recordSample(long currentWorldTime) {
        long now = System.nanoTime();
        if (lastSampleNanos == 0L) {
            lastSampleNanos = now;
            lastWorldTime = currentWorldTime;
            return;
        }
        // Sample at 200 ms intervals to keep the history aggregation
        // consistent. Faster sampling would over-represent client
        // tick jitter; slower sampling makes the bar widget feel
        // sluggish.
        double deltaSeconds = (now - lastSampleNanos) / 1_000_000_000.0;
        if (deltaSeconds < 0.2) return;

        long deltaTicks = currentWorldTime - lastWorldTime;
        // Negative delta = world reset (e.g. dimension change).
        // Drop the sample and re-anchor the baseline so the next
        // window starts cleanly.
        if (deltaTicks < 0) {
            lastSampleNanos = now;
            lastWorldTime = currentWorldTime;
            return;
        }
        double tps = deltaTicks / deltaSeconds;
        // Clamp upper bound so a one-tick burst can't spike the
        // bar to 100+. Vanilla server caps at 20.
        if (tps > 20.5) tps = 20.0;
        samples.addFirst(tps);
        while (samples.size() > SAMPLE_WINDOW) {
            samples.removeLast();
        }
        lastSampleNanos = now;
        lastWorldTime = currentWorldTime;
    }

    /** Live snapshot of the rolling window for the renderer. */
    public java.util.Deque<Double> getSamples() {
        return samples;
    }

    /** Smoothed TPS = simple mean of the window. Empty window
     *  returns 20 (assume healthy until we have data). */
    public double getSmoothedTps() {
        if (samples.isEmpty()) return 20.0;
        double sum = 0;
        for (Double s : samples) sum += s;
        return sum / samples.size();
    }
}
