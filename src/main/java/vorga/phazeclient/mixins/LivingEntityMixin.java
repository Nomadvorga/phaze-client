package vorga.phazeclient.mixins;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        World world = entity.getWorld();
        
        // Check if this is the local player being damaged by another player
        if (world.isClient && source.getAttacker() != null) {
            if (source.getAttacker() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) source.getAttacker();
                // Check if the entity being damaged is the client player
                if (entity instanceof net.minecraft.entity.player.PlayerEntity) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client.player != null && client.player.equals(entity)) {
                        ComboCounterHud.getInstance().onHitByEnemy();
                    }
                }
            }
        }
    }
}
