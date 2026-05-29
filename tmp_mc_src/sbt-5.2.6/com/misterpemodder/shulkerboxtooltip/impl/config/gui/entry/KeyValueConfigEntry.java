package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import com.misterpemodder.shulkerboxtooltip.impl.util.Key;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;

@Environment(EnvType.CLIENT)
public final class KeyValueConfigEntry<C> extends ValueConfigEntry<C, Key, Key> {
   private final class_4185 keyButton;

   public KeyValueConfigEntry(ConfigCategoryTab<C> tab, ValueConfigNode<C, Key, Key> valueNode) {
      super(tab, valueNode);
      class_2561 label = this.valueNode.getTitle();
      this.keyButton = class_4185.method_46430(label, (b) -> {
         this.tab.setSelectedKeyNode(this.valueNode);
         this.tab.getScreen().refresh();
      }).method_46434(0, 0, 160, 20).method_46435((supplier) -> ((Key)this.getValue()).isUnbound() ? class_2561.method_43469("narrator.controls.unbound", new Object[]{label}) : class_2561.method_43469("narrator.controls.bound", new Object[]{label, supplier.get()})).method_46431();
      this.children.addFirst(this.keyButton);
      this.refresh();
   }

   public void resetToDefault() {
      super.resetToDefault();
      this.tab.setSelectedKeyNode((ValueConfigNode)null);
      this.tab.getScreen().refresh();
   }

   public void refresh() {
      super.refresh();
      this.keyButton.method_25355(((Key)this.getValue()).get().method_27445());
      if (this.tab.getSelectedKeyNode() == this.valueNode) {
         this.keyButton.method_25355(class_2561.method_43470("> ").method_10852(this.keyButton.method_25369().method_27661().method_27695(new class_124[]{class_124.field_1068, class_124.field_1073})).method_27693(" <").method_27692(class_124.field_1054));
      }

   }

   public void method_25343(class_332 guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
      this.renderLabel(guiGraphics, x, y, entryWidth);
      this.keyButton.method_25358(160 - this.resetButton.method_25368() - 2 - this.undoButton.method_25368() - 2);
      if (this.tab.getMinecraft().field_1772.method_1726()) {
         this.undoButton.method_46421(x);
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(x + this.undoButton.method_25368() + 2);
         this.resetButton.method_46419(y);
         this.keyButton.method_46421(x + this.undoButton.method_25368() + 2 + this.resetButton.method_25368() + 2);
         this.keyButton.method_46419(y);
      } else {
         this.undoButton.method_46421(x + entryWidth - this.undoButton.method_25368());
         this.undoButton.method_46419(y);
         this.resetButton.method_46421(this.undoButton.method_46426() - this.resetButton.method_25368() - 2);
         this.resetButton.method_46419(y);
         this.keyButton.method_46421(this.resetButton.method_46426() - this.keyButton.method_25368() - 2);
         this.keyButton.method_46419(y);
      }

      this.keyButton.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.resetButton.method_25394(guiGraphics, mouseX, mouseY, delta);
      this.undoButton.method_25394(guiGraphics, mouseX, mouseY, delta);
   }
}
