package com.misterpemodder.shulkerboxtooltip.impl.config.gui;

import com.google.common.collect.UnmodifiableIterator;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.BooleanValueConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.CategoryTitleConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.ColorValueConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.ConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.EnumValueConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.IntegerValueConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.KeyValueConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry.PrefixTextConfigEntry;
import com.misterpemodder.shulkerboxtooltip.impl.tree.CategoryConfigNode;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ConfigNode;
import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import com.misterpemodder.shulkerboxtooltip.impl.util.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_339;
import net.minecraft.class_3675;
import net.minecraft.class_8030;
import net.minecraft.class_8087;
import net.minecraft.class_8209;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConfigCategoryTab<C> implements class_8087 {
   private final ConfigScreen<C> screen;
   private final CategoryConfigNode<C> category;
   private final C config;
   private final class_2561 title;
   private final class_2561 titleChanged;
   private final class_2561 titleError;
   private final class_2561 titleErrorChanged;
   private final ConfigEntryList list;
   private @Nullable ValueConfigNode<C, Key, Key> selectedKeyNode;
   private @Nullable class_8209 tabButton;

   public ConfigCategoryTab(ConfigScreen<C> screen, CategoryConfigNode<C> category, C config) {
      this.screen = screen;
      this.category = category;
      this.config = config;
      this.title = category.getTitle();
      this.titleChanged = this.title.method_27661().method_27692(class_124.field_1056);
      this.titleError = this.title.method_27661().method_27692(class_124.field_1061);
      this.titleErrorChanged = this.title.method_27661().method_27695(new class_124[]{class_124.field_1056, class_124.field_1061});
      List<ConfigEntry> entries = new ArrayList();
      UnmodifiableIterator var5 = category.getChildren().iterator();

      while(var5.hasNext()) {
         ConfigNode<C> node = (ConfigNode)var5.next();
         if (node.getPrefix() != null) {
            entries.add(new PrefixTextConfigEntry(this, node.getPrefix()));
         }

         if (node instanceof ValueConfigNode<C, ?, ?> valueNode) {
            entries.add(this.createValueEntry(valueNode));
         } else if (node instanceof CategoryConfigNode<C> categoryNode) {
            entries.addAll(this.createSubCategoryEntries(categoryNode));
         }
      }

      this.list = new ConfigEntryList(this, this.getMinecraft(), this.screen.field_22789, this.screen.field_22790 - this.screen.getHeaderHeight() - this.screen.getFooterHeight(), 0, 24, entries);
   }

   public @NotNull class_2561 method_48610() {
      return this.title;
   }

   public void method_48612(Consumer<class_339> consumer) {
      consumer.accept(this.list);
   }

   public void method_48611(class_8030 screenRectangle) {
      this.list.method_55444(screenRectangle.comp_1196(), screenRectangle.comp_1197(), screenRectangle.method_49620(), screenRectangle.method_49618());
   }

   private <T, V> ConfigEntry createValueEntry(ValueConfigNode<C, T, V> valueNode) {
      Class<? extends T> type = valueNode.getType();
      if (type.equals(Boolean.class)) {
         return new BooleanValueConfigEntry(this, valueNode);
      } else if (ColorKey.class.isAssignableFrom(type)) {
         return new ColorValueConfigEntry(this, valueNode);
      } else if (Enum.class.isAssignableFrom(type)) {
         return new EnumValueConfigEntry(this, valueNode);
      } else if (type.equals(Integer.class)) {
         return new IntegerValueConfigEntry(this, valueNode);
      } else if (Key.class.isAssignableFrom(type)) {
         return new KeyValueConfigEntry(this, valueNode);
      } else {
         throw new UnsupportedOperationException("Unsupported type: " + String.valueOf(type));
      }
   }

   private List<ConfigEntry> createSubCategoryEntries(CategoryConfigNode<C> categoryNode) {
      ArrayList<ConfigEntry> entries = new ArrayList(categoryNode.getChildren().size() + 1);
      entries.add(new CategoryTitleConfigEntry(this, categoryNode.getTitle()));
      UnmodifiableIterator var3 = categoryNode.getChildren().iterator();

      while(var3.hasNext()) {
         ConfigNode<C> node = (ConfigNode)var3.next();
         if (node instanceof ValueConfigNode<C, ?, ?> valueNode) {
            entries.add(this.createValueEntry(valueNode));
         }
      }

      return entries;
   }

   public @NotNull class_310 getMinecraft() {
      return (class_310)Objects.requireNonNull(this.screen.getMinecraft());
   }

   public ConfigScreen<C> getScreen() {
      return this.screen;
   }

   public @Nullable ValueConfigNode<C, Key, Key> getSelectedKeyNode() {
      return this.selectedKeyNode;
   }

   public void setSelectedKeyNode(@Nullable ValueConfigNode<C, Key, Key> selectedKeyNode) {
      this.selectedKeyNode = selectedKeyNode;
   }

   public void refresh() {
      if (this.tabButton != null) {
         boolean hasChanged = !this.category.isActiveValue(this.config);
         boolean hasError = this.category.validate(this.config) != null;
         class_2561 newTitle;
         if (hasError) {
            newTitle = hasChanged ? this.titleErrorChanged : this.titleError;
         } else {
            newTitle = hasChanged ? this.titleChanged : this.title;
         }

         this.tabButton.method_25355(newTitle);
         this.list.refreshEntries();
      }
   }

   public C getConfig() {
      return this.config;
   }

   public boolean keyPressed(int keyCode, int scanCode) {
      if (this.selectedKeyNode != null) {
         if (keyCode == 256) {
            this.selectedKeyNode.setEditingValue(Key.UNKNOWN_KEY);
         } else {
            this.selectedKeyNode.setEditingValue(new Key(class_3675.method_15985(keyCode, scanCode)));
         }

         this.selectedKeyNode = null;
         this.screen.refresh();
         return true;
      } else {
         return false;
      }
   }

   public void setTabButton(@Nullable class_8209 tabButton) {
      this.tabButton = tabButton;
   }
}
