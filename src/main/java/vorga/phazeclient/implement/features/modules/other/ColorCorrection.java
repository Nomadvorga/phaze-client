package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.system.colorcorrection.ColorCorrectionShader;

public final class ColorCorrection extends Module {
    private static final ColorCorrection INSTANCE = new ColorCorrection();

    public final SectionSetting mainSection = new SectionSetting("General");
    public final ValueSetting brightness = new ValueSetting("Brightness", "Adjust brightness").range(-1.0f, 1.0f).setValue(0.0f);
    public final ValueSetting contrast = new ValueSetting("Contrast", "Adjust contrast").range(0.0f, 3.0f).setValue(1.0f);
    public final ValueSetting saturation = new ValueSetting("Saturation", "Adjust saturation").range(0.0f, 3.0f).setValue(1.0f);
    public final ValueSetting hue = new ValueSetting("Hue", "Rotate colors around the hue wheel").range(-1.0f, 1.0f).step(0.01f).setValue(0.0f);
    public final ValueSetting gamma = new ValueSetting("Gamma", "Adjust gamma").range(0.2f, 3.0f).setValue(1.0f);
    public final ValueSetting temperature = new ValueSetting("Temperature", "Warm/cool tint").range(-1.0f, 1.0f).setValue(0.0f);
    public final ValueSetting vibrance = new ValueSetting("Vibrance", "Boost muted colors").range(0.0f, 2.0f).setValue(0.0f);

    public final ColorCorrectionShader shader;

    private ColorCorrection() {
        super("color_correction", "Color Correction", ModuleCategory.OTHER, true, false);

        brightness.setFullWidth(true);
        contrast.setFullWidth(true);
        saturation.setFullWidth(true);
        hue.setFullWidth(true);
        gamma.setFullWidth(true);
        temperature.setFullWidth(true);
        vibrance.setFullWidth(true);

        setup(mainSection, brightness, contrast, saturation, hue, gamma, temperature, vibrance);

        shader = new ColorCorrectionShader(this);
    }

    public static ColorCorrection getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Color correction post-process effect";
    }

    @Override
    public String getIcon() {
        return "color_correction.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public float getBrightness() { return brightness.getValue(); }
    public float getContrast() { return contrast.getValue(); }
    public float getSaturation() { return saturation.getValue(); }
    public float getHue() { return hue.getValue(); }
    public float getGamma() { return gamma.getValue(); }
    public float getTemperature() { return temperature.getValue(); }
    public float getVibrance() { return vibrance.getValue(); }
}
