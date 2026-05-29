package com.misterpemodder.shulkerboxtooltip.api.provider;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.class_124;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2371;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_7225;
import net.minecraft.class_9288;
import net.minecraft.class_9297;
import net.minecraft.class_9334;

public class BlockEntityPreviewProvider implements PreviewProvider {
   private final int defaultMaxInvSize;
   private final boolean defaultCanUseLootTables;
   private final int defaultMaxRowSize;
   private final int defaultCompactMaxRowSize;

   public BlockEntityPreviewProvider(int defaultMaxInvSize, boolean defaultCanUseLootTables) {
      this.defaultMaxInvSize = defaultMaxInvSize;
      this.defaultCanUseLootTables = defaultCanUseLootTables;
      this.defaultMaxRowSize = 9;
      this.defaultCompactMaxRowSize = 0;
   }

   public BlockEntityPreviewProvider(int defaultMaxInvSize, boolean defaultCanUseLootTables, int defaultMaxRowSize) {
      this.defaultMaxInvSize = defaultMaxInvSize;
      this.defaultCanUseLootTables = defaultCanUseLootTables;
      this.defaultMaxRowSize = defaultMaxRowSize <= 0 ? 9 : defaultMaxRowSize;
      this.defaultCompactMaxRowSize = 0;
   }

   public BlockEntityPreviewProvider(int defaultMaxInvSize, boolean defaultCanUseLootTables, int defaultMaxRowSize, int defaultCompactMaxRowSize) {
      this.defaultMaxInvSize = defaultMaxInvSize;
      this.defaultCanUseLootTables = defaultCanUseLootTables;
      this.defaultMaxRowSize = defaultMaxRowSize <= 0 ? 9 : defaultMaxRowSize;
      this.defaultCompactMaxRowSize = defaultCompactMaxRowSize;
   }

   public boolean shouldDisplay(PreviewContext context) {
      if (this.canUseLootTables() && context.stack().method_57826(class_9334.field_49626)) {
         return false;
      } else {
         return getItemCount(this.getInventory(context)) > 0;
      }
   }

   public boolean showTooltipHints(PreviewContext context) {
      return context.stack().method_57826(class_9334.field_49622);
   }

   public List<class_1799> getInventory(PreviewContext context) {
      class_7225.class_7874 registries = context.registryLookup();
      class_9288 container = (class_9288)context.stack().method_57824(class_9334.field_49622);
      int invMaxSize = this.getInventoryMaxSize(context);
      class_2371<class_1799> inv = class_2371.method_10213(invMaxSize, class_1799.field_8037);
      if (registries != null && container != null) {
         container.method_57492(inv);
      }

      return inv;
   }

   public int getInventoryMaxSize(PreviewContext context) {
      return this.defaultMaxInvSize;
   }

   public List<class_2561> addTooltip(PreviewContext context) {
      class_1799 stack = context.stack();
      class_9297 lootComponent = (class_9297)stack.method_57824(class_9334.field_49626);
      class_2583 style = class_2583.field_24360.method_10977(class_124.field_1080);
      if (this.canUseLootTables() && lootComponent != null) {
         List var10000;
         switch (ShulkerBoxTooltip.config.tooltip.lootTableInfoType) {
            case HIDE -> var10000 = Collections.emptyList();
            case SIMPLE -> var10000 = Collections.singletonList(class_2561.method_43471("shulkerboxtooltip.hint.loot_table").method_10862(style));
            default -> var10000 = Arrays.asList(class_2561.method_43471("shulkerboxtooltip.hint.loot_table.advanced").method_10852(class_2561.method_43470(": ")), class_2561.method_43470(" " + String.valueOf(lootComponent.comp_2414().method_29177())).method_10862(style));
         }

         return var10000;
      } else {
         return ShulkerBoxTooltipApi.getCurrentPreviewType(this.isFullPreviewAvailable(context)) == PreviewType.FULL ? Collections.emptyList() : getItemListTooltip(new ArrayList(), this.getInventory(context), style);
      }
   }

   public static List<class_2561> getItemCountTooltip(List<class_2561> tooltip, @Nullable List<class_1799> items) {
      return getItemListTooltip(tooltip, items, class_2583.field_24360.method_10977(class_124.field_1080));
   }

   public static List<class_2561> getItemListTooltip(List<class_2561> tooltip, @Nullable List<class_1799> items, class_2583 style) {
      int itemCount = getItemCount(items);
      class_5250 text;
      if (itemCount > 0) {
         text = class_2561.method_43469("container.shulkerbox.contains", new Object[]{itemCount});
      } else {
         text = class_2561.method_43471("container.shulkerbox.empty");
      }

      tooltip.add(text.method_10862(style));
      return tooltip;
   }

   public int getMaxRowSize(PreviewContext context) {
      return this.defaultMaxRowSize;
   }

   public int getCompactMaxRowSize(PreviewContext context) {
      return this.defaultCompactMaxRowSize;
   }

   public boolean canUseLootTables() {
      return this.defaultCanUseLootTables;
   }

   private static int getItemCount(@Nullable List<class_1799> items) {
      int itemCount = 0;
      if (items != null) {
         for(class_1799 stack : items) {
            if (stack.method_7909() != class_1802.field_8162) {
               ++itemCount;
            }
         }
      }

      return itemCount;
   }
}
