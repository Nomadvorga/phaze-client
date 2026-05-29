package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_6379;
import net.minecraft.class_6381;
import net.minecraft.class_6382;
import net.minecraft.class_6379.class_6380;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public final class CategoryTitleConfigEntry extends ConfigEntry {
   private final class_310 minecraft;
   private final class_2561 label;

   public CategoryTitleConfigEntry(ConfigCategoryTab<?> tab, class_2561 label) {
      this.minecraft = tab.getMinecraft();
      this.label = label;
   }

   public @NotNull List<? extends class_6379> method_37025() {
      return List.of(new class_6379() {
         public class_6379.@NotNull class_6380 method_37018() {
            return class_6380.field_33785;
         }

         public void method_37020(class_6382 narrationElementOutput) {
            narrationElementOutput.method_37034(class_6381.field_33788, CategoryTitleConfigEntry.this.label);
         }
      });
   }

   public void method_25343(class_332 guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
      guiGraphics.method_27534(this.minecraft.field_1772, this.label, x + entryWidth / 2, y + 5, -1);
   }

   public @NotNull List<? extends class_364> method_25396() {
      return List.of();
   }
}
