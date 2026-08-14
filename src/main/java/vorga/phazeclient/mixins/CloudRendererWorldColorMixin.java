package vorga.phazeclient.mixins;

import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

/**
 * Marks the CLOUDS colour-correction scope and tints the cloud colour.
 *
 * <h3>1.21.11 port note</h3>
 * The tint used to be a {@code @Redirect} on the first
 * {@code RenderSystem.setShaderColor(float,float,float,float)} call inside
 * {@code CloudRenderer.renderClouds}: the cloud quads were drawn straight
 * afterwards with whatever colour that call left in GL state.
 *
 * <p>1.21.11 removed imperative GPU colour state - {@code setShaderColor}
 * no longer exists on {@code RenderSystem} and {@code renderClouds} never
 * calls it. The colour now arrives as the packed ARGB first argument of
 * {@code renderClouds(int, CloudRenderMode, float, Vec3d, long, float)},
 * which the method feeds through {@code ColorHelper.toRgbaVector} and
 * uploads into the cloud std140 UBO. So the argument itself is the
 * equivalent hook, exactly as in {@code WorldRendererSkyDomeTintMixin}
 * for the sky dome.
 *
 * <p>The old redirect swallowed the vanilla call and then discarded the
 * value it had just computed, so the CLOUDS target never reached the
 * screen. Modifying the argument restores the intended transform;
 * {@code applyToArgb} returns its input untouched when the target is
 * inactive, so a disabled module costs one branch.
 */
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

    /**
     * {@code argsOnly = true, ordinal = 0} on the only {@code int} argument
     * of {@code renderClouds}, i.e. the packed ARGB cloud colour in LVT
     * slot 1.
     */
    @ModifyVariable(
            method = "renderClouds",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int phaze$applyCloudWorldColor(int cloudArgb) {
        return WorldColorCorrectionController.applyToArgb(
                WorldColorCorrectionController.Target.CLOUDS,
                cloudArgb
        );
    }
}
