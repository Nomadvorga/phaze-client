package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the value returned by {@link ClientWorld#getSkyColor(Vec3d, float)}
 * so the configured sky tint reaches every renderer that consumes
 * the result: vanilla sky-dome render, {@code BackgroundRenderer.getFogColor},
 * and Iris's {@code skyColor} shader uniform consumed via
 * {@code gbuffers_sky}.
 *
 * <h3>Why a {@code @ModifyReturnValue} (and not a HEAD-cancellable
 * inject)</h3>
 * Earlier revisions of this mixin pre-empted vanilla entirely with a
 * {@code @Inject(at = HEAD, cancellable = true)} that ran a hand-port
 * of {@code ClientWorld.getSkyColor} so we could pre-empt
 * BadOptimizations' sky-color cache. That approach had two
 * symptoms reported by the user:
 * <ul>
 *   <li>Without a shader pack the sky was rendered almost black on
 *       world join, only recovering after a relog.</li>
 *   <li>Even after the sky filled in, the biome-sampler hand-port
 *       could disagree with vanilla in edge cases (data-driven sky
 *       colours newer Minecraft versions fed through the registry,
 *       early ticks where {@code BiomeAccess} isn't fully populated
 *       yet) - producing the dark / off-colour result the
 *       screenshot shows.</li>
 * </ul>
 *
 * <p>Switching to {@code @ModifyReturnValue} fixes both: vanilla's
 * full implementation always runs, and we just adjust the result.
 * MixinExtras inserts its modifier on every RETURN opcode in the
 * post-transform body, including the one synthesised by
 * {@code @Inject(cancellable = true)}, so BadOpt's cache hit path
 * still feeds through our tint and the user gets real-time tint
 * updates without the dark-sky regression.
 *
 * <p>Priority 1300 places this modifier above BadOpt's mixins
 * (priority 1200) so when both injectors target the same RETURN
 * opcode we run last and the tint is the value the JVM ultimately
 * returns to callers - including the Iris {@code skyColor} uniform
 * read inside {@code CommonUniforms}.
 */
@Mixin(value = ClientWorld.class, priority = 1300)
public abstract class ClientWorldSkyCustomizerMixin {

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private int phaze$tintSkyColor(int original, Vec3d cameraPos, float tickDelta) {
        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }
        ClientWorld self = (ClientWorld) (Object) this;
        return module.applyToSky(original, self.getSkyBrightness(tickDelta));
    }
}
