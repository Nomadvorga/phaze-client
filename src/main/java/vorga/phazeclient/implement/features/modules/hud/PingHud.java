package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class PingHud extends RectHudModule {
    private static final PingHud INSTANCE = new PingHud();

    public static PingHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting dynamicPingColor = new BooleanSetting("Dynamic Ping Color", "Color ping by thresholds").setValue(true);

    private int cachedPing = -1;

    private PingHud() {
        super("ping_hud", "Ping", 22.0f, 190.0f, 1.0f);
        dynamicPingColor.setFullWidth(true);
        setup(otherSection, dynamicPingColor);
    }

    public int getCachedPing() {
        return cachedPing;
    }

    public void updatePing(int latency) {
        // Update every frame for real-time ping display
        cachedPing = Math.max(0, latency);
    }

    public void resetPingCache() {
        cachedPing = -1;
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
