package com.misterpemodder.shulkerboxtooltip.api;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorRegistry;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProviderRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.network.ServerNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.provider.OverridingPreviewProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_3222;

public interface ShulkerBoxTooltipApi {
   @Nullable
   static PreviewProvider getPreviewProviderForStack(class_1799 stack) {
      return PreviewProviderRegistry.getInstance().get(stack);
   }

   @Nullable
   static PreviewProvider getPreviewProviderForStackWithOverrides(class_1799 stack) {
      return OverridingPreviewProvider.maybeWrap(getPreviewProviderForStack(stack), stack);
   }

   @Environment(EnvType.CLIENT)
   static boolean isPreviewAvailable(PreviewContext context) {
      return ShulkerBoxTooltipClient.isPreviewAvailable(context);
   }

   @Nonnull
   @Environment(EnvType.CLIENT)
   static PreviewType getCurrentPreviewType(boolean hasFullPreviewMode) {
      return ShulkerBoxTooltipClient.getCurrentPreviewType(hasFullPreviewMode);
   }

   static boolean hasModAvailable(class_3222 player) {
      return ServerNetworking.hasModAvailable(player);
   }

   @Environment(EnvType.CLIENT)
   default void registerColors(ColorRegistry registry) {
   }

   void registerProviders(PreviewProviderRegistry var1);
}
