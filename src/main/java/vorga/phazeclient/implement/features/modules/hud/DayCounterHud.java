package vorga.phazeclient.implement.features.modules.hud;

public final class DayCounterHud extends RectHudModule {
    private static final DayCounterHud INSTANCE = new DayCounterHud();

    public static DayCounterHud getInstance() {
        return INSTANCE;
    }

    private DayCounterHud() {
        super("day_counter_hud", "Day Counter", 22.0f, 338.0f, 1.0f);
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
