package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.awt.Color;

public class GlintColorizer extends Module {
    private static final GlintColorizer INSTANCE = new GlintColorizer();

    public final SelectSetting mode = new SelectSetting("Mode", "Glint color mode")
            .value("Custom", "Rainbow", "Turquoise", "Golden", "Ruby")
            .selected("Custom");
    public final ColorSetting customColor = new ColorSetting("Color", "Custom glint color").value(0xFF00FFCC);
    public final ValueSetting rainbowSpeed = new ValueSetting("Speed", "Rainbow cycling speed").range(0.2f, 3.0f).step(0.1f).setValue(1.0f);

    private GlintColorizer() {
        super("glint_colorizer", "Custom Glint", ModuleCategory.OTHER);
        setup(mode, customColor, rainbowSpeed);
    }

    public static GlintColorizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Custom color and rainbow animation for enchanted item glint";
    }

    @Override
    public String getIcon() {
        return "color_correction.png";
    }

    public int getActiveGlintColor() {
        if (!isEnabled()) return 0xFF9933FF;
        switch (mode.getSelected()) {
            case "Rainbow":
                float hue = (float) ((System.currentTimeMillis() % (long) (5000.0f / rainbowSpeed.getValue())) / (5000.0f / rainbowSpeed.getValue()));
                return Color.HSBtoRGB(hue, 0.85f, 1.0f);
            case "Turquoise":
                return 0xFF00FFCC;
            case "Golden":
                return 0xFFFFD700;
            case "Ruby":
                return 0xFFFF3366;
            default:
                return customColor.getColor();
        }
    }
}
