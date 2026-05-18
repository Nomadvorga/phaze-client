package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Active potion-effect HUD with icon + name + duration. The actual
 * rendering pipeline lives in {@code InGameHudMixin#renderPotionHud}
 * which uses these knobs to decide what each row contains and how
 * to color it.
 *
 * <h3>Settings</h3>
 * <ul>
 *   <li><b>Color By Type</b> - tint the effect NAME by category:
 *       beneficial = green, harmful = red, neutral = white. Uses
 *       fixed colours; no per-side picker so the toggle stays a
 *       single boolean.</li>
 *   <li><b>Flash On Expiry</b> - pulse the effect name alpha when
 *       there's less than 10 seconds remaining so the user
 *       notices the impending timeout.</li>
 * </ul>
 */
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
        setup(colorsSection, colorByType, flashOnExpiry);
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
