package vorga.phazeclient.implement.features.modules.client;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.color.ColorPalette;
import vorga.phazeclient.base.util.color.ThemeColorPalette;
import vorga.phazeclient.implement.menu.MenuPalette;
import vorga.phazeclient.implement.menu.MenuPalettes;
import vorga.phazeclient.implement.menu.MenuStyle;

public final class Theme extends Module {
    private static final Theme INSTANCE = new Theme();

    public static Theme getInstance() {
        return INSTANCE;
    }

    private ColorPalette currentPalette = new ThemeColorPalette(MenuPalettes.LUNAR_BLUE);

    public final SelectSetting menuTheme = new SelectSetting("Theme", "Menu & HUD theme preset")
            .value(
                    "Lunar Blue",
                    "Mocha Gold",
                    "Rose Quartz",
                    "Emerald Frost",
                    "Arctic Mint",
                    "Crimson Silk",
                    "Solar Ember",
                    "Midnight Bloom",
                    "Desert Mirage",
                    "Sapphire Steel",
                    "Velvet Plum",
                    "Frosted Peach",
                    "Moss Smoke",
                    "Polar Night",
                    "Snow",
                    "Obsidian",
                    "Nebula",
                    "Coral",
                    "Jade",
                    "Sunset",
                    "Violet",
                    "Ocean"
            )
            .selected("Lunar Blue");

    public final ValueSetting blurRadius = new ValueSetting("Blur Radius", "Adjust background blur strength")
            .range(0, 32)
            .setValue(16);

    public final ColorSetting hudTextColor = new ColorSetting(
            "Hud Text Color",
            "Default color for HUD text that does not use its own dynamic tint"
    )
            .value(0xFFFFFFFF)
            .noAlpha()
            .popupRow();

    /**
     * UI language for the menu's user-facing strings (modals, kebab
     * popups, etc). English is the default; selecting Russian flips
     * {@link Lang} to its RU table on the next render frame.
     * Module / category names are NOT translated - the user
     * explicitly asked for those to stay in their canonical English
     * form regardless of locale.
     */
    public final SelectSetting language = new SelectSetting("Language", "UI language for menu strings")
            .value(Lang.EN, Lang.RU)
            .selected(Lang.EN);

    private Theme() {
        super("themes", "Themes", ModuleCategory.OTHER, false, false);
        hudTextColor.setFullWidth(true);
        language.setFullWidth(true);
        setup(menuTheme, blurRadius, language, hudTextColor);

        // Push the initial selection through to the Lang table so
        // any code reading {@link Lang#t} during boot sees the
        // configured locale, not the default. Subsequent changes
        // are picked up on each modal render via syncLanguage().
        Lang.setActive(language.getSelected());

        applyTheme();
        applyMenuTheme();
    }

    /**
     * Re-syncs {@link Lang#setActive} with the SelectSetting's
     * current value. Called from the modal's render path so a
     * mid-game language switch takes effect immediately without a
     * restart. Cheap (volatile write) so per-frame is fine.
     */
    public void syncLanguage() {
        Lang.setActive(language.getSelected());
    }

    public float getFadeSpeed() {
        return 1.0f;
    }

    public int[] getClientColors() {
        MenuPalette palette = getCurrentMenuPalette();
        return new int[]{palette.chipActive(), palette.chipNew(), palette.accentGreen()};
    }

    public ColorPalette getCurrentPalette() {
        applyTheme();
        return currentPalette;
    }

    public MenuPalette getCurrentMenuPalette() {
        return MenuPalettes.byName(menuTheme.getSelected());
    }

    public float getMenuBlurRadius() {
        float value = blurRadius.getValue();
        // Softer logarithmic scaling - the slider's upper half has
        // strong diminishing returns so raw value 32 tops out around
        // ~5.3 effective radius (previously ~8.75) instead of turning
        // the backdrop into a mushy wash. Low values feel roughly the
        // same because log1p stays near-linear there.
        // value 0-32 -> effective blur 0-5.3 (non-linear)
        return (float) (Math.log1p(value) * 1.5);
    }

    public float getHudBlurQualityMultiplier() {
        // Fixed quality = 70.0
        return 0.03f + (0.70f * 0.70f * 0.97f);
    }

    public int getHudBlurMode() {
        // Fixed blur type = Kawase
        return 2;
    }

    public float getHudBlurRadiusMultiplier() {
        return 2.5f;
    }

    public int getHudTextColor() {
        return 0xFF000000 | (hudTextColor.getColor() & 0x00FFFFFF);
    }

    @Override
    public String getDescription() {
        return "Custom themes for GUI";
    }

    @Override
    public boolean isVisible() {
        return false; // Hidden from module list, but accessible via search
    }

    @Override
    public boolean showIconInSettings() {
        return false;
    }

    public void applyMenuTheme() {
        MenuStyle.apply(getCurrentMenuPalette());
    }

    public void applyTheme() {
        // HUD palette is derived directly from the selected menu
        // palette via ThemeColorPalette, so picking e.g. "Snow" in the
        // dropdown automatically lightens HUD surfaces too. Previously
        // the HUD was locked to DarkPalette regardless of selection.
        currentPalette = new ThemeColorPalette(getCurrentMenuPalette());
    }
}
