package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class Cooldowns extends Module {
    private static final Cooldowns INSTANCE = new Cooldowns();

    public final SectionSetting generalSection = new SectionSetting("General");

    public final BooleanSetting textShadow = new BooleanSetting(
            "Text Shadow",
            "Draw the cooldown number with a vanilla-style drop shadow"
    ).setValue(true);

    public final BooleanSetting showDecimals = new BooleanSetting(
            "Show Decimals",
            "Display the remaining cooldown with one decimal place (e.g. \"1.4\") instead of rounding to whole seconds"
    ).setValue(true);

    public final BooleanSetting colorByCooldown = new BooleanSetting(
            "Color By Cooldown",
            "Color the displayed number red/yellow/green based on remaining cooldown progress"
    ).setValue(false);

    private Cooldowns() {
        super("cooldowns", "Cooldowns", ModuleCategory.HUD);
        textShadow.setFullWidth(true);
        showDecimals.setFullWidth(true);
        colorByCooldown.setFullWidth(true);
        setup(generalSection, textShadow, showDecimals, colorByCooldown);
    }

    public static Cooldowns getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Shows the remaining cooldown time as a number above each hotbar slot";
    }

    @Override
    public String getIcon() {
        return "cooldowns.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public int colorForProgress(float progress) {
        if (!colorByCooldown.isValue()) {
            return 0xFFFFFFFF;
        }
        if (progress > 0.75F) {
            return 0xFFFF5555;
        }
        if (progress > 0.25F) {
            return 0xFFFFAA00;
        }
        return 0xFF55FF55;
    }

    public String formatSeconds(float seconds) {
        if (showDecimals.isValue()) {
            return String.format(java.util.Locale.ROOT, "%.1f", seconds);
        }
        return String.valueOf(Math.max(1, Math.round(seconds)));
    }
}
