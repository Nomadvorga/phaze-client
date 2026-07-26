package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the sky-dome by rewriting the packed colour handed to
 * {@link SkyRendering#renderTopSky(int)}.
 *
 * <h3>1.21.11 port note</h3>
 * Up to 1.21.4 this mixin was an {@code @Redirect} on the
 * {@code RenderSystem.setShaderColor(float,float,float,float)} call at
 * the head of {@code SkyRendering.renderSky(FFF)}: the dome geometry was
 * drawn immediately afterwards with whatever colour that call left in GL
 * state, so redirecting it controlled the dome deterministically.
 *
 * <p>1.21.6 deleted both halves of that: {@code renderSky(FFF)} is gone
 * (the dome is now {@code renderTopSky(int)}, fed straight from
 * {@code SkyRenderState.skyColor} by {@code WorldRenderer}), and
 * imperative GPU state including {@code setShaderColor} was removed from
 * {@code RenderSystem} - the colour now travels as the packed ARGB
 * argument that {@code renderTopSky} converts via
 * {@code ColorHelper.toRgbaVector} and uploads as a dynamic uniform.
 *
 * <p>So the equivalent hook is the argument itself. Modifying it at HEAD
 * keeps the exact property the redirect had: we are the last writer of
 * the dome colour, after every upstream mixin that touched the sky
 * colour further up the pipeline, and independent of how the value was
 * produced (biome sampler, cached fast path, or another mod's override).
 *
 * <h3>Cost</h3>
 * Runs once per frame (single sky-dome submit per frame). Three FP
 * multiplies + three integer-pack/unpack ops. Module-disabled fast path
 * is one volatile read on {@code SkyCustomizer.isEnabled()}.
 *
 * <p>Priority 1500 places this above BadOpt (1200) and the sister
 * sky-color mixin (1300) so any future mixin-stack reordering still
 * leaves us as the last writer of the dome RGB.
 */
@Mixin(value = SkyRendering.class, priority = 1500)
public abstract class WorldRendererSkyDomeTintMixin {

    @ModifyVariable(
            method = "renderTopSky(I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int phaze$tintSkyDome(int skyArgb) {
        // Vanilla passes packed ARGB. Keep the module working on an
        // opaque colour exactly like the old float redirect did (which
        // always fed 1.0 alpha), then restore the original alpha bits
        // before the colour-correction pass.
        int alpha = (skyArgb >>> 24) & 0xFF;
        int working = 0xFF000000 | (skyArgb & 0x00FFFFFF);

        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module != null && module.isEnabled()) {
            working = module.applyToSky(working, phaze$skyBrightness());
        }

        return WorldColorCorrectionController.applyToArgb(
                WorldColorCorrectionController.Target.SKY,
                (alpha << 24) | (working & 0x00FFFFFF)
        );
    }

    /**
     * 1.21.11 replacement for {@code World.getSkyBrightness(tickDelta)}:
     * the day-night light factor is now the interpolated environment
     * attribute {@code SKY_LIGHT_FACTOR_VISUAL}, read off the camera's
     * interpolator - the same path vanilla's {@code LightmapTextureManager}
     * uses. Read through the interpolator rather than
     * {@code World.getEnvironmentAttributes()} so we get the frame-accurate
     * value and never trip the positional-attribute assertion.
     */
    private static float phaze$skyBrightness() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.gameRenderer == null) {
            return 0.5F;
        }
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) {
            return 0.5F;
        }
        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        Float factor = camera.getEnvironmentAttributeInterpolator()
                .get(EnvironmentAttributes.SKY_LIGHT_FACTOR_VISUAL, tickProgress);
        return factor == null ? 0.5F : factor;
    }
}
