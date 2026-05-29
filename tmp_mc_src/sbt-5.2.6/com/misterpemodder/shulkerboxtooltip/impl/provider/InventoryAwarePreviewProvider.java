package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.provider.BlockEntityPreviewProvider;
import java.util.function.Supplier;
import net.minecraft.class_1263;
import net.minecraft.class_8934;

public class InventoryAwarePreviewProvider<I extends class_1263> extends BlockEntityPreviewProvider {
   private final Supplier<? extends I> inventoryFactory;
   private final ThreadLocal<I> cachedInventory = ThreadLocal.withInitial(() -> null);

   public InventoryAwarePreviewProvider(int maxRowSize, Supplier<? extends I> inventoryFactory) {
      super(27, false, maxRowSize, maxRowSize);
      this.inventoryFactory = inventoryFactory;
   }

   private I getInventory() {
      I inv = (I)(this.cachedInventory.get());
      if (inv == null) {
         inv = (I)(this.inventoryFactory.get());
         this.cachedInventory.set(inv);
      }

      return inv;
   }

   public boolean showTooltipHints(PreviewContext context) {
      return this.shouldDisplay(context);
   }

   public int getInventoryMaxSize(PreviewContext context) {
      return this.getInventory().method_5439();
   }

   public boolean canUseLootTables() {
      return this.getInventory() instanceof class_8934;
   }
}
