package com.misterpemodder.shulkerboxtooltip.mixin.client.fabric;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.impl.tooltip.PreviewTooltipComponent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2561;
import net.minecraft.class_5632;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_1799.class})
public class ItemStackMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"getTooltipImage()Ljava/util/Optional;"},
      cancellable = true
   )
   private void onGetTooltipData(CallbackInfoReturnable<Optional<class_5632>> cir) {
      PreviewContext context = PreviewContext.builder((class_1799)this).withOwner(ShulkerBoxTooltipClient.client == null ? null : ShulkerBoxTooltipClient.client.field_1724).build();
      if (ShulkerBoxTooltipApi.isPreviewAvailable(context)) {
         cir.setReturnValue(Optional.of(new PreviewTooltipComponent(ShulkerBoxTooltipApi.getPreviewProviderForStackWithOverrides(context.stack()), context)));
      }

   }

   @Inject(
      at = {@At("RETURN")},
      method = {"getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;"}
   )
   private void onGetTooltip(class_1792.class_9635 context, class_1657 player, class_1836 type, CallbackInfoReturnable<List<class_2561>> cir) {
      List<class_2561> tooltip = (List)cir.getReturnValue();
      class_1799 var10000 = (class_1799)this;
      Objects.requireNonNull(tooltip);
      ShulkerBoxTooltipClient.modifyStackTooltip(var10000, tooltip::addAll);
   }
}
