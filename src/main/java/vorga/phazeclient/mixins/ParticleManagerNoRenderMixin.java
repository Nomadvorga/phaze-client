package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.NoRender;

/**
 * Drops particle creation requests at the {@link ParticleManager}
 * funnel. In 1.21.11 the effect-based public surface is still a
 * single 7-arg {@code addParticle(ParticleEffect, double x, y, z,
 * double vx, vy, vz)} returning {@code Particle} (verified against
 * the 1.21.11 jar with {@code javap -p}); the older 8/9-arg overloads
 * with {@code alwaysSpawn} / {@code canSpawnOnMinimal} flags stay
 * removed. Every {@code ClientWorld#addParticleClient} /
 * {@code addImportantParticleClient} path still bottoms out here, so
 * targeting just the 7-arg method covers the client-side call sites.
 *
 * <p>Two gating modes:
 * <ul>
 *   <li>{@link NoRender#particles} - cancel everything, returning
 *       {@code null} from {@code addParticle}. Vanilla treats a
 *       {@code null} return as "particle was rejected" and just
 *       moves on, so no downstream code path is broken by us
 *       refusing to allocate.</li>
 *   <li>{@link NoRender#hitParticles} - cancel only the four particle
 *       types that fire on entity hits:
 *       {@link ParticleTypes#DAMAGE_INDICATOR},
 *       {@link ParticleTypes#CRIT},
 *       {@link ParticleTypes#ENCHANTED_HIT},
 *       {@link ParticleTypes#SWEEP_ATTACK}. The first three are sent
 *       by the server through {@code EntityStatusS2CPacket} (status
 *       2/4/9) and turned into client-side particle requests; the
 *       fourth is spawned client-side by the player's own attack
 *       code. All four go through this funnel.</li>
 * </ul>
 *
 * <p>Order matters: we check the broad toggle first because if
 * {@code particles} is on, the {@code hitParticles} state is
 * irrelevant - the broader switch already wins. Mirrors the
 * visibility gating in {@link NoRender}.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerNoRenderMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void phaze$cancelAdd7(ParticleEffect parameters,
                                  double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  CallbackInfoReturnable<Particle> cir) {
        if (phaze$shouldCancel(parameters)) {
            cir.setReturnValue(null);
        }
    }

    private static boolean phaze$shouldCancel(ParticleEffect parameters) {
        NoRender mod = NoRender.getInstance();
        if (mod == null || !mod.isEnabled() || parameters == null) {
            return false;
        }
        if (mod.particles.isValue()) {
            return true;
        }
        ParticleType<?> type = parameters.getType();
        if (mod.hitParticles.isValue()) {
            if (type == ParticleTypes.DAMAGE_INDICATOR
                    || type == ParticleTypes.CRIT
                    || type == ParticleTypes.ENCHANTED_HIT
                    || type == ParticleTypes.SWEEP_ATTACK) {
                return true;
            }
        }
        if (mod.potionParticles.isValue()) {
            // {@link ParticleTypes#ENTITY_EFFECT} is the visible bubble
            // produced by every active StatusEffect on a LivingEntity
            // (LivingEntity#tickStatusEffects spawns one per tick per
            // active effect, tinted by the effect colour). In 1.21.4
            // ambient / beacon-supplied effects use the same particle
            // type with an ambient-flag colour modifier rather than a
            // separate type, so suppressing ENTITY_EFFECT alone covers
            // every potion-bubble path the user can observe.
            if (type == ParticleTypes.ENTITY_EFFECT) {
                return true;
            }
        }
        if (mod.splashPotionParticles.isValue()) {
            // The burst emitted when a splash potion bottle breaks.
            // {@link ParticleTypes#EFFECT} covers regular splashes
            // (slowness, weakness, water bottle, etc.) and
            // {@link ParticleTypes#INSTANT_EFFECT} covers the
            // instant healing / instant damage variants - both go
            // through the same {@code addParticle} funnel via
            // {@code PotionEntity#applySplash}. Lingering potions
            // emit {@code area_effect_cloud} entity particles via a
            // separate code path (entity render, not
            // ParticleManager.addParticle), so they remain unaffected
            // and the toggle stays specifically about splash impact.
            if (type == ParticleTypes.EFFECT
                    || type == ParticleTypes.INSTANT_EFFECT) {
                return true;
            }
        }
        if (mod.foodParticles.isValue()) {
            // {@link ParticleTypes#ITEM} is the chewy item-shard
            // particle vanilla emits via
            // {@code LivingEntity#spawnItemParticles} when the player
            // (or any LivingEntity) is mid-{@code eat} animation.
            // Vanilla also uses this type for the splatter when an
            // item entity is destroyed by fire / explosion, but that
            // is rare enough that the user-facing label "Food
            // Particles" still describes the dominant path.
            if (type == ParticleTypes.ITEM) {
                return true;
            }
        }
        if (mod.maceParticles.isValue()) {
            // Keep this resilient across mapping/minor-version renames:
            // match by registry path instead of hard-coding enum constants.
            var id = Registries.PARTICLE_TYPE.getId(type);
            if (id != null) {
                String path = id.getPath();
                if (path.contains("gust") || path.contains("wind")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cancel the burst of block-shard particles vanilla emits when a
     * block finishes breaking. Up to 1.21.4 {@code ParticleManager}
     * owned this funnel and a HEAD cancel here covered every break
     * source - own digging, neighbour break, BUD-style updates, world
     * events.
     *
     * <p>TODO(1.21.11): {@code ParticleManager} no longer declares
     * {@code addBlockBreakParticles} at all. Verified against the
     * 1.21.11 jar: the method moved to
     * {@code net.minecraft.client.world.ClientWorld#addBlockBreakParticles(BlockPos, BlockState)}
     * (overriding {@code World#addBlockBreakParticles}), which builds the
     * shard particles itself and hands finished {@code Particle}
     * instances to {@code ParticleManager#addParticle(Particle)} -
     * i.e. it bypasses the {@code ParticleEffect} funnel this mixin
     * filters, so nothing else in this class catches it either.
     * Re-targeting is not possible from inside this mixin because
     * {@code @Mixin} here is bound to {@code ParticleManager}; fixing
     * it needs a new {@code ClientWorldBreakParticlesMixin} plus an
     * entry in {@code phaze.mixins.json}, both outside this file.
     * Left with {@code require = 0} so it is a silent no-op instead of
     * a launch-time injection failure; the NoRender "break block
     * particles" toggle is inert until that mixin exists.
     */
    @Inject(method = "addBlockBreakParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void phaze$cancelBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        // The broader {@code Particles} toggle already cancels every
        // particle individually inside addParticle so it doesn't need
        // to be re-checked here. Only the dedicated break-block toggle
        // gates this fast-path early-return.
        if (mod.breakBlockParticles.isValue()) {
            ci.cancel();
        }
    }

    /**
     * Cancel the smaller per-tick particles that vanilla emits while
     * a block is being mined (one shard per dig tick, before the
     * block has actually broken). Distinct from {@link
     * #phaze$cancelBreakParticles} but visually part of the same
     * "breaking a block" experience - one toggle controls both so
     * users don't have to track the engine's internal split.
     *
     * <p>TODO(1.21.11): same breakage as
     * {@link #phaze$cancelBreakParticles} - {@code ParticleManager}
     * no longer declares {@code addBlockBreakingParticles}. Verified
     * against the 1.21.11 jar: it moved to {@code ClientWorld} AND was
     * renamed, to
     * {@code net.minecraft.client.world.ClientWorld#spawnBlockBreakingParticle(BlockPos, Direction)}
     * (called from {@code MinecraftClient}'s dig loop). Needs the same
     * new {@code ClientWorld} mixin + {@code phaze.mixins.json} entry;
     * kept at {@code require = 0} so it stays a silent no-op rather
     * than a hard injection failure.
     */
    @Inject(method = "addBlockBreakingParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void phaze$cancelBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (mod.breakBlockParticles.isValue()) {
            ci.cancel();
        }
    }
}
