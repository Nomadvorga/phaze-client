package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the sky-dome by intercepting the
 * {@link RenderSystem#setShaderColor(float, float, float, float)}
 * call inside {@link SkyRendering#renderSky(float, float, float)}.
 *
 * <h3>Why a redirect on {@code setShaderColor} (and not on the
 * outer {@code ClientWorld.getSkyColor})</h3>
 * The user reports the symptom precisely as: fog gets the tint,
 * the sky-dome stays vanilla (black on the screenshot). We do
 * have a {@code @ModifyReturnValue} on {@code ClientWorld.getSkyColor}
 * already, but in practice that hook can lose the dome path under
 * specific mixin stacks - BadOptimizations injects an
 * {@code @Inject(HEAD, cancellable=true)} that returns
 * synthetically without hitting our RETURN handler, and several
 * other mods install {@code @Redirect}s around the call that bypass
 * the inner RETURN bytecode entirely. The fog path uses
 * {@code BackgroundRenderer.getFogColor} which has its own RETURN
 * we do hit, hence the asymmetry the user sees.
 *
 * <p>Hooking {@link RenderSystem#setShaderColor} inside
 * {@link SkyRendering#renderSky} sidesteps all of that: vanilla's
 * very first instruction in {@code renderSky(red, green, blue)} is
 * {@code RenderSystem.setShaderColor(red, green, blue, 1.0F)} -
 * we redirect that single call, run the incoming RGB through the
 * exact same {@code applyToSky} pipeline the fog mixin uses, and
 * forward the tinted values to {@link RenderSystem}. The actual
 * sky-dome geometry is drawn immediately after with whatever
 * shader colour the {@code setShaderColor} call left in GL state,
 * so this redirect controls the dome colour deterministically
 * regardless of what other mixins did upstream.
 *
 * <h3>Cost</h3>
 * Runs once per frame (single sky-dome submit per frame). Three FP
 * multiplies + three integer-pack/unpack ops. Module-disabled fast
 * path is one volatile read on {@code SkyCustomizer.isEnabled()}.
 *
 * <p>Priority 1500 places this above BadOpt (1200) and the sister
 * sky-color mixin (1300) so any future mixin-stack reordering still
 * leaves us as the last writer of the dome RGB.
 */
@Mixin(value = SkyRendering.class, priority = 1500)
public abstract class WorldRendererSkyDomeTintMixin {

    @Redirect(
            method = "renderSky(FFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V",
                    ordinal = 0
            )
    )
    private void phaze$tintSkyDome(float red, float green, float blue, float alpha) {
        int rr = clamp255(Math.round(red * 255.0F));
        int gg = clamp255(Math.round(green * 255.0F));
        int bb = clamp255(Math.round(blue * 255.0F));
        int working = 0xFF000000 | (rr << 16) | (gg << 8) | bb;

        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module != null && module.isEnabled()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            float skyBrightness = 0.5F;
            if (mc != null && mc.world != null) {
                skyBrightness = mc.world.getSkyBrightness(1.0F);
            }
            working = module.applyToSky(working, skyBrightness);
        }

        int corrected = WorldColorCorrectionController.applyToArgb(
                WorldColorCorrectionController.Target.SKY,
                ((clamp255(Math.round(alpha * 255.0F)) & 0xFF) << 24) | (working & 0x00FFFFFF)
        );

        float tr = ((corrected >> 16) & 0xFF) / 255.0F;
        float tg = ((corrected >> 8) & 0xFF) / 255.0F;
        float tb = (corrected & 0xFF) / 255.0F;
        float ta = ((corrected >> 24) & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(tr, tg, tb, ta);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
