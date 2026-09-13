package vorga.phazeclient.api.system.colorcorrection;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.system.motionblur.PostEffectShader;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;

public class ColorCorrectionShader {
    private final ColorCorrection config;
    private final PostEffectShader shader;

    public ColorCorrectionShader(ColorCorrection config) {
        this.config = config;
        this.shader = new PostEffectShader(
                Identifier.of("phazeclient", "color_correction")
        );
        this.shader.useCopyOutput(Identifier.of("phazeclient", "color_output"));

        this.shader.declareUniformBlock("ColorCorrectionConfig",
                "Brightness", "Contrast", "Saturation", "Hue",
                "Gamma", "Temperature", "Vibrance");
    }

    private float lastBrightness = Float.NaN;
    private float lastContrast = Float.NaN;
    private float lastSaturation = Float.NaN;
    private float lastHue = Float.NaN;
    private float lastGamma = Float.NaN;
    private float lastTemperature = Float.NaN;
    private float lastVibrance = Float.NaN;

    private boolean isIdentity() {
        return config.getBrightness() == 0.0F
                && config.getContrast() == 1.0F
                && config.getSaturation() == 1.0F
                && config.getHue() == 0.0F
                && config.getGamma() == 1.0F
                && config.getTemperature() == 0.0F
                && config.getVibrance() == 0.0F;
    }

    public void apply() {
        if (!config.isEnabled()) return;

        if (isIdentity()) return;

        float brightness = config.getBrightness();
        float contrast = config.getContrast();
        float saturation = config.getSaturation();
        float hue = config.getHue();
        float gamma = config.getGamma();
        float temperature = config.getTemperature();
        float vibrance = config.getVibrance();

        if (brightness != lastBrightness) {
            shader.setUniformValue("Brightness", brightness);
            lastBrightness = brightness;
        }
        if (contrast != lastContrast) {
            shader.setUniformValue("Contrast", contrast);
            lastContrast = contrast;
        }
        if (saturation != lastSaturation) {
            shader.setUniformValue("Saturation", saturation);
            lastSaturation = saturation;
        }
        if (hue != lastHue) {
            shader.setUniformValue("Hue", hue);
            lastHue = hue;
        }
        if (gamma != lastGamma) {
            shader.setUniformValue("Gamma", gamma);
            lastGamma = gamma;
        }
        if (temperature != lastTemperature) {
            shader.setUniformValue("Temperature", temperature);
            lastTemperature = temperature;
        }
        if (vibrance != lastVibrance) {
            shader.setUniformValue("Vibrance", vibrance);
            lastVibrance = vibrance;
        }

        shader.render(0.0f);

    }

    public void reload() {
        shader.reload();

        lastBrightness = Float.NaN;
        lastContrast = Float.NaN;
        lastSaturation = Float.NaN;
        lastHue = Float.NaN;
        lastGamma = Float.NaN;
        lastTemperature = Float.NaN;
        lastVibrance = Float.NaN;
    }
}
