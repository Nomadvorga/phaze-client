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
 * and report the average.
 *
 * <h3>Rendering</h3>
 * Routed through the standard batched HUD pipeline alongside Time /
 * Memory / Combo / etc. - the renderer pulls the formatted text via
 * {@link #getFormattedText()} and the colour via
 * {@link #getColor()} (when {@link #colorByTps} is on).
 */
public final class TpsHud extends RectHudModule {
    private static final TpsHud INSTANCE = new TpsHud();

    /** How many raw samples the rolling window holds. */
    public static final int SAMPLE_WINDOW = 60;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting colorByTps = new BooleanSetting(
            "Color By TPS",
            "Tint the TPS reading green / yellow / red based on tier"
    ).setValue(true);

    /** Rolling raw-sample buffer. New samples added at the head;
     *  oldest sample drops off the tail when SAMPLE_WINDOW is hit. */
    private final java.util.ArrayDeque<Double> samples = new java.util.ArrayDeque<>(SAMPLE_WINDOW);
    private long lastSampleNanos = 0L;
    private long lastWorldTime = 0L;

    private TpsHud() {
        super("tps_hud", "TPS", 22.0F, 22.0F, 1.0F);
        colorByTps.setFullWidth(true);
        setup(generalSection, colorByTps);
    }

    public static TpsHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Estimates server TPS based on world-time advancement";
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
        double deltaSeconds = (now - lastSampleNanos) / 1_000_000_000.0;
        if (deltaSeconds < 0.2) return;

        long deltaTicks = currentWorldTime - lastWorldTime;
        if (deltaTicks < 0) {
            lastSampleNanos = now;
            lastWorldTime = currentWorldTime;
            return;
        }
        double tps = deltaTicks / deltaSeconds;
        if (tps > 20.5) tps = 20.0;
        samples.addFirst(tps);
        while (samples.size() > SAMPLE_WINDOW) {
            samples.removeLast();
        }
        lastSampleNanos = now;
        lastWorldTime = currentWorldTime;
    }

    /** Smoothed TPS = simple mean of the window. Empty window
     *  returns 20 (assume healthy until we have data). */
    public double getSmoothedTps() {
        if (samples.isEmpty()) return 20.0;
        double sum = 0;
        for (Double s : samples) sum += s;
        return sum / samples.size();
    }

    /** Formatted text the batched HUD renderer paints. */
    public String getFormattedText() {
        return String.format("TPS: %.1f", getSmoothedTps());
    }

    /** Tier color matching the FPS HUD convention. */
    public int getColor() {
        if (!colorByTps.isValue()) return 0xFFFFFFFF;
        double tps = getSmoothedTps();
        if (tps >= 18.0) return 0xFF55FF55;
        if (tps >= 15.0) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
