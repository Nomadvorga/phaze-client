package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class WailaHud extends RectHudModule {
    private static final WailaHud INSTANCE = new WailaHud();

    public static WailaHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting infoSection = new SectionSetting("Info Display");
    public final BooleanSetting showIcon = new BooleanSetting("Show Icon", "Show block/entity icon").setValue(true);
    public final BooleanSetting showCorrectTool = new BooleanSetting("Show Correct Tool", "Show best tool for mining").setValue(true);
    public final BooleanSetting showCoordinates = new BooleanSetting("Show Coordinates", "Show block coordinates").setValue(true);
    public final BooleanSetting showBreakTime = new BooleanSetting("Show Break Time", "Show time to break block").setValue(true);
    public final BooleanSetting alwaysShow = new BooleanSetting("Always Show", "Show HUD even when not targeting anything").setValue(true);
    public final BooleanSetting showEntities = new BooleanSetting("Show Entities", "Show info when targeting entities").setValue(true);

    private WailaHud() {
        super("waila_hud", "Waila", 22.0f, 544.0f, 1.0f);
        showIcon.setFullWidth(true);
        showCorrectTool.setFullWidth(true);
        showCoordinates.setFullWidth(true);
        showBreakTime.setFullWidth(true);
        alwaysShow.setFullWidth(true);
        showEntities.setFullWidth(true);
        setup(showBrackets, infoSection, showIcon, showCorrectTool, showCoordinates, showBreakTime, alwaysShow, showEntities);
    }

    @Override
    public String getDescription() {
        return "Shows block/entity info when targeting";
    }

    @Override
    public String getIcon() {
        return "waila.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
