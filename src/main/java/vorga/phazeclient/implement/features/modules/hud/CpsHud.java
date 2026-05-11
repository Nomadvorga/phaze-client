package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class CpsHud extends RectHudModule {
    private static final CpsHud INSTANCE = new CpsHud();

    public static CpsHud getInstance() {
        return INSTANCE;
    }

    public final BooleanSetting showCpsText = new BooleanSetting("Show CPS Text", "Show or hide the CPS text").setValue(true);
    public final BooleanSetting rightClickCps = new BooleanSetting("Right Click CPS", "Show right click CPS alongside left click").setValue(false);
    public final SectionSetting otherSection = new SectionSetting("Other");
    public final BooleanSetting reverseText = new BooleanSetting("Reverse Order", "Show value before label, e.g. \"5 CPS\" instead of \"CPS: 5\"").setValue(false);

    private CpsHud() {
        super("cps_hud", "CPS");
        reverseText.setFullWidth(true);
        showCpsText.setFullWidth(true);
        rightClickCps.setFullWidth(true);
        // Reverse Order is grouped under the Other section, while
        // Show CPS Text and Right Click CPS stay in the Main column
        // because they're CPS-specific config rather than generic
        // text-layout knobs.
        setup(showCpsText, rightClickCps, otherSection, reverseText);
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
