package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;

public final class FpsHud extends RectHudModule {
    private static final FpsHud INSTANCE = new FpsHud();

    public static FpsHud getInstance() {
        return INSTANCE;
    }

    /**
     * Swap the {@code FPS} label position. Default OFF renders
     * {@code "FPS: 60"}; ON renders {@code "60 FPS"}.
     */
    public final BooleanSetting reverseOrder = new BooleanSetting("Reverse Order", "Show value before label, e.g. \"60 FPS\" instead of \"FPS: 60\"").setValue(false);

    private FpsHud() {
        super("fps_hud", "FPS");
        reverseOrder.setFullWidth(true);
        setup(reverseOrder);
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
