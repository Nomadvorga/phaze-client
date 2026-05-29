package com.misterpemodder.shulkerboxtooltip.impl.config.gui;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.ConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.ValueConfigEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_4265;

@Environment(EnvType.CLIENT)
public final class ConfigEntryList extends class_4265<ConfigEntry> {
   private final ConfigCategoryTab<?> tab;

   public ConfigEntryList(ConfigCategoryTab<?> tab, class_310 minecraft, int width, int contentHeight, int headerHeight, int itemSpacing, Iterable<ConfigEntry> entries) {
      super(minecraft, width, contentHeight, headerHeight, itemSpacing);
      this.tab = tab;
      entries.forEach((x$0) -> this.method_25321(x$0));
   }

   public void method_48579(class_332 guiGraphics, int mouseX, int mouseY, float delta) {
      super.method_48579(guiGraphics, mouseX, mouseY, delta);
      ConfigEntry entry = (ConfigEntry)this.method_37019();
      if (entry != null) {
         if (entry instanceof ValueConfigEntry) {
            ValueConfigEntry<?, ?, ?> valueEntry = (ValueConfigEntry)entry;
            if (valueEntry.resetButton.method_49606()) {
               this.tab.getScreen().method_47415(ValueConfigEntry.RESET_BUTTON_TOOLTIP);
               return;
            }

            if (valueEntry.undoButton.method_49606()) {
               this.tab.getScreen().method_47415(ValueConfigEntry.UNDO_BUTTON_TOOLTIP);
               return;
            }
         }

         if (entry.getTooltip() != null) {
            this.tab.getScreen().method_47414(entry.getTooltip());
         }
      }

   }

   public int method_25322() {
      return this.field_22758 - 80;
   }

   public void refreshEntries() {
      this.method_25396().forEach(ConfigEntry::refresh);
   }

   protected void method_57713(class_332 guiGraphics) {
   }
}
