package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.CustomFog;

/**
 * Smooth Skies "Lower Void Darkness" sub-toggle.
 *
 * <p>Vanilla {@code BackgroundRenderer.getFogColor} darkens the
 * fog colour by {@code (cameraY - bottomY) * horizonShadingRatio},
 * clamped to {@code [0, 1]}. The default ratio of {@code 0.03125}
 * means stepping below the world height drives the multiplier
 * negative and the fog hard-blacks. SmoothSkies (MIT) flips the
 * ratio to a flat-world {@code 1.0} so the multiplier never
 * collapses, and the fog stays a visible colour at any altitude.
 *
 * <p>We mirror that approach here with a {@code @ModifyReturnValue}
 * on {@link ClientWorld.Properties#getHorizonShadingRatio} - the
 * single accessor every fog colour pipeline reads. Gated on
 * {@link CustomFog#isLowerVoidDarkness} so vanilla void shading
 * is preserved unless the user opts in via the Smooth Skies
 * sub-toggle.
 */
@Mixin(ClientWorld.Properties.class)
public class ClientWorldHorizonSmoothSkiesMixin {

    @ModifyReturnValue(method = "getHorizonShadingRatio", at = @At("RETURN"))
    private float phaze$flattenHorizonShading(float vanilla) {
        if (!CustomFog.getInstance().isLowerVoidDarkness()) {
            return vanilla;
        }
        // 1.0 is the flat-world value vanilla itself uses for
        // superflat dimensions, so the path is well-tested. It
        // produces a perfectly even fog colour regardless of
        // altitude, which is exactly the user-facing behaviour
        // SmoothSkies advertises.
        return 1.0F;
    }
}
