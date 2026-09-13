package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class PotionHud extends RectHudModule {
    private static final PotionHud INSTANCE = new PotionHud();

    public final SectionSetting colorsSection = new SectionSetting("Colors");
    public final BooleanSetting colorByType = new BooleanSetting(
            "Color By Type",
            "Tint the effect name by category: green = beneficial, red = harmful, white = neutral"
    ).setValue(true);
    public final BooleanSetting flashOnExpiry = new BooleanSetting(
            "Flash On Expiry",
            "Pulse the effect line when it has less than 10 seconds left"
    ).setValue(true);

    public static PotionHud getInstance() {
        return INSTANCE;
    }

    private PotionHud() {
        super("potion_hud", "Potions", 22.0f, 286.0f, 1.0f);
        colorByType.setFullWidth(true);
        flashOnExpiry.setFullWidth(true);
        setup(colorsSection, colorByType, flashOnExpiry, otherSection, cornerRounding);
    }

    @Override
    public String getDescription() {
        return "Shows active potion effects on HUD with optional buff/debuff color coding";
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
