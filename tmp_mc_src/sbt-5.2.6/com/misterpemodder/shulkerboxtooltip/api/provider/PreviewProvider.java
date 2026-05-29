package com.misterpemodder.shulkerboxtooltip.api.provider;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.renderer.PreviewRenderer;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;

public interface PreviewProvider {
   boolean shouldDisplay(PreviewContext var1);

   List<class_1799> getInventory(PreviewContext var1);

   int getInventoryMaxSize(PreviewContext var1);

   default int getMaxRowSize(PreviewContext context) {
      return 0;
   }

   default int getCompactMaxRowSize(PreviewContext context) {
      return 0;
   }

   default boolean isFullPreviewAvailable(PreviewContext context) {
      return true;
   }

   default boolean showTooltipHints(PreviewContext context) {
      return true;
   }

   default String getTooltipHintLangKey(PreviewContext context) {
      return "shulkerboxtooltip.hint.compact";
   }

   default String getFullTooltipHintLangKey(PreviewContext context) {
      return "shulkerboxtooltip.hint.full";
   }

   default String getLockKeyTooltipHintLangKey(PreviewContext context) {
      return "shulkerboxtooltip.hint.lock";
   }

   @Environment(EnvType.CLIENT)
   default ColorKey getWindowColorKey(PreviewContext context) {
      return ColorKey.DEFAULT;
   }

   @Environment(EnvType.CLIENT)
   default PreviewRenderer getRenderer() {
      return PreviewRenderer.getDefaultRendererInstance();
   }

   default List<class_2561> addTooltip(PreviewContext context) {
      return Collections.emptyList();
   }

   @Environment(EnvType.CLIENT)
   default void onInventoryAccessStart(PreviewContext context) {
   }

   @Nullable
   @Environment(EnvType.CLIENT)
   default class_2960 getTextureOverride(PreviewContext context) {
      return null;
   }

   default int getPriority() {
      return 1000;
   }
}
