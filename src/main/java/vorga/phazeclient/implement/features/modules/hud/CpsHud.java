package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public final class CpsHud extends RectHudModule {
    private static final CpsHud INSTANCE = new CpsHud();

    public static CpsHud getInstance() {
        return INSTANCE;
    }

    public final BooleanSetting reverseText = new BooleanSetting("Reverse Text", "Move CPS text to the other side from CPS value").setValue(false);
    public final BooleanSetting showCpsText = new BooleanSetting("Show CPS Text", "Show or hide the CPS text").setValue(true);
    public final BooleanSetting rightClickCps = new BooleanSetting("Right Click CPS", "Show right click CPS alongside left click").setValue(false);

    private CpsHud() {
        super("cps_hud", "CPS");
        setup(reverseText, showCpsText, rightClickCps);
    }

    @Override
    public String getDescription() {
        return "Shows current clicks per second on HUD";
    }

    @Override
    public String getIcon() {
        return "cps_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
