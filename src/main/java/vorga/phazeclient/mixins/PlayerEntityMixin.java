package vorga.phazeclient.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        if (target instanceof LivingEntity) {
            ComboCounterHud.getInstance().onAttack((LivingEntity) target);
        }
    }
}
