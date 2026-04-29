package vorga.phazeclient.implement.features.modules.hud;

public final class PotionHud extends RectHudModule {
    private static final PotionHud INSTANCE = new PotionHud();

    public static PotionHud getInstance() {
        return INSTANCE;
    }

    private PotionHud() {
        super("potion_hud", "Potions", 22.0f, 286.0f, 1.0f);
    }

    @Override
    public String getDescription() {
        return "Shows active potion effects on HUD";
    }

    @Override
    public String getIcon() {
        return "potion_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
