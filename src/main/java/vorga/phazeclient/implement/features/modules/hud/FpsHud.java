package vorga.phazeclient.implement.features.modules.hud;

public final class FpsHud extends RectHudModule {
    private static final FpsHud INSTANCE = new FpsHud();

    public static FpsHud getInstance() {
        return INSTANCE;
    }

    private FpsHud() {
        super("fps_hud", "FPS");
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
