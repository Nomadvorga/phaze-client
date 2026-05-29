package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ColorWidget;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_2583;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_5481;

@Environment(EnvType.CLIENT)
public final class ColorValueConfigEntry<C> extends ValueConfigEntry<C, ColorKey, Integer> {
   private final ColorWidget colorWidget;
   private final class_342 inputField;

   public ColorValueConfigEntry(ConfigCategoryTab<C> tab, ValueConfigNode<C, ColorKey, Integer> valueNode) {
      super(tab, valueNode);
      this.inputField = new class_342(tab.getMinecraft().field_1772, 0, 0, 138, 18, this.valueNode.getTitle());
      this.inputField.method_1852(this.displayValue());
      this.inputField.method_1863(this::onInputChange);
      this.colorWidget = new ColorWidget(this.valueNode.getTitle(), this.inputField, this::getValue);
      this.children.addFirst(this.colorWidget);
      this.children.addFirst(this.inputField);
   }

   public void refresh() {
      if (this.valueNode.validate(this.tab.getConfig()) == null) {
         String valueStr = this.displayValue();
         if (!this.inputField.method_1882().equals(valueStr)) {
            this.inputField.method_1852(valueStr);
         }

         this.inputField.method_1854((s, i) -> class_5481.method_30747(s, class_2583.field_24360));
      } else {
         this.inputField.method_1854((s, i) -> class_5481.method_30747(s, class_2583.field_24360.method_10977(class_124.field_1061)));
      }

      super.refresh();
   }

   private void onInputChange(String value) {
      int argb;
      try {
         if (value.startsWith("#")) {
            if (value.length() > 7) {
               this.setValue(-1);
               return;
            }

            argb = (int)Long.parseLong(value.substring(1), 16);
         } else {
            argb = (int)Long.parseLong(value, 16);
         }
      } catch (NumberFormatException var4) {
         this.setValue(-1);
         return;
      }

      this.setValue(argb);
   }

   private String displayValue() {
      return "#" + Integer.toHexString((Integer)this.getValue());
   }

   public void method_25343(class_332 guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
      this.renderLabel(guiGraphics, x, y, entryWidth);
      this.inputField.method_25358(138 - this.resetButton.method_25368() - 2 - this.undoButton.method_25368() - 2);
      if (this.tab.getMinecraft().field_1772.method_1726()) {
         this.undoButton.method_46421(x);
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(this.undoButton.method_46426() + this.undoButton.method_25368() + 2);
         this.resetButton.method_46419(y);
         this.inputField.method_46421(this.resetButton.method_46426() + this.resetButton.method_25368() + 2);
         this.inputField.method_46419(y + 1);
         this.colorWidget.method_46421(this.inputField.method_46426() + this.inputField.method_25368() + 2);
         this.colorWidget.method_46419(y + 1);
      } else {
         this.undoButton.method_46421(x + entryWidth - this.undoButton.method_25368());
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(this.undoButton.method_46426() - this.resetButton.method_25368() - 2);
         this.resetButton.method_46419(y);
         this.inputField.method_46421(this.resetButton.method_46426() - this.inputField.method_25368() - 3);
         this.inputField.method_46419(y + 1);
         this.colorWidget.method_46421(this.inputField.method_46426() - this.colorWidget.method_25368() - 2);
         this.colorWidget.method_46419(y + 1);
      }

      this.colorWidget.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.inputField.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.resetButton.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.undoButton.method_25394(guiGraphics, mouseX, mouseY, delta);
   }
}
