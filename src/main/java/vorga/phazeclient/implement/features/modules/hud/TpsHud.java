package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class TpsHud extends RectHudModule {
    private static final TpsHud INSTANCE = new TpsHud();

    public static final int SAMPLE_WINDOW = 60;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting colorByTps = new BooleanSetting(
            "Color By TPS",
            "Tint the TPS reading green / yellow / red based on tier"
    ).setValue(true);

    private final java.util.ArrayDeque<Double> samples = new java.util.ArrayDeque<>(SAMPLE_WINDOW);
    private long lastSampleNanos = 0L;
    private long lastWorldTime = 0L;

    private TpsHud() {
        super("tps_hud", "TPS", 22.0F, 22.0F, 1.0F);
        colorByTps.setFullWidth(true);
        setup(generalSection, colorByTps, otherSection, cornerRounding);
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

    public double getSmoothedTps() {
        if (samples.isEmpty()) return 20.0;
        double sum = 0;
        for (Double s : samples) sum += s;
        return sum / samples.size();
    }

    public String getFormattedText() {
        return String.format("TPS: %.1f", getSmoothedTps());
    }

    public int getColor() {
        if (!colorByTps.isValue()) return 0xFFFFFFFF;
        double tps = getSmoothedTps();
        if (tps >= 18.0) return 0xFF55FF55;
        if (tps >= 15.0) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
