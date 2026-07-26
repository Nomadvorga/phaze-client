package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

/**
 * Tints the world's sky colour at its single source so the configured
 * tint reaches every renderer that consumes it: the vanilla sky-dome
 * ({@code SkyRenderState.skyColor} -> {@code SkyRendering.renderTopSky}),
 * the atmospheric fog blend ({@code AtmosphericFogModifier}), and Iris's
 * {@code skyColor} shader uniform consumed via {@code gbuffers_sky}.
 *
 * <h3>1.21.11 port note - why this no longer mixes into ClientWorld</h3>
 * Through 1.21.4 the single source was {@code ClientWorld.getSkyColor(Vec3d, float)}
 * and this class was a {@code @ModifyReturnValue} on it. 1.21.6 deleted
 * that method outright: sky colour became one of the data-driven
 * environment attributes ({@code EnvironmentAttributes.SKY_COLOR_VISUAL}),
 * produced by {@code WorldEnvironmentAttributeAccess} and read by every
 * consumer through {@code Camera.getEnvironmentAttributeInterpolator().get(...)}.
 *
 * <p>{@link EnvironmentAttributeInterpolator#get} is therefore the exact
 * 1.21.11 analogue of the old {@code getSkyColor} return: one choke point
 * that both the dome and the fog read from. The class name is kept
 * because {@code phaze.mixins.json} registers mixins by class name; only
 * the target moved.
 *
 * <h3>Why a {@code @ModifyReturnValue} (and not a HEAD-cancellable
 * inject)</h3>
 * Earlier revisions of this mixin pre-empted vanilla entirely with a
 * {@code @Inject(at = HEAD, cancellable = true)} that ran a hand-port
 * of the sky-colour computation so we could pre-empt BadOptimizations'
 * sky-color cache. That approach had two symptoms reported by the user:
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
 * {@code @Inject(cancellable = true)}, so a cache-hit fast path
 * still feeds through our tint and the user gets real-time tint
 * updates without the dark-sky regression.
 *
 * <p>Priority 1300 places this modifier above BadOpt's mixins
 * (priority 1200) so when both injectors target the same RETURN
 * opcode we run last and the tint is the value the JVM ultimately
 * returns to callers - including the Iris {@code skyColor} uniform
 * read inside {@code CommonUniforms}.
 */
@Mixin(value = EnvironmentAttributeInterpolator.class, priority = 1300)
public abstract class ClientWorldSkyCustomizerMixin {

    // Erased signature of the target is (EnvironmentAttribute, float)Object,
    // so the handler has to work in Object terms. The attribute identity
    // check is a reference compare and runs before anything else, keeping
    // the per-frame cost for the ~dozen other attribute reads negligible.
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private Object phaze$tintSkyColor(Object original, EnvironmentAttribute<?> attribute, float tickProgress) {
        if (attribute != EnvironmentAttributes.SKY_COLOR_VISUAL || !(original instanceof Integer packed)) {
            return original;
        }

        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }

        // Day-night phase: World.getSkyBrightness(tickDelta) is gone in
        // 1.21.11; SKY_LIGHT_FACTOR_VISUAL is the attribute that replaced
        // it (same value vanilla's LightmapTextureManager now reads).
        // Re-entering get() here is safe - the attribute check above bails
        // out immediately for it, and the entry map lookup has already
        // completed by the time a RETURN modifier runs.
        EnvironmentAttributeInterpolator self = (EnvironmentAttributeInterpolator) (Object) this;
        Float skyBrightness = self.get(EnvironmentAttributes.SKY_LIGHT_FACTOR_VISUAL, tickProgress);

        return module.applyToSky(packed, skyBrightness == null ? 0.5F : skyBrightness);
    }
}
