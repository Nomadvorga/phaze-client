package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
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
 * funnel. In 1.21.4 the public surface is a single 7-arg
 * {@code addParticle(ParticleEffect, double x, y, z, double vx, vy,
 * vz)} (verified by {@code javap -p}); the older 8/9-arg overloads
 * with {@code alwaysSpawn} / {@code canSpawnOnMinimal} flags were
 * removed. Targeting just the 7-arg method covers every
 * {@code World#addParticle} client-side call site.
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
        return false;
    }

    /**
     * Cancel the burst of block-shard particles vanilla emits when a
     * block finishes breaking. {@code addBlockBreakParticles} is the
     * single funnel called from {@code WorldRenderer#processWorldEvent}
     * (event id 2001) so a HEAD cancel covers every break source -
     * own digging, neighbour break, BUD-style updates, world events.
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
