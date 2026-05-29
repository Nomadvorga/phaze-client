package com.misterpemodder.shulkerboxtooltip.mixin.client;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import java.util.function.Consumer;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2480;
import net.minecraft.class_2561;
import net.minecraft.class_9331;
import net.minecraft.class_9334;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_1799.class})
public class ItemStackMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V"},
      cancellable = true
   )
   private void removeLore(class_9331<?> componentType, class_1792.class_9635 context, Consumer<class_2561> textConsumer, class_1836 type, CallbackInfo ci) {
      if (componentType == class_9334.field_49632) {
         class_1792 item = ((class_1799)this).method_7909();
         if (ShulkerBoxTooltip.config != null && ShulkerBoxTooltip.config.tooltip.hideShulkerBoxLore && item instanceof class_1747) {
            class_1747 blockitem = (class_1747)item;
            if (blockitem.method_7711() instanceof class_2480) {
               ci.cancel();
            }
         }
      }

   }
}
