package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.awt.Color;

public class GlintColorizer extends Module {
    private static final GlintColorizer INSTANCE = new GlintColorizer();

    public final SectionSetting generalSection = new SectionSetting("General");

    public final SelectSetting mode = new SelectSetting("Mode", "Glint color mode")
            .value("Custom", "Rainbow", "Turquoise", "Golden", "Ruby")
            .selected("Custom");
    public final ColorSetting customColor = new ColorSetting("Color", "Custom glint color").value(0xFF00FFCC)
            .visible(() -> mode.isSelected("Custom"));
    public final ValueSetting speed = new ValueSetting("Speed", "Glint movement and color animation speed") {
        @Override
        public boolean isFullWidth() {
            return mode.isSelected("Custom");
        }
    }
            .range(0.2f, 4.0f).step(0.1f).setValue(1.0f);

    private GlintColorizer() {
        super("glint_colorizer", "Custom Glint", ModuleCategory.OTHER);
        setup(generalSection, mode, customColor, speed);
    }

    public static GlintColorizer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Custom color and animation speed for enchanted item and armor glint";
    }

    @Override
    public String getIcon() {
        return "color_correction.png";
    }

    public int getActiveGlintColor() {
        if (!isEnabled()) return 0xFF9933FF;
        switch (mode.getSelected()) {
            case "Rainbow":
                float cycle = Math.max(0.2f, speed.getValue());
                float hue = (float) ((System.currentTimeMillis() % (long) (5000.0f / cycle)) / (5000.0f / cycle));
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
