package vorga.phazeclient.implement.features.modules.client;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.color.ColorPalette;
import vorga.phazeclient.base.util.color.ThemeColorPalette;
import vorga.phazeclient.implement.menu.MenuPalette;
import vorga.phazeclient.implement.menu.MenuPalettes;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.MenuUiSettings;

public final class Theme extends Module {
    private static final Theme INSTANCE = new Theme();

    public static Theme getInstance() {
        return INSTANCE;
    }

    private ColorPalette currentPalette = new ThemeColorPalette(MenuPalettes.LUNAR_BLUE);
    private boolean guiScaleAdjustmentActive;
    private float pendingGuiScale = MenuUiSettings.DEFAULT_GUI_SCALE;

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

    public final ValueSetting guiScale = new ValueSetting("GUI Scale", "Scale the entire Phaze GUI")
            .range(MenuUiSettings.MIN_GUI_SCALE, MenuUiSettings.MAX_GUI_SCALE)
            .step(0.1F)
            .setValue(MenuUiSettings.DEFAULT_GUI_SCALE)
            .onChange(this::applyGuiScale);

    public final ColorSetting hudTextColor = new ColorSetting(
            "Hud Text Color",
            "Default color for HUD text that does not use its own dynamic tint"
    )
            .value(0xFFFFFFFF)
            .noAlpha()
            .popupRow();

    public final SelectSetting language = new SelectSetting("Language", "UI language for menu strings")
            .value(Lang.EN, Lang.RU)
            .selected(Lang.EN);

    public final BooleanSetting renderOtherPlayerCosmetics = new BooleanSetting(
            "Render Other Player Cosmetics",
            "Render Phaze cosmetics equipped by other players"
    ).setValue(true);

    private Theme() {
        super("themes", "Themes", ModuleCategory.OTHER, false, false);
        hudTextColor.setFullWidth(true);
        language.setFullWidth(true);
        guiScale.setFullWidth(true);
        renderOtherPlayerCosmetics.setFullWidth(true);
        setup(menuTheme, blurRadius, language, hudTextColor, guiScale, renderOtherPlayerCosmetics);

        Lang.setActive(language.getSelected());

        applyTheme();
        applyMenuTheme();
    }

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

        return (float) (Math.log1p(value) * 1.5);
    }

    public float getHudBlurQualityMultiplier() {

        return 0.03f + (0.70f * 0.70f * 0.97f);
    }

    public int getHudBlurMode() {

        return 2;
    }

    public float getHudBlurRadiusMultiplier() {
        return 2.5f;
    }

    public int getHudTextColor() {
        return 0xFF000000 | (hudTextColor.getColor() & 0x00FFFFFF);
    }

    private void applyGuiScale(float value) {
        float clamped = Math.max(MenuUiSettings.MIN_GUI_SCALE, Math.min(MenuUiSettings.MAX_GUI_SCALE, value));
        if (Float.compare(value, clamped) != 0) {
            guiScale.setValue(clamped);
            return;
        }
        pendingGuiScale = clamped;
        if (!guiScaleAdjustmentActive) {
            MenuUiSettings.getInstance().setGuiScale(clamped);
        }
    }

    public void beginGuiScaleAdjustment() {
        guiScaleAdjustmentActive = true;
        pendingGuiScale = guiScale.getValue();
    }

    public void endGuiScaleAdjustment() {
        if (!guiScaleAdjustmentActive) {
            return;
        }
        guiScaleAdjustmentActive = false;
        applyGuiScale(pendingGuiScale);
    }

    @Override
    public String getDescription() {
        return "Custom themes for GUI";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public boolean showIconInSettings() {
        return false;
    }

    public void applyMenuTheme() {
        MenuStyle.apply(getCurrentMenuPalette());
    }

    public void applyTheme() {

        currentPalette = new ThemeColorPalette(getCurrentMenuPalette());
    }
}
