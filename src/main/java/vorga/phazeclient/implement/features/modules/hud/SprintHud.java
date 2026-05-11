package vorga.phazeclient.implement.features.modules.hud;

public final class SprintHud extends RectHudModule {
    private static final SprintHud INSTANCE = new SprintHud();

    public static SprintHud getInstance() {
        return INSTANCE;
    }

    private SprintHud() {
        super("sprint_hud", "Sprint HUD", 22.0f, 96.0f, 1.0f);
        // Sprint HUD now formats its own state strings without the
        // square-bracket wrappers (e.g. "Sprinting (AutoSprint)" rather
        // than "[Sprinting (AutoSprint)]"), so the parent-registered
        // Show Brackets toggle would be a no-op redundancy. Hiding it
        // from the settings panel keeps the UI honest.
        showBrackets.visible(() -> false);
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
