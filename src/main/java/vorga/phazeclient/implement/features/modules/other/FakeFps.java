package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class FakeFps extends Module {
    private static final FakeFps INSTANCE = new FakeFps();

    private static final long UPDATE_INTERVAL_MS = 1000L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting minFps = new ValueSetting(
            "Min FPS",
            "Lower bound of the fake FPS range (inclusive)"
    ).range(1, 9999).step(1).setValue(120);
    public final ValueSetting maxFps = new ValueSetting(
            "Max FPS",
            "Upper bound of the fake FPS range (inclusive)"
    ).range(1, 9999).step(1).setValue(240);

    private int lastFps = 0;

    private long lastUpdateMs = 0L;
    private final java.util.Random random = new java.util.Random();

    private FakeFps() {
        super("fake_fps", "Fake FPS", ModuleCategory.OTHER);
        minFps.setFullWidth(true);
        maxFps.setFullWidth(true);
        setup(generalSection, minFps, maxFps);
    }

    public static FakeFps getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Replaces the visible FPS counter with a randomised value drawn from your configured range";
    }

    @Override
    public String getIcon() {
        return "fake_fps.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public int getFakeFps() {
        long now = System.currentTimeMillis();
        if (lastUpdateMs == 0L || now - lastUpdateMs >= UPDATE_INTERVAL_MS) {
            int lo = Math.round(minFps.getValue());
            int hi = Math.round(maxFps.getValue());

            if (lo > hi) {
                int tmp = lo;
                lo = hi;
                hi = tmp;
            }
            int span = hi - lo + 1;
            if (span <= 0) span = 1;
            lastFps = lo + random.nextInt(span);
            lastUpdateMs = now;
        }
        return lastFps;
    }
}
