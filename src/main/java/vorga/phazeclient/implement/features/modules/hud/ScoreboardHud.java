package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ScoreboardHud extends RectHudModule {
    private static final ScoreboardHud INSTANCE = new ScoreboardHud();

    public static ScoreboardHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting showNumbers = new BooleanSetting("Show Numbers", "Show score numbers on the right").setValue(true);
    public final BooleanSetting showTitle = new BooleanSetting("Show Title", "Show scoreboard title").setValue(true);

    private ScoreboardHud() {
        // Default position: right side, vertically centered
        super("scoreboard_hud", "Scoreboard", 0.0f, 0.0f, 1.5f);
        showNumbers.setFullWidth(true);
        showTitle.setFullWidth(true);
        setup(otherSection, showNumbers, showTitle);
    }

    @Override
    public String getDescription() {
        return "Modify vanilla scoreboard with blur, custom colors, position, and scale";
    }

    @Override
    public String getIcon() {
        return "scoreboard_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public float getMinHudScale() {
        return 1.0f;
    }

    public boolean shouldUseVanillaColors() {
        return background.isValue() && isVanillaPreset();
    }
}
