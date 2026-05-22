package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.other.BetterDeathScreen;

/**
 * Consolidated {@link PlayerEntity} mixin merging the combo-counter
 * hit hook and the better-death-screen capture. Both target
 * {@code PlayerEntity} and live in independent {@code @Inject}
 * methods so they merge cleanly into one file.
 *
 * <h3>Combo counter</h3>
 * On HEAD of {@code attack}, advance the combo counter only when
 * the target is another {@code PlayerEntity} that isn't ourselves.
 * Hits on mobs / armour stands / item frames don't increment the
 * counter so it tracks PvP combos exclusively.
 *
 * <h3>Better death screen</h3>
 * On HEAD of {@code onDeath}, capture the {@link DamageSource} for
 * the local player so the death-screen module can render a richer
 * "killed by ..." panel. The local-player filter ({@code self ==
 * client.player}) keeps remote-player onDeath calls out of the
 * capture - we only care about our own death state.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void phaze$onAttack(Entity target, CallbackInfo ci) {
        if (!(target instanceof PlayerEntity player)) return;
        if (target == (Object) this) return;
        ComboCounterHud.getInstance().onAttack(player);
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void phaze$captureDeath(DamageSource damageSource, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != client.player) return;

        BetterDeathScreen module = BetterDeathScreen.getInstance();
        if (module == null || !module.isEnabled()) return;
        module.recordDeath(damageSource);
    }
}
