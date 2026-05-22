package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the values returned by {@link ClientWorld#getSkyColor(Vec3d, float)}
 * and {@link ClientWorld#getCloudsColor(float)} according to the
 * {@link SkyCustomizer} module's mode / colours.
 *
 * <h3>Why these two methods specifically</h3>
 * Vanilla's renderer ({@code WorldRenderer}, {@code BackgroundRenderer},
 * the cloud-shader path) all read sky / cloud colours through these
 * two ClientWorld accessors before feeding them into shader
 * uniforms. Wrapping the return value here is the cheapest and most
 * future-proof hook point: the renderer pipeline downstream can
 * change between MC versions without breaking us, because we only
 * touch the value that comes out the accessor.
 *
 * <h3>{@code @ModifyReturnValue}</h3>
 * MixinExtras' modifier preserves vanilla's path entirely, including
 * the biome lookup / time-of-day math; we just transform the final
 * int. {@code @Inject + cancellable} would force us to recompute
 * skyBrightness ourselves, which would drift across MC versions
 * (the curve has been tweaked at least once between 1.20 -> 1.21).
 *
 * <h3>Sky brightness for tinting</h3>
 * The first method's signature passes {@code tickDelta} which we
 * forward into {@link ClientWorld#getSkyBrightness(float)} via the
 * mixin's {@code this} cast - same call vanilla itself would make
 * if it needed the value. For the cloud method, vanilla doesn't
 * pass position so we approximate skyBrightness from the same
 * accessor; close enough for the tint blend.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldSkyCustomizerMixin {

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private int phaze$tintSky(int vanilla, Vec3d cameraPos, float tickDelta) {
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return vanilla;
        }
        ClientWorld self = (ClientWorld) (Object) this;
        float brightness = self.getSkyBrightness(tickDelta);
        return module.applyToSky(vanilla, brightness);
    }

    @ModifyReturnValue(method = "getCloudsColor", at = @At("RETURN"))
    private int phaze$tintClouds(int vanilla, float tickDelta) {
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return vanilla;
        }
        ClientWorld self = (ClientWorld) (Object) this;
        float brightness = self.getSkyBrightness(tickDelta);
        return module.applyToClouds(vanilla, brightness);
    }
}
