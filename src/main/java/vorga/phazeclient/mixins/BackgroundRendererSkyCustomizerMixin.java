package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the fog colour vector returned by
 * {@link BackgroundRenderer#getFogColor} so the horizon, the
 * framebuffer clear-colour, and the {@code RenderSystem.setShaderFog}
 * uniform pick up the configured sky tint.
 *
 * <h3>Why the fog hook is needed for shader packs</h3>
 * Modern shader packs (Complementary, BSL, Sildur's, Photon and
 * friends) draw the sky through their own {@code gbuffers_skybasic}
 * shader which procedurally scatters light from the sun direction
 * and ignores the vanilla {@code skyColor} uniform entirely. As a
 * result, tinting only the {@link ClientWorld#getSkyColor} accessor
 * never reaches the visible sky-dome under those packs - the
 * scattering shader rebuilds the colour from physics.
 *
 * <p>The {@code fogColor} uniform, on the other hand, is captured by
 * Iris via its {@code MixinFogRenderer} hook into
 * {@code FogRenderer.setupFog} and consumed by basically every
 * shader pack to drive the horizon haze and the atmospheric
 * distance-fade. By tinting the vanilla {@code Vector4f} returned
 * <em>before</em> Iris reads it, the horizon and distance-fog
 * colour follow our tint even under shader packs that completely
 * replace the sky-dome shader.
 *
 * <h3>Vanilla / no-shader path</h3>
 * Without Iris, this same vector flows into:
 * <ul>
 *   <li>{@code RenderSystem.clearColor(...)} - the framebuffer
 *       clear colour, which paints the part of the sky behind the
 *       sky-dome geometry.</li>
 *   <li>{@code BackgroundRenderer.applyFog} which produces the
 *       {@code Fog} uniform consumed by the world shader for
 *       distance fog.</li>
 * </ul>
 * Combined with the {@link ClientWorldSkyCustomizerMixin} sky-color
 * tint, no-shader users get a fully consistent recolour: dome +
 * horizon + fog all on the same hue.
 *
 * <h3>Submersion and visual-effect skip</h3>
 * Vanilla picks dedicated colours when the camera is in water /
 * lava / powder snow and when the entity has Blindness / Darkness;
 * those signals are recognisable to players (cyan = water, orange
 * = lava, off-white = powder snow, dim grey = blindness) and would
 * be confusing if overridden by a sky tint, so we leave them
 * alone.
 */
@Mixin(value = BackgroundRenderer.class, priority = 900)
public abstract class BackgroundRendererSkyCustomizerMixin {

    @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
    private static Vector4f phaze$tintFogColor(Vector4f vanilla, Camera camera, float tickDelta, ClientWorld world,
                                               int viewDistance, float skyDarkness) {
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled() || vanilla == null) {
            return vanilla;
        }
        if (camera != null) {
            switch (camera.getSubmersionType()) {
                case WATER:
                case LAVA:
                case POWDER_SNOW:
                    return vanilla;
                default:
                    break;
            }
            if (camera.getFocusedEntity() instanceof LivingEntity living
                    && (living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS))) {
                return vanilla;
            }
        }

        int r = clamp255(Math.round(vanilla.x * 255.0F));
        int g = clamp255(Math.round(vanilla.y * 255.0F));
        int b = clamp255(Math.round(vanilla.z * 255.0F));
        int incoming = 0xFF000000 | (r << 16) | (g << 8) | b;

        float brightness = world != null ? world.getSkyBrightness(tickDelta) : 0.5F;
        int tinted = module.applyToSky(incoming, brightness);

        vanilla.x = ((tinted >> 16) & 0xFF) / 255.0F;
        vanilla.y = ((tinted >> 8) & 0xFF) / 255.0F;
        vanilla.z = (tinted & 0xFF) / 255.0F;
        return vanilla;
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
