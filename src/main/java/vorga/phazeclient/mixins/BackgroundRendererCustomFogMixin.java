package vorga.phazeclient.mixins;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
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
 * Replaces vanilla fog with the {@link CustomFog} module's
 * configuration. Two hooks are necessary because vanilla splits
 * fog evaluation into two distinct passes:
 *
 * <ul>
 *   <li>{@code BackgroundRenderer.getFogColor} - returns the
 *       background clear colour the framebuffer is wiped with
 *       BEFORE world geometry draws. Without overriding this the
 *       sky / horizon behind objects keeps its vanilla colour, so
 *       the user only sees the tint on far terrain and the result
 *       reads as "like vanilla". Soup-visuals fixed this exact bug
 *       by overriding both hooks; we mirror that approach.</li>
 *   <li>{@code BackgroundRenderer.applyFog} - returns the
 *       {@link Fog} record fed into the shader uniforms (start /
 *       end / shape / colour). This is what controls the actual
 *       distance falloff and the colour terrain fades INTO.</li>
 * </ul>
 *
 * <p>Both hooks are HEAD + cancellable so we replace vanilla's
 * value entirely instead of letting it run first - that keeps
 * the override pixel-perfect across MC versions where the
 * intermediate math has changed (1.20 -&gt; 1.21.4 reorganised
 * the night-vision boost, water-fog interpolation, etc.).
 *
 * <p>Submersion fog (water / lava / powder snow) and the
 * blindness / darkness status-effect overrides are skipped so
 * gameplay-critical visibility cues are preserved. The
 * {@link #shouldntApplyCustomFog} guard centralises every
 * such early-return.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererCustomFogMixin {

    @Unique
    private static boolean phaze$shouldntApplyCustomFog(Camera camera, BackgroundRenderer.FogType fogType) {
        CustomFog module = CustomFog.getInstance();
        if (module == null || !module.isEnabled()) {
            return true;
        }
        // Sky-fog opt-out: if the user picked terrain-only, skip
        // the FOG_SKY pass and let vanilla's sky pipeline render
        // a clear horizon.
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

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private static void phaze$getFogColor(
            Camera camera,
            float tickDelta,
            ClientWorld world,
            int clampedViewDistance,
            float skyDarkness,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        if (phaze$shouldntApplyCustomFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN)) {
            return;
        }
        CustomFog module = CustomFog.getInstance();
        int argb = module.getResolvedColorArgb();
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        cir.setReturnValue(new Vector4f(r, g, b, a));
    }

    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void phaze$applyFog(
            Camera camera,
            BackgroundRenderer.FogType fogType,
            Vector4f color,
            float viewDistance,
            boolean thickenFog,
            float tickDelta,
            CallbackInfoReturnable<Fog> cir
    ) {
        if (phaze$shouldntApplyCustomFog(camera, fogType)) {
            return;
        }
        CustomFog module = CustomFog.getInstance();

        float distance = module.getDistance();
        float density = module.getDensity();
        if (density < 0.0F) density = 0.0F;
        if (density > 1.0F) density = 1.0F;
        float start = distance * (1.0F - density);
        float end = distance;

        int argb = module.getResolvedColorArgb();
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float a = ((argb >>> 24) & 0xFF) / 255.0F;

        // CYLINDER matches the soup-visuals reference and keeps the
        // fog edge horizontally consistent regardless of pitch -
        // SPHERE would make the fog bend over the camera in a way
        // that reads weirdly when the user looks up / down.
        cir.setReturnValue(new Fog(start, end, FogShape.CYLINDER, r, g, b, a));
    }
}
