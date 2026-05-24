package vorga.phazeclient.mixins;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints {@link ClientWorld#getSkyColor(Vec3d, float)} so the configured
 * sky tint reaches every renderer that consumes the value: vanilla
 * sky-dome render, {@code BackgroundRenderer.getFogColor} (horizon),
 * and Iris's {@code skyColor} shader uniform consumed by shader packs
 * via {@code gbuffers_sky}.
 *
 * <h3>Why we pre-empt the entire method instead of using
 * {@code @ModifyReturnValue}</h3>
 * BadOptimizations stacks two cooperating injects on the same method:
 * <ol>
 *   <li>{@code @Inject(at = HEAD, cancellable = true)} returns
 *       {@code bo$skyColorCache} early on cache hit.</li>
 *   <li>{@code @Inject(at = RETURN)} writes the live return value
 *       back into {@code bo$skyColorCache}.</li>
 * </ol>
 * Sitting a {@code @ModifyReturnValue} between them either pollutes
 * the cache with a tinted value (next-frame double-tint) or gets
 * skipped entirely on cache hits (the user-visible "color doesn't
 * change immediately" symptom). The mod doesn't ship a Mixin Extras
 * version with {@code @WrapMethod} either, so we go the lower-level
 * route: a {@code @Inject(at = HEAD, cancellable = true)} with
 * priority 1300 (above BadOpt's 1200), which lets us pre-empt both
 * BadOpt injects entirely while the module is enabled.
 *
 * <p>To keep behaviour parity we recompute the same vanilla pipeline
 * BadOpt would have produced, then apply the tint. The maths is a
 * straight port of {@code ClientWorld.getSkyColor} so the only thing
 * we lose under BadOpt is the cache - the rest of BadOpt's
 * optimizations (lightmap, debug HUD throttling, particle culling,
 * tick-rate work) keep running. When the module is disabled we
 * never call {@code cir.cancel()} and BadOpt's cache stays fully
 * effective.
 *
 * <p>Sodium compatibility is preserved because the duplicated
 * sampler call is exactly the same as the one Sodium redirects on
 * vanilla's body - both run a fast biome-colour sample under
 * Sodium, both run vanilla's slower sampler without it.
 */
@Mixin(value = ClientWorld.class, priority = 1300)
public abstract class ClientWorldSkyCustomizerMixin {

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
    private void phaze$tintSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            // Module off: let vanilla / BadOpt run normally so the
            // cache pipeline keeps its full performance.
            return;
        }
        ClientWorld self = (ClientWorld) (Object) this;
        int vanilla = phaze$computeVanillaSkyColor(self, cameraPos, tickDelta);
        int tinted = module.applyToSky(vanilla, self.getSkyBrightness(tickDelta));
        cir.setReturnValue(tinted);
    }

    /**
     * Reproduces vanilla's {@code ClientWorld.getSkyColor} body
     * verbatim. Kept in sync with {@code net.minecraft.client.world.ClientWorld}
     * for 1.21.4 - if Mojang changes this maths in a future MC
     * version we update this method to match. Centralising the
     * pipeline here means the module's tint is applied to a value
     * that's bit-for-bit identical to what vanilla would have
     * returned on a cache miss.
     */
    private static int phaze$computeVanillaSkyColor(ClientWorld world, Vec3d cameraPos, float tickDelta) {
        float angle = world.getSkyAngle(tickDelta);
        Vec3d biomeBase = cameraPos.subtract(2.0, 2.0, 2.0).multiply(0.25);
        Vec3d sampled = CubicSampler.sampleColor(biomeBase, (x, y, z) ->
                Vec3d.unpackRgb(world.getBiomeAccess().getBiomeForNoiseGen(x, y, z).value().getSkyColor()));
        float sunFactor = MathHelper.cos(angle * (float) (Math.PI * 2)) * 2.0F + 0.5F;
        sunFactor = MathHelper.clamp(sunFactor, 0.0F, 1.0F);
        sampled = sampled.multiply(sunFactor);
        int color = ColorHelper.getArgb(sampled);
        float rain = world.getRainGradient(tickDelta);
        if (rain > 0.0F) {
            float t = rain * 0.75F;
            int grey = ColorHelper.scaleRgb(ColorHelper.grayscale(color), 0.6F);
            color = ColorHelper.lerp(t, color, grey);
        }
        float thunder = world.getThunderGradient(tickDelta);
        if (thunder > 0.0F) {
            float t = thunder * 0.75F;
            int grey = ColorHelper.scaleRgb(ColorHelper.grayscale(color), 0.2F);
            color = ColorHelper.lerp(t, color, grey);
        }
        int lightning = world.getLightningTicksLeft();
        if (lightning > 0) {
            float t = Math.min(lightning - tickDelta, 1.0F) * 0.45F;
            color = ColorHelper.lerp(t, color, ColorHelper.getArgb(204, 204, 255));
        }
        return color;
    }
}
