package vorga.phazeclient.implement.features.modules.client;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
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

    private Theme() {
        super("themes", "Themes", ModuleCategory.OTHER, false, false);
        setup(menuTheme, blurRadius);

        applyTheme();
        applyMenuTheme();
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
        return 1.25f;
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
