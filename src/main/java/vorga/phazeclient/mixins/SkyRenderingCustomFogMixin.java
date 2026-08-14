package vorga.phazeclient.mixins;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.CustomFog;

/**
 * Stops the sunrise/sunset glow from punching through Custom Fog.
 *
 * <p>The fog hooks in {@link FogRendererCustomFogMixin} cover the fog UBO and
 * the fog colour, which between them tint the sky background and everything
 * blended against it. The dawn/dusk glow is not part of that: vanilla draws it
 * as its own geometry in {@code SkyRendering.renderGlowingSky}, with its own
 * colour, on top of the sky. So with fog on, the horizon went fog-coloured
 * everywhere except a bright wedge around the sun.
 *
 * <p>Suppressing that draw is what makes the fog uniform. It is gated on
 * "Affect Sky" because that is the setting which decides whether Custom Fog
 * owns the sky at all - with it off the sky, and therefore the glow, stays
 * vanilla.
 */
@Mixin(SkyRendering.class)
public class SkyRenderingCustomFogMixin {

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void phaze$suppressSunriseGlow(MatrixStack matrices, float tickProgress, int color, CallbackInfo ci) {
        CustomFog module = CustomFog.getInstance();
        if (module == null || !module.isEnabled() || !module.isAffectSky()) {
            return;
        }
        // Same carve-outs as the fog hooks: submersion and the blindness /
        // darkness effects are gameplay visibility cues and stay vanilla.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return;
        }
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS))) {
            return;
        }
        ci.cancel();
    }
}
