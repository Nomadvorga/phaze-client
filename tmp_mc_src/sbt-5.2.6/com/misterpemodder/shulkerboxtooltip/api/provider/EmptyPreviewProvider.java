package com.misterpemodder.shulkerboxtooltip.api.provider;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_1799;

public class EmptyPreviewProvider implements PreviewProvider {
   public static final PreviewProvider INSTANCE = new EmptyPreviewProvider();

   protected EmptyPreviewProvider() {
   }

   public int getInventoryMaxSize(PreviewContext context) {
      return 0;
   }

   public boolean shouldDisplay(PreviewContext context) {
      return false;
   }

   public List<class_1799> getInventory(PreviewContext context) {
      return Collections.emptyList();
   }
}
