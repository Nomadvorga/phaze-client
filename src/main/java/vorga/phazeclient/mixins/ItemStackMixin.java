package vorga.phazeclient.mixins;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ShinyPots;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void phaze$shinyPots(CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ShinyPots.isPotion(stack)) {
            cir.setReturnValue(true);
        }
    }
}
