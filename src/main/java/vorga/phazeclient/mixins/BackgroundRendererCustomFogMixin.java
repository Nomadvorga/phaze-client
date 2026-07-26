package vorga.phazeclient.mixins;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.CustomFog;

/**
 * Custom fog colour + distance override. We rebuild the final
 * fog state from Phaze's module settings instead of inheriting any
 * distances, alpha, or enable-state semantics from vanilla. That
 * keeps Custom Fog visually active even when Mojang's fog path is
 * effectively disabled. Submersion fog (water / lava / powder snow)
 * and blindness / darkness status effects are still skipped so
 * gameplay-critical visibility cues are preserved.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererCustomFogMixin {

    @Unique
    private static boolean phaze$shouldntApplyCustomFog(Camera camera, BackgroundRenderer.FogType fogType) {
        CustomFog module = CustomFog.getInstance();
        if (module == null || !module.isEnabled()) {
            return true;
        }
        if (fogType == BackgroundRenderer.FogType.FOG_SKY && !module.isAffectSky()) {
            return true;
        }
        CameraSubmersionType submersion = camera.getSubmersionType();
        if (submersion != CameraSubmersionType.NONE) {
            return true;
        }
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity living) {
            if (living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS)) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private static void phaze$getFogColor(Camera camera,
                                          float tickDelta,
                                          net.minecraft.client.world.ClientWorld world,
                                          int viewDistance,
                                          float skyDarkness,
                                          CallbackInfoReturnable<Vector4f> cir) {
        Vector4f vanilla = cir.getReturnValue();
        if (phaze$shouldntApplyCustomFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN)) {
            return;
        }
        CustomFog module = CustomFog.getInstance();
        int rgb = module.getResolvedRgb();
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        cir.setReturnValue(new Vector4f(r, g, b, 1.0F));
    }

    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void phaze$applyFog(Camera camera,
                                       BackgroundRenderer.FogType fogType,
                                       Vector4f color,
                                       float viewDistance,
                                       boolean thickFog,
                                       float tickProgress,
                                       CallbackInfoReturnable<Fog> cir) {
        if (phaze$shouldntApplyCustomFog(camera, fogType)) {
            return;
        }
        // Short-circuit the entire vanilla path so global fogEnabled,
        // render-distance based fog ramps, thick-fog branches, and
        // renderer-specific wrappers (e.g. Sodium's terrain shader
        // fog variant) all receive our own fog packet instead.
        cir.setReturnValue(phaze$buildCustomFog());
    }

    @Unique
    private static float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }

    @Unique
    private static Fog phaze$buildCustomFog() {
        CustomFog module = CustomFog.getInstance();
        float distance = Math.max(2.0F, module.getDistance());
        float density = clamp01(module.getDensity());
        float start = distance * (1.0F - density);
        float end = distance;

        int rgb = module.getResolvedRgb();
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        // Always emit a fully-specified fog packet from module
        // settings so vanilla's own fog-enabled toggle cannot mute it.
        return new Fog(start, end, FogShape.CYLINDER, r, g, b, 1.0F);
    }
}
