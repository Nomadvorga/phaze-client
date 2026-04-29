package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class DirectionHud extends RectHudModule {
    private static final DirectionHud INSTANCE = new DirectionHud();

    public final SectionSetting directionSection = new SectionSetting("Direction");
    public final ValueSetting hudLength = new ValueSetting("HUD Length", "Length of direction strip")
            .range(120, 320)
            .setValue(220);
    public final BooleanSetting showIntermediate = new BooleanSetting("Show Intermediate", "Show NE/SE/SW/NW marks").setValue(true);
    public final BooleanSetting showDegreeNumber = new BooleanSetting("Show Degree Number", "Show center degree value").setValue(true);
    public final ValueSetting smoothness = new ValueSetting("Smoothness", "Compass movement smoothness")
            .range(1, 20)
            .setValue(10);

    public static DirectionHud getInstance() {
        return INSTANCE;
    }

    private DirectionHud() {
        super("direction_hud", "Direction", 22.0f, 340.0f, 1.0f);
        hudLength.setFullWidth(true);
        showIntermediate.setFullWidth(true);
        showDegreeNumber.setFullWidth(true);
        smoothness.setFullWidth(true);
        setup(directionSection, hudLength, showIntermediate, showDegreeNumber, smoothness);
    }

    @Override
    public String getDescription() {
        return "Shows animated cardinal direction strip";
    }
}

