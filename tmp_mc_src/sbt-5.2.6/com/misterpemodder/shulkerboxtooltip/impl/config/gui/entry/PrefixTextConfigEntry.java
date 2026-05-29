package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_6379;
import net.minecraft.class_7940;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public final class PrefixTextConfigEntry extends ConfigEntry {
   private final class_7940 textWidget;
   private final List<class_7940> textWidgetAsList;

   public PrefixTextConfigEntry(ConfigCategoryTab<?> tab, class_2561 text) {
      this.textWidget = new class_7940(text, tab.getMinecraft().field_1772);
      this.textWidgetAsList = List.of(this.textWidget);
   }

   public @NotNull List<? extends class_6379> method_37025() {
      return this.textWidgetAsList;
   }

   public void method_25343(class_332 guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
      this.textWidget.method_46421(x);
      this.textWidget.method_46419(y);
      this.textWidget.method_48984(entryWidth);
      this.textWidget.method_48579(guiGraphics, mouseX, mouseY, delta);
   }

   public @NotNull List<? extends class_364> method_25396() {
      return this.textWidgetAsList;
   }
}
