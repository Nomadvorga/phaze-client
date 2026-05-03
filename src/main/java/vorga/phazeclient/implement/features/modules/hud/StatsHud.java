package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class StatsHud extends RectHudModule {
    private static final StatsHud INSTANCE = new StatsHud();

    public final SectionSetting statsSection = new SectionSetting("Stats");
    public final BooleanSetting showPercent = new BooleanSetting("Show Percent", "Show share of HUD time").setValue(true);
    public final BooleanSetting showMs = new BooleanSetting("Show Milliseconds", "Show timing in milliseconds").setValue(true);
    public final ValueSetting topCount = new ValueSetting("Top Count", "How many heaviest HUD parts to display")
            .range(1, 10)
            .setValue(10);
    public final ValueSetting sampleSmoothing = new ValueSetting("Smoothing", "Smoothing amount for stats")
            .range(5, 95)
            .setValue(70);

    public static StatsHud getInstance() {
        return INSTANCE;
    }

    private StatsHud() {
        super("stats_hud", "Stats", 22.0f, 140.0f, 1.0f);
        showPercent.setFullWidth(true);
        showMs.setFullWidth(true);
        topCount.setFullWidth(true);
        sampleSmoothing.setFullWidth(true);
        setup(
                textShadow, colorSection, background, backgroundPreset, colorBrightness, backgroundOpacity, backgroundBlurRadius,
                statsSection, showPercent, showMs, topCount, sampleSmoothing
        );
    }

    @Override
    public String getDescription() {
        return "Shows what HUD parts cost most FPS";
    }

    @Override
    public String getIcon() {
        return "fps_hud.png";
    }
}
