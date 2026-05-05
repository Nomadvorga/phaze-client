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
    }

    public void apply() {
        if (!config.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        shader.setUniformValue("Brightness", config.getBrightness());
        shader.setUniformValue("Contrast", config.getContrast());
        shader.setUniformValue("Saturation", config.getSaturation());
        shader.setUniformValue("Gamma", config.getGamma());
        shader.setUniformValue("Temperature", config.getTemperature());
        shader.setUniformValue("Vibrance", config.getVibrance());

        shader.render(0.0f);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public void reload() {
        shader.reload();
    }
}
