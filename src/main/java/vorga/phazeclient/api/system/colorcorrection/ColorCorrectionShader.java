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
        // Must match, in order, the "ColorCorrectionConfig" list in
        // assets/phazeclient/post_effect/color_correction.json and the block in
        // assets/phazeclient/shaders/core/post/colorcorrection/color_correction.fsh.
        // All three describe one byte layout; only the JSON one is visible to
        // vanilla, so a mismatch shows up as values landing on wrong members
        // rather than as an error.
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

    /**
     * True when every parameter sits at the value that makes its stage in
     * color_correction.fsh an identity:
     * <ul>
     *   <li>{@code color += Brightness} with 0</li>
     *   <li>{@code (color - 0.5) * Contrast + 0.5} with 1</li>
     *   <li>{@code mix(lum, color, Saturation)} with 1</li>
     *   <li>vibrance scale {@code 1 + Vibrance * (1 - sat)} with 0</li>
     *   <li>{@code fract(hue + Hue)} with 0</li>
     *   <li>the temperature offsets with 0</li>
     *   <li>{@code pow(color, 1 / Gamma)} with 1</li>
     * </ul>
     */
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

        // Merely enabling the module used to cost a full-screen post-process
        // pass every frame - framebuffer bind, blit and a fragment shader over
        // every pixel - even with every slider still at its default, where the
        // shader provably outputs its input. Skipping it here is free in
        // output and gives the whole pass back.
        if (isIdentity()) return;

        float brightness = config.getBrightness();
        float contrast = config.getContrast();
        float saturation = config.getSaturation();
        float hue = config.getHue();
        float gamma = config.getGamma();
        float temperature = config.getTemperature();
        float vibrance = config.getVibrance();

        // Each setUniformValue walks every pass of the effect and looks the
        // uniform up by name, so re-sending unchanged values is pure overhead.
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
        // Program rebuilt: every uniform is back at its default, so the
        // skip-if-unchanged guards above must not suppress the next upload.
        lastBrightness = Float.NaN;
        lastContrast = Float.NaN;
        lastSaturation = Float.NaN;
        lastHue = Float.NaN;
        lastGamma = Float.NaN;
        lastTemperature = Float.NaN;
        lastVibrance = Float.NaN;
    }
}
