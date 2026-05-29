package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import com.misterpemodder.shulkerboxtooltip.impl.config.gui.ConfigCategoryTab;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_364;
import net.minecraft.class_4185;
import net.minecraft.class_5481;
import net.minecraft.class_6379;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ValueConfigEntry<C, T, V> extends ConfigEntry {
   private final class_2561 label;
   private final class_2561 labelChanged;
   private final class_2561 labelError;
   private final class_2561 labelErrorChanged;
   private final @Nullable List<class_5481> tooltip;
   private List<class_5481> tooltipWithError;
   protected final ConfigCategoryTab<C> tab;
   protected final List<class_339> children = Lists.newArrayList();
   protected final ValueConfigNode<C, T, V> valueNode;
   public final class_4185 resetButton;
   public final class_4185 undoButton;
   private @Nullable class_2561 validationError;
   private boolean hasChanged;
   public static final class_2561 RESET_BUTTON_LABEL = class_2561.method_43471("shulkerboxtooltip.config.reset_to_default.small");
   public static final class_2561 RESET_BUTTON_TOOLTIP = class_2561.method_43471("shulkerboxtooltip.config.reset_to_default.full");
   public static final class_2561 UNDO_BUTTON_LABEL = class_2561.method_43471("shulkerboxtooltip.config.undo.small");
   public static final class_2561 UNDO_BUTTON_TOOLTIP = class_2561.method_43471("shulkerboxtooltip.config.undo.full");

   protected ValueConfigEntry(ConfigCategoryTab<C> tab, ValueConfigNode<C, T, V> valueNode) {
      this.tab = tab;
      this.label = valueNode.getTitle().method_27661().method_27692(class_124.field_1080);
      this.labelChanged = this.label.method_27661().method_27695(new class_124[]{class_124.field_1056, class_124.field_1068});
      this.labelError = this.label.method_27661().method_27692(class_124.field_1061);
      this.labelErrorChanged = this.label.method_27661().method_27695(new class_124[]{class_124.field_1056, class_124.field_1061});
      this.tooltip = valueNode.getTooltip() == null ? null : tab.getMinecraft().field_1772.method_1728(valueNode.getTooltip(), 350);
      this.tooltipWithError = this.getTooltipWithError();
      this.valueNode = valueNode;
      this.resetButton = class_4185.method_46430(RESET_BUTTON_LABEL, (b) -> this.resetToDefault()).method_46434(0, 0, Math.max(tab.getMinecraft().field_1772.method_27525(RESET_BUTTON_LABEL) + 6, 20), 20).method_46431();
      this.resetButton.field_22763 = !valueNode.isDefaultValue(this.tab.getConfig());
      this.children.add(this.resetButton);
      this.undoButton = class_4185.method_46430(UNDO_BUTTON_LABEL, (b) -> this.resetToActive()).method_46434(0, 0, Math.max(tab.getMinecraft().field_1772.method_27525(UNDO_BUTTON_LABEL) + 6, 20), 20).method_46431();
      this.undoButton.field_22763 = !valueNode.isActiveValue(this.tab.getConfig());
      this.children.add(this.undoButton);
   }

   public void resetToDefault() {
      this.valueNode.resetToDefault();
      this.tab.getScreen().refresh();
   }

   public void resetToActive() {
      this.valueNode.resetToActive(this.tab.getConfig());
      this.tab.getScreen().refresh();
   }

   public V getValue() {
      return this.valueNode.getEditingValue(this.tab.getConfig());
   }

   public void setValue(V value) {
      this.valueNode.setEditingValue(value);
      this.tab.getScreen().refresh();
   }

   public void refresh() {
      this.resetButton.field_22763 = !this.valueNode.isDefaultValue(this.tab.getConfig());
      this.undoButton.field_22763 = !this.valueNode.isActiveValue(this.tab.getConfig());
      this.validationError = this.valueNode.validate(this.tab.getConfig());
      this.hasChanged = !this.valueNode.isActiveValue(this.tab.getConfig());
      this.tooltipWithError = this.getTooltipWithError();
   }

   public @NotNull List<? extends class_364> method_25396() {
      return this.children;
   }

   public @NotNull List<? extends class_6379> method_37025() {
      return this.children;
   }

   public @Nullable List<class_5481> getTooltip() {
      return this.validationError != null ? this.tooltipWithError : this.tooltip;
   }

   private List<class_5481> getTooltipWithError() {
      if (this.validationError != null) {
         List<class_5481> errorTooltip = new ArrayList();
         if (this.tooltip != null) {
            errorTooltip.addAll(this.tooltip);
         }

         errorTooltip.add(this.validationError.method_27661().method_27692(class_124.field_1061).method_30937());
         return errorTooltip;
      } else {
         return this.tooltip;
      }
   }

   protected void renderLabel(class_332 guiGraphics, int x, int y, int entryWidth) {
      class_2561 l;
      if (this.validationError != null) {
         l = this.hasChanged ? this.labelErrorChanged : this.labelError;
      } else {
         l = this.hasChanged ? this.labelChanged : this.label;
      }

      if (this.tab.getMinecraft().field_1772.method_1726()) {
         x = x + entryWidth - this.tab.getMinecraft().field_1772.method_27525(l);
      }

      guiGraphics.method_51430(this.tab.getMinecraft().field_1772, l.method_30937(), x, y + 5, -1, false);
   }
}
