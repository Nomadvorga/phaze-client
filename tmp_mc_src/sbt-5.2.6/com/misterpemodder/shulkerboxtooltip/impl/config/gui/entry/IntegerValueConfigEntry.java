package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_2583;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_5481;

@Environment(EnvType.CLIENT)
public final class IntegerValueConfigEntry<C> extends ValueConfigEntry<C, Integer, Integer> {
   private final class_342 inputField;
   private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d*");

   public IntegerValueConfigEntry(ConfigCategoryTab<C> tab, ValueConfigNode<C, Integer, Integer> valueNode) {
      super(tab, valueNode);
      this.inputField = new class_342(tab.getMinecraft().field_1772, 0, 0, 158, 18, this.valueNode.getTitle());
      this.inputField.method_1852(((Integer)this.getValue()).toString());
      this.inputField.method_1890((s) -> INTEGER_PATTERN.matcher(s).matches());
      this.inputField.method_1863(this::onInputChange);
      this.children.addFirst(this.inputField);
   }

   public void refresh() {
      if (this.valueNode.validate(this.tab.getConfig()) == null) {
         String valueStr = ((Integer)this.getValue()).toString();
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
      try {
         this.setValue(Integer.parseInt(value));
      } catch (NumberFormatException var3) {
      }

   }

   public void method_25343(class_332 guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
      this.renderLabel(guiGraphics, x, y, entryWidth);
      this.inputField.method_46421(x + entryWidth - 158 - 1);
      this.inputField.method_46419(y + 1);
      this.resetButton.method_46421(x + entryWidth - this.resetButton.method_25368() - 2 - this.undoButton.method_25368());
      this.resetButton.method_46419(y);
      this.undoButton.method_46421(x + entryWidth - this.undoButton.method_25368());
      this.undoButton.method_46419(y);
      this.inputField.method_25358(158 - this.resetButton.method_25368() - 2 - this.undoButton.method_25368() - 2);
      if (this.tab.getMinecraft().field_1772.method_1726()) {
         this.undoButton.method_46421(x);
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(this.undoButton.method_46426() + this.undoButton.method_25368() + 2);
         this.resetButton.method_46419(y);
         this.inputField.method_46421(this.resetButton.method_46426() + this.resetButton.method_25368() + 2);
         this.inputField.method_46419(y + 1);
      } else {
         this.undoButton.method_46421(x + entryWidth - this.undoButton.method_25368());
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(this.undoButton.method_46426() - this.resetButton.method_25368() - 2);
         this.resetButton.method_46419(y);
         this.inputField.method_46421(this.resetButton.method_46426() - this.inputField.method_25368() - 3);
         this.inputField.method_46419(y + 1);
      }

      this.inputField.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.resetButton.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.undoButton.method_25394(guiGraphics, mouseX, mouseY, delta);
   }
}
