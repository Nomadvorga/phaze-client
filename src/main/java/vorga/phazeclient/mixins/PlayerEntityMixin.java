package vorga.phazeclient.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;

/**
 * Consolidated {@link PlayerEntity} mixin for the combo-counter hit
 * hook.
 *
 * <h3>Combo counter</h3>
 * On HEAD of {@code attack}, advance the combo counter only when
 * the target is another {@code PlayerEntity} that isn't ourselves.
 * Hits on mobs / armour stands / item frames don't increment the
 * counter so it tracks PvP combos exclusively.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void phaze$onAttack(Entity target, CallbackInfo ci) {
        if (!(target instanceof PlayerEntity player)) return;
        if (target == (Object) this) return;
        ComboCounterHud.getInstance().onAttack(player);
    }
}
