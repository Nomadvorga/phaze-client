package vorga.phazeclient.implement.features.modules.hud;

public final class CpsHud extends RectHudModule {
    private static final CpsHud INSTANCE = new CpsHud();

    public static CpsHud getInstance() {
        return INSTANCE;
    }

    private CpsHud() {
        super("cps_hud", "CPS");
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
