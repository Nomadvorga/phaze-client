package vorga.phazeclient.implement.features.modules.hud;

public final class KeystrokesHud extends RectHudModule {
    private static final KeystrokesHud INSTANCE = new KeystrokesHud();

    public static KeystrokesHud getInstance() {
        return INSTANCE;
    }

    private KeystrokesHud() {
        super("keystrokes_hud", "Keystrokes", 22.0f, 218.0f, 1.0f);
    }

    @Override
    public String getDescription() {
        return "Shows movement and mouse buttons on HUD";
    }

    @Override
    public String getIcon() {
        return "keystrokes_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
