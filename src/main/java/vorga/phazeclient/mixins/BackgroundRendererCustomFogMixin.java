package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
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
import vorga.phazeclient.implement.features.modules.other.CustomFog;

/**
 * Custom fog colour + distance override. Both hooks run as
 * {@code @ModifyReturnValue} so vanilla's full pipeline executes
 * first and we just override the final value. Submersion fog
 * (water / lava / powder snow) and blindness / darkness status
 * effects are skipped so gameplay-critical visibility cues are
 * preserved.
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

    @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
    private static Vector4f phaze$getFogColor(Vector4f vanilla, @Local(argsOnly = true) Camera camera) {
        if (phaze$shouldntApplyCustomFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN)) {
            return vanilla;
        }
        CustomFog module = CustomFog.getInstance();
        int rgb = module.getResolvedRgb();
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        return new Vector4f(r, g, b, 1.0F);
    }

    @ModifyReturnValue(method = "applyFog", at = @At("RETURN"))
    private static Fog phaze$applyFog(Fog vanillaFog,
                                      @Local(argsOnly = true) Camera camera,
                                      @Local(argsOnly = true) BackgroundRenderer.FogType fogType) {
        if (phaze$shouldntApplyCustomFog(camera, fogType)) {
            return vanillaFog;
        }
        CustomFog module = CustomFog.getInstance();

        float distance = module.getDistance();
        float density = clamp01(module.getDensity());
        float start = distance * (1.0F - density);
        float end = distance;

        int rgb = module.getResolvedRgb();
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        // CYLINDER keeps the fog edge horizontally consistent
        // regardless of pitch.
        return new Fog(start, end, FogShape.CYLINDER, r, g, b, 1.0F);
    }

    @Unique
    private static float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }
}
