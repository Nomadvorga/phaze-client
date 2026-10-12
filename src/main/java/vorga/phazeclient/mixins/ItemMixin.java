package vorga.phazeclient.mixins;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ShinyPots;

@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void phaze$shinyPotsItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ShinyPots.isPotion(stack)) {
            cir.setReturnValue(true);
        }
    }
}
