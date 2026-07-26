package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererWorldColorMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void phaze$pushCloudWorldColor(CallbackInfo ci) {
        WorldColorCorrectionController.push(WorldColorCorrectionController.Target.CLOUDS);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void phaze$popCloudWorldColor(CallbackInfo ci) {
        WorldColorCorrectionController.pop();
    }

    @Redirect(
            method = "renderClouds",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
                    ordinal = 0
            )
    )
    private void phaze$applyCloudWorldColor(float red, float green, float blue, float alpha) {
        int argb = ((int) (alpha * 255.0F) & 0xFF) << 24
                | ((int) (red * 255.0F) & 0xFF) << 16
                | ((int) (green * 255.0F) & 0xFF) << 8
                | ((int) (blue * 255.0F) & 0xFF);
        int transformed = WorldColorCorrectionController.applyToArgb(WorldColorCorrectionController.Target.CLOUDS, argb);
        RenderSystem.setShaderColor(
                ((transformed >>> 16) & 0xFF) / 255.0F,
                ((transformed >>> 8) & 0xFF) / 255.0F,
                (transformed & 0xFF) / 255.0F,
                ((transformed >>> 24) & 0xFF) / 255.0F
        );
    }
}
