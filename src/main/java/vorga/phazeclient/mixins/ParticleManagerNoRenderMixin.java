package vorga.phazeclient.mixins;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.NoRender;

/**
 * Drops particle creation requests at the {@link ParticleManager}
 * funnel. Both public {@code addParticle} overloads (the 7-arg
 * {@code (effect, x, y, z, vx, vy, vz)} and the 8-arg {@code (effect,
 * alwaysSpawn, x, y, z, vx, vy, vz)}) end up here in 1.21.4 and
 * forward to the private 9-arg path; targeting both publics keeps us
 * forward-compatible even if Mojang ever stops inlining the chain.
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

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void phaze$cancelAdd8(ParticleEffect parameters, boolean alwaysSpawn,
                                  double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  CallbackInfoReturnable<Particle> cir) {
        if (phaze$shouldCancel(parameters)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void phaze$cancelAdd9(ParticleEffect parameters, boolean alwaysSpawn, boolean canSpawnOnMinimal,
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
        if (mod.hitParticles.isValue()) {
            ParticleType<?> type = parameters.getType();
            return type == ParticleTypes.DAMAGE_INDICATOR
                    || type == ParticleTypes.CRIT
                    || type == ParticleTypes.ENCHANTED_HIT
                    || type == ParticleTypes.SWEEP_ATTACK;
        }
        return false;
    }
}
