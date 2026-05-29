package com.misterpemodder.shulkerboxtooltip.fabric;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import com.misterpemodder.shulkerboxtooltip.impl.tooltip.PreviewClientTooltipComponent;
import com.misterpemodder.shulkerboxtooltip.impl.tooltip.PreviewTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.class_5684;

@Environment(EnvType.CLIENT)
public final class ShulkerBoxTooltipClientImpl extends ShulkerBoxTooltipClient implements ClientModInitializer {
   public void onInitializeClient() {
      ShulkerBoxTooltipClient.init();
      TooltipComponentCallback.EVENT.register((TooltipComponentCallback)(data) -> {
         if (data instanceof PreviewTooltipComponent previewData) {
            return new PreviewClientTooltipComponent(previewData);
         } else {
            return null;
         }
      });
   }
}
