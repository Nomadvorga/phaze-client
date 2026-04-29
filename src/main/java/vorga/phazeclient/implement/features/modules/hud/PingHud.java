package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class PingHud extends RectHudModule {
    private static final PingHud INSTANCE = new PingHud();

    public static PingHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final ValueSetting updateInterval = new ValueSetting("Update Time", "How often the ping value updates (seconds)")
            .range(1, 10)
            .setValue(2);

    private int cachedPing = -1;
    private long lastUpdateTimeMs = 0;

    private PingHud() {
        super("ping_hud", "Ping", 22.0f, 190.0f, 1.0f);
        updateInterval.setFullWidth(true);
        setup(otherSection, updateInterval);
    }

    public int getCachedPing() {
        return cachedPing;
    }

    public void updatePing(int latency) {
        if (latency <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long intervalMs = (long) (updateInterval.getValue() * 1000L);
        if (cachedPing <= 0 || now - lastUpdateTimeMs >= intervalMs) {
            cachedPing = latency;
            lastUpdateTimeMs = now;
        }
    }

    public void resetPingCache() {
        cachedPing = -1;
        lastUpdateTimeMs = 0;
    }

    @Override
    public String getDescription() {
        return "Shows server ping on HUD";
    }

    @Override
    public String getIcon() {
        return "ping_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
