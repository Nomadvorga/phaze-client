package com.misterpemodder.shulkerboxtooltip.mixin.client;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import java.util.List;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2480;
import net.minecraft.class_2561;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_2480.class})
public class ShulkerBoxBlockMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"appendHoverText(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V"},
      cancellable = true
   )
   private void onAppendTooltip(class_1799 stack, class_1792.class_9635 context, List<class_2561> tooltip, class_1836 options, CallbackInfo ci) {
      if (ShulkerBoxTooltip.config != null && ShulkerBoxTooltip.config.tooltip.type != Configuration.ShulkerBoxTooltipType.VANILLA) {
         ci.cancel();
      }

   }
}
