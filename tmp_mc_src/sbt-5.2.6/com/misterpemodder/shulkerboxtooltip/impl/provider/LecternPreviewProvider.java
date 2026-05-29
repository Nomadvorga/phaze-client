package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.class_1263;
import net.minecraft.class_1799;
import net.minecraft.class_2371;
import net.minecraft.class_9279;
import net.minecraft.class_9334;

public class LecternPreviewProvider extends InventoryAwarePreviewProvider<class_1263> {
   private static final MapCodec<class_1799> CODEC;

   public LecternPreviewProvider(int maxRowSize, Supplier<? extends class_1263> inventoryFactory) {
      super(maxRowSize, inventoryFactory);
   }

   public List<class_1799> getInventory(PreviewContext context) {
      int invMaxSize = this.getInventoryMaxSize(context);
      List<class_1799> inv = class_2371.method_10213(invMaxSize, class_1799.field_8037);
      class_9279 nbtComponent = (class_9279)context.stack().method_57824(class_9334.field_49611);
      if (nbtComponent != null) {
         nbtComponent.method_57446(CODEC).result().ifPresent((book) -> inv.set(0, book));
      }

      return inv;
   }

   public boolean showTooltipHints(PreviewContext context) {
      return context.stack().method_57826(class_9334.field_49611);
   }

   static {
      CODEC = class_1799.field_24671.fieldOf("Book");
   }
}
