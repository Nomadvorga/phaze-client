package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class DayCounterHud extends RectHudModule {
    private static final DayCounterHud INSTANCE = new DayCounterHud();

    public static DayCounterHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");

    /**
     * Swap the {@code Day} label position. Default OFF preserves the
     * historical {@code "5 Days"} form (value first, with pluralised
     * label as a suffix); ON renders {@code "Day: 5"} (label first,
     * colon, plain number) for users who prefer the label-prefixed
     * style of the other HUDs.
     */
    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Show \"Day: 5\" instead of \"5 Days\"").setValue(false);

    private DayCounterHud() {
        super("day_counter_hud", "Day Counter", 22.0f, 338.0f, 1.0f);
        reverseOrder.setFullWidth(true);
        setup(otherSection, reverseOrder);
    }

    @Override
    public String getDescription() {
        return "Shows current world day";
    }

    @Override
    public String getIcon() {
        return "day_counter_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
