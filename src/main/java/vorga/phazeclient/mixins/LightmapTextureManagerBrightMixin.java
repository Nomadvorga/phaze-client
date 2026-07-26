package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.Bright;

/**
 * Implements the actual brightness override for the {@link Bright}
 * module. {@link LightmapTextureManager#update(float)} reads the gamma
 * {@code SimpleOption} via {@code getValue()} (boxed {@link Double})
 * and feeds it into the lightmap curve; we intercept that boxed value
 * with MixinExtras' {@link ModifyExpressionValue} and substitute a
 * larger one when the module is active.
 *
 * <p>The {@code * 10} factor on top of the slider is intentional: the
 * engine's gamma curve saturates well before slider 1.0 alone would
 * push it, so the multiplier is what actually drives the lightmap into
 * the "fully lit" region. Verbatim from the reference port - changing
 * it would break parity with the upstream feel users expect.
 *
 * <p>Falls through untouched whenever {@code Bright} is null or its
 * state is off, so vanilla brightness control behaves normally when
 * the module is disabled.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerBrightMixin {

    @ModifyExpressionValue(
            method = "update(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"
            )
    )
    private Object phaze$injectFullBright(Object original) {
        Bright bright = Bright.getInstance();
        if (bright == null || !bright.isState()) {
            return original;
        }
        if (!(original instanceof Double vanilla)) {
            return original;
        }
        return Math.max(vanilla, bright.brightness.getValue() * 10.0);
    }
}
