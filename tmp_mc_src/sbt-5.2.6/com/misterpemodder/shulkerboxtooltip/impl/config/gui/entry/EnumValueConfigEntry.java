package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_5676;

@Environment(EnvType.CLIENT)
public final class EnumValueConfigEntry<C, E extends Enum<E>> extends ValueConfigEntry<C, E, E> {
   private final class_5676<E> valueButton;

   public EnumValueConfigEntry(ConfigCategoryTab<C> tab, ValueConfigNode<C, E, E> valueNode) {
      super(tab, valueNode);
      this.valueButton = class_5676.method_32606((value) -> class_2561.method_43471(value.toString())).method_32616().method_32620(List.of((Enum[])this.valueNode.getValueType().getEnumConstants())).method_32619((Enum)this.getValue()).method_32617(0, 0, 160, 20, this.valueNode.getTitle(), (b, value) -> this.setValue(value));
      this.children.addFirst(this.valueButton);
   }

   public void refresh() {
      super.refresh();
      E value = (E)(this.getValue());
      if (this.valueButton.method_32603() != value) {
         this.valueButton.method_32605(value);
      }

   }

   public void method_25343(class_332 guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
      this.renderLabel(guiGraphics, x, y, entryWidth);
      this.valueButton.method_25358(160 - this.resetButton.method_25368() - 2 - this.undoButton.method_25368() - 2);
      if (this.tab.getMinecraft().field_1772.method_1726()) {
         this.undoButton.method_46421(x);
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(x + this.undoButton.method_25368() + 2);
         this.resetButton.method_46419(y);
         this.valueButton.method_46421(x + this.undoButton.method_25368() + 2 + this.resetButton.method_25368() + 2);
         this.valueButton.method_46419(y);
      } else {
         this.undoButton.method_46421(x + entryWidth - this.undoButton.method_25368());
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(this.undoButton.method_46426() - this.resetButton.method_25368() - 2);
         this.resetButton.method_46419(y);
         this.valueButton.method_46421(this.resetButton.method_46426() - this.valueButton.method_25368() - 2);
         this.valueButton.method_46419(y);
      }

      this.valueButton.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.resetButton.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.undoButton.method_25394(guiGraphics, mouseX, mouseY, delta);
   }
}
