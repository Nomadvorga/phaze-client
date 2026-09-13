package vorga.phazeclient.mixins;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.NoRender;

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

            if (type == ParticleTypes.ENTITY_EFFECT) {
                return true;
            }
        }
        if (mod.splashPotionParticles.isValue()) {

            if (type == ParticleTypes.EFFECT
                    || type == ParticleTypes.INSTANT_EFFECT) {
                return true;
            }
        }
        if (mod.foodParticles.isValue()) {

            if (type == ParticleTypes.ITEM) {
                return true;
            }
        }
        if (mod.maceParticles.isValue()) {

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

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelAddParticleInstance(Particle particle, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.particles.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelEmitter(net.minecraft.entity.Entity entity,
                                     net.minecraft.particle.ParticleEffect effect,
                                     CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.particles.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelEmitterWithMaxAge(net.minecraft.entity.Entity entity,
                                               net.minecraft.particle.ParticleEffect effect,
                                               int maxAge,
                                               CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.particles.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "addToBatch(Lnet/minecraft/client/render/SubmittableBatch;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/Camera;F)V",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$cancelParticleRendering(net.minecraft.client.render.SubmittableBatch batch,
                                               net.minecraft.client.render.Frustum frustum,
                                               net.minecraft.client.render.Camera camera,
                                               float tickDelta,
                                               CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.particles.isValue()) {
            ci.cancel();
        }
    }
}
