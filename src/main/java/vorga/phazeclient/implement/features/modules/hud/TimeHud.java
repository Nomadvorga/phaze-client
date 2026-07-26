package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Real wall-clock time HUD. Renders through the existing batched
 * HUD pipeline ({@code InGameHudMixin#renderSimpleTextHud}) which
 * handles drag, scale, background, and bracket wrapping uniformly
 * across every text HUD module.
 *
 * <h3>Settings</h3>
 * <ul>
 *   <li>{@code 24 Hour Format} - on by default flips to 24h time.</li>
 *   <li>{@code Show AM/PM} - 12h-only modifier.</li>
 *   <li>{@code Show Seconds} - extra extension for both formats.</li>
 *   <li>{@code Show Day Phase} - appends the in-game day phase
 *       label (Morning / Day / Dusk / Night) computed from the
 *       client's world time.</li>
 *   <li>{@code Color Phase} - tints the appended phase label by
 *       phase: morning yellow, day white, dusk orange, night blue.
 *       Implemented via in-message legacy color codes so the
 *       single-line batched renderer doesn't need a per-segment
 *       color path.</li>
 * </ul>
 */
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
            "Append the in-game day phase (Morning / Day / Dusk / Night)"
    ).setValue(false);
    public final BooleanSetting colorPhase = new BooleanSetting(
            "Color Phase",
            "Tint the day-phase label by phase (Morning yellow, Day white, Dusk orange, Night blue)"
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
                phaseSection, showPhase, colorPhase);
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

    /**
     * Decode the world time-of-day tick into one of four phases.
     * Boundaries match vanilla's lighting transitions: 0..6000 =
     * Morning (sunrise), 6000..12000 = Day, 12000..13800 = Dusk
     * (the brief sunset window), 13800..23999 = Night.
     */
    public Phase phaseForTime(long timeOfDay) {
        long t = ((timeOfDay % 24000L) + 24000L) % 24000L;
        if (t < 6000L) return Phase.MORNING;
        if (t < 12000L) return Phase.DAY;
        if (t < 13800L) return Phase.DUSK;
        return Phase.NIGHT;
    }

    /** Phases mapped to display strings + legacy colour codes so
     *  the single-line text renderer can tint the suffix without
     *  needing a per-segment color path. */
    public enum Phase {
        MORNING("Morning", "§e"),
        DAY("Day", "§f"),
        DUSK("Dusk", "§6"),
        NIGHT("Night", "§9");

        public final String label;
        public final String colorCode;

        Phase(String label, String colorCode) {
            this.label = label;
            this.colorCode = colorCode;
        }
    }
}
