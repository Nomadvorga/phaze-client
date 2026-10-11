package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    // onDamaged is called on the client too when the entity is damaged (status packet path).
    @Inject(method = "onDamaged", at = @At("HEAD"))
    private void phaze$onDamaged(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient) return;
        if (!(self instanceof PlayerEntity)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.player.equals(self)) return;
        ComboCounterHud.getInstance().onHitByEnemy();
    }

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void phaze$modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod == null || !mod.isEnabled()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.equals(self)) {
            float speed = mod.swingSpeed.getValue();
            if (Math.abs(speed - 1.0f) > 0.02f) {
                int base = cir.getReturnValue();
                int modified = Math.max(1, Math.round(base / speed));
                cir.setReturnValue(modified);
            }
        }
    }
}
