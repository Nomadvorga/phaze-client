package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class FpsHud extends RectHudModule {
    private static final FpsHud INSTANCE = new FpsHud();

    public static FpsHud getInstance() {
        return INSTANCE;
    }

    public final SectionSetting otherSection = new SectionSetting("Other");

    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Show value before label, e.g. \"60 FPS\" instead of \"FPS: 60\"").setValue(false);

    private FpsHud() {
        super("fps_hud", "FPS");
        reverseOrder.setFullWidth(true);
        setup(otherSection, reverseOrder, cornerRounding);
    }

    @Override
    public String getDescription() {
        return "Shows current FPS on HUD";
    }

    @Override
    public String getIcon() {
        return "fps_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
