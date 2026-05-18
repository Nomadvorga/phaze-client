package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.BetterDeathScreen;

/**
 * Captures the local player's death context so the
 * {@link BetterDeathScreen} module can surface it on the death
 * screen.
 *
 * <h3>Why hook PlayerEntity.onDeath</h3>
 * Hooking {@code LivingEntity.onDeath} would fire for every mob in
 * the world, forcing us to filter to the local player on every
 * call. Targeting the player subclass narrows the inject to the
 * relevant path with zero runtime branching. {@code @Inject HEAD}
 * runs before vanilla's death-message broadcast, so the
 * {@code DamageSource} we capture is identical to the one vanilla
 * later renders on the death screen.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBetterDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void phaze$captureDeath(DamageSource damageSource, CallbackInfo ci) {
        // Filter to the local player; remote players' onDeath calls
        // are irrelevant to our death screen panel.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        // Comparing references is safe here because the local
        // ClientPlayerEntity is a subclass of PlayerEntity and the
        // mixin is applied to PlayerEntity itself.
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != client.player) return;

        BetterDeathScreen module = BetterDeathScreen.getInstance();
        if (module == null || !module.isEnabled()) return;
        module.recordDeath(damageSource);
    }
}
