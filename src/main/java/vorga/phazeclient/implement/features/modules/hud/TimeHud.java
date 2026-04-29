package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class TimeHud extends RectHudModule {
    private static final TimeHud INSTANCE = new TimeHud();

    public final SectionSetting timeSection = new SectionSetting("Time");
    public final BooleanSetting hour24 = new BooleanSetting("24 Hour Format", "Use 24-hour time format").setValue(false);
    public final BooleanSetting showAmPm = new BooleanSetting("Show AM/PM", "Show AM/PM suffix in 12-hour mode")
            .setValue(true)
            .visible(() -> !hour24.isValue());

    public static TimeHud getInstance() {
        return INSTANCE;
    }

    private TimeHud() {
        super("time_hud", "Time", 22.0f, 442.0f, 1.0f);
        hour24.setFullWidth(true);
        showAmPm.setFullWidth(true);
        setup(timeSection, hour24, showAmPm);
    }

    @Override
    public String getDescription() {
        return "Shows real local time";
    }
}

