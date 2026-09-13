package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class TimeHud extends RectHudModule {
    private static final TimeHud INSTANCE = new TimeHud();

    public final SectionSetting timeSection = new SectionSetting("Time");
    public final BooleanSetting hour24 = new BooleanSetting(
            "24 Hour Format",
            "Use 24-hour time format"
    ).setValue(false);
    public final BooleanSetting showAmPm = new BooleanSetting(
            "Show AM/PM",
            "Show AM/PM suffix in 12-hour mode"
    ).setValue(true)
            .visible(() -> !hour24.isValue());
    public final BooleanSetting showSeconds = new BooleanSetting(
            "Show Seconds",
            "Append seconds to the displayed time (HH:mm:ss)"
    ).setValue(false);

    public final SectionSetting phaseSection = new SectionSetting("Day Phase");
    public final BooleanSetting showPhase = new BooleanSetting(
            "Show Day Phase",
            "Append the real-time day phase (Morning / Day / Evening / Night)"
    ).setValue(false);
    public final BooleanSetting colorPhase = new BooleanSetting(
            "Color Phase",
            "Tint the day-phase label by phase (Morning yellow, Day white, Evening orange, Night blue)"
    ).setValue(true)
            .visible(() -> showPhase.isValue());

    public static TimeHud getInstance() {
        return INSTANCE;
    }

    private TimeHud() {
        super("time_hud", "Time", 22.0f, 442.0f, 1.0f);
        hour24.setFullWidth(true);
        showAmPm.setFullWidth(true);
        showSeconds.setFullWidth(true);
        showPhase.setFullWidth(true);
        colorPhase.setFullWidth(true);
        setup(timeSection, hour24, showAmPm, showSeconds,
                phaseSection, showPhase, colorPhase, otherSection, cornerRounding);
    }

    @Override
    public String getDescription() {
        return "Shows real local time, optionally with seconds and the in-game day phase";
    }

    @Override
    public String getIcon() {
        return "time_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public Phase phaseForTime(long timeOfDay) {

        long t = ((timeOfDay % 24000L) + 24000L) % 24000L;
        if (t < 6000L) return Phase.MORNING;
        if (t < 12000L) return Phase.DAY;
        if (t < 13800L) return Phase.EVENING;
        return Phase.NIGHT;
    }

    public Phase phaseForClock(int hour) {
        if (hour < 4) return Phase.NIGHT;
        if (hour < 12) return Phase.MORNING;
        if (hour < 18) return Phase.DAY;
        if (hour < 22) return Phase.EVENING;
        return Phase.NIGHT;
    }

    public enum Phase {
        MORNING("Morning", "§e"),
        DAY("Day", "§f"),
        EVENING("Evening", "§6"),
        NIGHT("Night", "§9");

        public final String label;
        public final String colorCode;

        Phase(String label, String colorCode) {
            this.label = label;
            this.colorCode = colorCode;
        }
    }
}
