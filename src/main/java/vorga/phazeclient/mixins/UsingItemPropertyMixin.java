package vorga.phazeclient.mixins;

import net.minecraft.client.render.item.property.bool.UsingItemProperty;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

/**
 * Suppresses the 3D shield blocking model transformation when OldAnimations
 * sword blocking is active, so only the sword blocking animation is shown.
 */
@Mixin(UsingItemProperty.class)
public abstract class UsingItemPropertyMixin {

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void phaze$suppressShieldBlockModel(
            ItemStack stack,
            ClientWorld world,
            LivingEntity entity,
            int seed,
            ModelTransformationMode mode,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (stack.getItem() instanceof ShieldItem && entity instanceof PlayerEntity player) {
            if (OldAnimations.isSwordBlockingActive(player)) {
                cir.setReturnValue(false);
            }
        }
    }
}
