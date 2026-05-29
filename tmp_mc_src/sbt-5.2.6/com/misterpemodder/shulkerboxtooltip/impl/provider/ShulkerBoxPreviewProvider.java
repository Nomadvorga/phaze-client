package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_1767;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2480;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_2627;
import net.minecraft.class_9334;

public class ShulkerBoxPreviewProvider extends InventoryAwarePreviewProvider<class_2627> {
   public ShulkerBoxPreviewProvider(int maxRowSize, Supplier<? extends class_2627> blockEntitySupplier) {
      super(maxRowSize, blockEntitySupplier);
   }

   public boolean showTooltipHints(PreviewContext context) {
      return true;
   }

   @Environment(EnvType.CLIENT)
   public ColorKey getWindowColorKey(PreviewContext context) {
      class_1767 dye = ((class_2480)class_2248.method_9503(context.stack().method_7909())).method_10528();
      if (dye == null) {
         return ColorKey.SHULKER_BOX;
      } else {
         ColorKey var10000;
         switch (dye) {
            case field_7946 -> var10000 = ColorKey.ORANGE_SHULKER_BOX;
            case field_7958 -> var10000 = ColorKey.MAGENTA_SHULKER_BOX;
            case field_7951 -> var10000 = ColorKey.LIGHT_BLUE_SHULKER_BOX;
            case field_7947 -> var10000 = ColorKey.YELLOW_SHULKER_BOX;
            case field_7961 -> var10000 = ColorKey.LIME_SHULKER_BOX;
            case field_7954 -> var10000 = ColorKey.PINK_SHULKER_BOX;
            case field_7944 -> var10000 = ColorKey.GRAY_SHULKER_BOX;
            case field_7967 -> var10000 = ColorKey.LIGHT_GRAY_SHULKER_BOX;
            case field_7955 -> var10000 = ColorKey.CYAN_SHULKER_BOX;
            case field_7945 -> var10000 = ColorKey.PURPLE_SHULKER_BOX;
            case field_7966 -> var10000 = ColorKey.BLUE_SHULKER_BOX;
            case field_7957 -> var10000 = ColorKey.BROWN_SHULKER_BOX;
            case field_7942 -> var10000 = ColorKey.GREEN_SHULKER_BOX;
            case field_7964 -> var10000 = ColorKey.RED_SHULKER_BOX;
            case field_7963 -> var10000 = ColorKey.BLACK_SHULKER_BOX;
            default -> var10000 = ColorKey.WHITE_SHULKER_BOX;
         }

         return var10000;
      }
   }

   public List<class_2561> addTooltip(PreviewContext context) {
      class_1799 stack = context.stack();
      if (this.canUseLootTables() && ShulkerBoxTooltip.config.tooltip.lootTableInfoType == Configuration.LootTableInfoType.HIDE && stack.method_57826(class_9334.field_49626)) {
         class_2583 style = class_2583.field_24360.method_10977(class_124.field_1080);
         return Collections.singletonList(class_2561.method_43470("???????").method_10862(style));
      } else {
         return super.addTooltip(context);
      }
   }
}
