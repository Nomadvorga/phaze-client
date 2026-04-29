package vorga.phazeclient.implement.features.modules.client;

import net.minecraft.util.math.MathHelper;
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

    public final SelectSetting hudBlurType = new SelectSetting("HUD Blur Type", "Select blur algorithm for HUD")
            .value("Current", "Soup", "Kawase")
            .selected("Soup");

    public final ValueSetting hudBlurQuality = new ValueSetting("HUD Blur Quality", "Adjust HUD blur render quality")
            .range(5, 100)
            .setValue(100);

    public final SelectSetting hudFont = new SelectSetting("HUD Font", "Select HUD text font")
            .value("Vanilla", "MSDF Vanilla")
            .selected("Vanilla");

    private Theme() {
        super("themes", "Themes", ModuleCategory.CLIENT, false, false);
        hudBlurType.setFullWidth(true);
        hudBlurQuality.setFullWidth(true);
        hudFont.setFullWidth(true);
        setup(menuTheme, blurRadius, hudBlurType, hudBlurQuality, hudFont);

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
        return blurRadius.getValue();
    }

    public float getHudBlurQualityMultiplier() {
        float t = MathHelper.clamp(hudBlurQuality.getValue() / 100.0f, 0.05f, 1.0f);
        // Non-linear curve so low quality is visibly lighter/faster, high quality keeps detail.
        return 0.03f + (t * t * 0.97f);
    }

    public int getHudBlurMode() {
        if (useKawaseHudBlur()) {
            return 2;
        }
        return useSoupHudBlur() ? 1 : 0;
    }

    public boolean useSoupHudBlur() {
        return "Soup".equalsIgnoreCase(hudBlurType.getSelected());
    }

    public boolean useKawaseHudBlur() {
        return "Kawase".equalsIgnoreCase(hudBlurType.getSelected());
    }

    public float getHudBlurRadiusMultiplier() {
        if (useKawaseHudBlur()) {
            return 1.25f;
        }
        return useSoupHudBlur() ? 1.75f : 1.0f;
    }

    public boolean useMsdfHudFont() {
        return "MSDF Vanilla".equalsIgnoreCase(hudFont.getSelected());
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
