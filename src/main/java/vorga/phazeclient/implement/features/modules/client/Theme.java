package vorga.phazeclient.implement.features.modules.client;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.color.ColorPalette;
import vorga.phazeclient.base.util.color.DarkPalette;
import vorga.phazeclient.implement.menu.MenuPalette;
import vorga.phazeclient.implement.menu.MenuPalettes;
import vorga.phazeclient.implement.menu.MenuStyle;

public final class Theme extends Module {
    private static final Theme INSTANCE = new Theme();

    public static Theme getInstance() {
        return INSTANCE;
    }

    private ColorPalette currentPalette = new DarkPalette();

    public final SelectSetting menuTheme = new SelectSetting("Theme", "Lunar menu theme preset")
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
                    "Polar Night"
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
        // Use logarithmic scaling to reduce impact at high values
        // value 0-32 -> effective blur 0-12 (non-linear)
        return (float) (Math.log1p(value) * 2.5);
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
        currentPalette = new DarkPalette();
    }
}
