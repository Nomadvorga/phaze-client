package vorga.phazeclient.implement.features.modules.hud;

public final class SprintHud extends RectHudModule {
    private static final SprintHud INSTANCE = new SprintHud();

    public static SprintHud getInstance() {
        return INSTANCE;
    }

    private SprintHud() {
        super("sprint_hud", "Sprint HUD", 22.0f, 96.0f, 1.0f);
    }

    @Override
    public String getDescription() {
        return "Shows current sprint, sneak, and flying state";
    }

    @Override
    public String getIcon() {
        return "sprint_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
