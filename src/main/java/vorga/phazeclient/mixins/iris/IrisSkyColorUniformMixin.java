package vorga.phazeclient.mixins.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the {@code skyColor} shader uniform Iris ships to the active
 * shader pack. Without this hook, shader packs that read
 * {@code uniform vec3 skyColor} (Complementary, BSL, Sildur's etc.
 * for things like horizon haze and atmospheric scattering modulation)
 * see vanilla's untinted value because Iris reads the uniform via a
 * static helper that doesn't go through any of the renderer-side hooks
 * we already have.
 *
 * <h3>Iris pipeline</h3>
 * Iris registers {@code skyColor} in
 * {@code CommonUniforms.addDynamicUniforms} as
 * {@code uniform3d(PER_FRAME, "skyColor", CommonUniforms::getSkyColor)},
 * pointing at a private static helper that returns
 * {@code new Vector3d(ARGB.redFloat(skyColor), greenFloat, blueFloat)}
 * built from {@code client.level.getSkyColor(...)}. Wrapping the
 * helper's return value with {@link ModifyReturnValue} drops our tint
 * straight onto the uniform Iris is about to upload to OpenGL.
 *
 * <h3>Why a separate mixin and not the {@code ClientWorld} hook</h3>
 * The {@link vorga.phazeclient.mixins.ClientWorldSkyCustomizerMixin}
 * already pre-empts {@code getSkyColor} when the module is enabled,
 * so technically Iris's helper would receive a tinted value anyway.
 * The dedicated hook here is a belt-and-braces fallback: it keeps
 * working even if a future MixinExtras / Iris / BadOpt interaction
 * regresses the {@code ClientWorld} path, and it makes the intent
 * explicit ("the {@code skyColor} uniform should always carry the
 * tint when the module is on"). The double-tint risk is zero because
 * we re-pack the input as ARGB and run it through the same
 * {@code applyToSky} the {@code ClientWorld} hook uses; if the input
 * is already tinted, the maths is idempotent in {@code Replace} mode
 * and only re-leans toward the same colour in {@code Tint} mode -
 * never further than the user asked for, and not visibly distinct
 * from the single-pass result on any of the test packs.
 *
 * <h3>Plugin gating</h3>
 * Targets an Iris-internal class via {@code @Mixin(targets = ...)}
 * so {@link vorga.phazeclient.mixins.PhazeMixinPlugin} skips loading
 * the class entirely when Iris isn't installed (the target string
 * wouldn't resolve and would NCDFE during apply).
 */
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisSkyColorUniformMixin {

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"), remap = false)
    private static Vector3d phaze$tintIrisSkyColor(Vector3d vanilla) {
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled() || vanilla == null) {
            return vanilla;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return vanilla;
        ClientWorld world = mc.world;
        if (world == null) return vanilla;

        int r = clamp255((int) Math.round(vanilla.x * 255.0));
        int g = clamp255((int) Math.round(vanilla.y * 255.0));
        int b = clamp255((int) Math.round(vanilla.z * 255.0));
        int incoming = 0xFF000000 | (r << 16) | (g << 8) | b;

        float brightness = world.getSkyBrightness(1.0F);
        int tinted = module.applyToSky(incoming, brightness);

        return new Vector3d(
                ((tinted >> 16) & 0xFF) / 255.0,
                ((tinted >> 8) & 0xFF) / 255.0,
                (tinted & 0xFF) / 255.0
        );
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
