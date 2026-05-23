package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.CustomFog;

/**
 * Smooth Skies "Fix Skybox Clipping" sub-toggle.
 *
 * <p>At low render distances vanilla's far-plane sits closer than
 * the skybox, which clips the far edge of clouds / horizon and
 * produces the visible seam between sky and fog the user reported.
 * Pushing the projection's far distance out to a 176-block floor
 * (matches the SmoothSkies reference, MIT) lets the sky pipeline
 * draw all the way to the horizon, so the fog ramp blends into a
 * full sky gradient instead of a hard cutoff.
 *
 * <p>Gated on {@link CustomFog#isFixSkyboxClipping} so the change
 * only fires while the user has Custom Fog ON and the Smooth
 * Skies + Fix Skybox Clipping sub-toggles ON.
 */
@Mixin(GameRenderer.class)
public class GameRendererFarPlaneSmoothSkiesMixin {

    @ModifyReturnValue(method = "getFarPlaneDistance", at = @At("RETURN"))
    private float phaze$pushFarPlane(float vanilla) {
        if (!CustomFog.getInstance().isFixSkyboxClipping()) {
            return vanilla;
        }
        // 176 blocks is the SmoothSkies floor - just past 11 chunks,
        // which covers every render-distance dropout where vanilla's
        // far-plane sat shy of the skybox dome. Larger values risk
        // pushing translucent geometry depth past the precision the
        // GL projection can resolve cleanly.
        return Math.max(vanilla, 176.0F);
    }
}
