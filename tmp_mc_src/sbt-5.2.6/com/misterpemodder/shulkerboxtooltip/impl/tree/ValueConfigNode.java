package com.misterpemodder.shulkerboxtooltip.impl.tree;

import java.util.Objects;
import net.minecraft.class_2487;
import net.minecraft.class_2561;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ValueConfigNode<C, T, V> implements ConfigNode<C> {
   private String name;
   private class_2561 title;
   private class_2561 tooltip;
   private class_2561 prefix;
   private Class<? extends T> type;
   private Class<? extends V> valueType;
   private boolean requiresRestart;
   private ValueReader<C, V> valueReader;
   private ValueWriter<C, V> valueWriter;
   private @Nullable ValueReader<class_2487, V> nbtReader;
   private @Nullable ValueWriter<class_2487, V> nbtWriter;
   private @Nullable ValueValidator<V> validator;
   private V defaultValue;
   private V editingValue;
   private CategoryConfigNode<C> category;

   private ValueConfigNode() {
   }

   public static <C, T, V> Builder<C, T, V> builder() {
      return new Builder<C, T, V>();
   }

   public Class<? extends T> getType() {
      return this.type;
   }

   public @NotNull Class<? extends V> getValueType() {
      return this.valueType;
   }

   public V getDefaultValue() {
      return this.defaultValue;
   }

   public V getActiveValue(C config) {
      return this.valueReader.read(config);
   }

   public void setActiveValue(C config, V value) {
      this.valueWriter.write(config, value);
   }

   public V getEditingValue(C config) {
      if (this.editingValue == null) {
         this.setEditingValue(this.getActiveValue(config));
      }

      return this.editingValue;
   }

   public void setEditingValue(V value) {
      this.editingValue = value;
   }

   public void resetToDefault() {
      this.setEditingValue(this.getDefaultValue());
   }

   public void resetToActive(C config) {
      this.setEditingValue(this.getActiveValue(config));
   }

   public boolean isDefaultValue(C config) {
      return Objects.equals(this.getDefaultValue(), this.getEditingValue(config));
   }

   public boolean isActiveValue(C config) {
      return Objects.equals(this.getActiveValue(config), this.getEditingValue(config));
   }

   public class_2561 validate(C config) {
      return this.validator == null ? null : this.validator.validate(this.getEditingValue(config));
   }

   public @NotNull String getName() {
      return this.name;
   }

   public @NotNull class_2561 getTitle() {
      return this.title;
   }

   public @Nullable class_2561 getTooltip() {
      return this.tooltip;
   }

   public @Nullable class_2561 getPrefix() {
      return this.prefix;
   }

   public boolean restartRequired(C config) {
      return this.requiresRestart && !this.isActiveValue(config);
   }

   public void writeToNbt(C config, class_2487 compound) {
      if (this.nbtWriter != null) {
         this.nbtWriter.write(compound, this.getActiveValue(config));
      }
   }

   public void readFromNbt(C config, class_2487 compound) {
      if (this.nbtReader != null) {
         this.setActiveValue(config, this.nbtReader.read(compound));
      }
   }

   public void copy(C from, C to) {
      this.setActiveValue(to, this.getActiveValue(from));
   }

   public void writeEditingToConfig(C config) {
      this.setActiveValue(config, this.getEditingValue(config));
   }

   public static class Builder<C, T, V> {
      private ValueConfigNode<C, T, V> node = new ValueConfigNode<C, T, V>();

      private Builder() {
      }

      public Builder<C, T, V> type(Class<? extends T> type) {
         this.node.type = type;
         return this;
      }

      public Builder<C, T, V> valueType(Class<? extends V> valueType) {
         this.node.valueType = valueType;
         return this;
      }

      public Builder<C, T, V> name(String name) {
         this.node.name = name;
         return this;
      }

      public Builder<C, T, V> title(class_2561 title) {
         this.node.title = title;
         return this;
      }

      public Builder<C, T, V> tooltip(class_2561 tooltip) {
         this.node.tooltip = tooltip;
         return this;
      }

      public Builder<C, T, V> prefix(class_2561 prefix) {
         this.node.prefix = prefix;
         return this;
      }

      public Builder<C, T, V> defaultValue(V defaultValue) {
         this.node.defaultValue = defaultValue;
         return this;
      }

      public Builder<C, T, V> requiresRestart(boolean requiresRestart) {
         this.node.requiresRestart = requiresRestart;
         return this;
      }

      public Builder<C, T, V> valueReader(ValueReader<C, V> valueReader) {
         this.node.valueReader = valueReader;
         return this;
      }

      public Builder<C, T, V> valueWriter(ValueWriter<C, V> valueWriter) {
         this.node.valueWriter = valueWriter;
         return this;
      }

      public Builder<C, T, V> validator(ValueValidator<V> validator) {
         this.node.validator = validator;
         return this;
      }

      public Builder<C, T, V> nbtReader(ValueReader<class_2487, V> nbtReader) {
         this.node.nbtReader = nbtReader;
         return this;
      }

      public Builder<C, T, V> nbtWriter(ValueWriter<class_2487, V> nbtWriter) {
         this.node.nbtWriter = nbtWriter;
         return this;
      }

      public Builder<C, T, V> category(CategoryConfigNode<C> category) {
         this.node.category = category;
         return this;
      }

      public ValueConfigNode<C, T, V> build() {
         ValueConfigNode<C, T, V> n = this.node;
         Objects.requireNonNull(n.name);
         Objects.requireNonNull(n.type);
         Objects.requireNonNull(n.valueType);
         Objects.requireNonNull(n.title);
         Objects.requireNonNull(n.valueReader);
         Objects.requireNonNull(n.valueWriter);
         Objects.requireNonNull(n.category);
         this.node = null;
         return n;
      }
   }

   @FunctionalInterface
   public interface ValueReader<S, V> {
      V read(S var1);
   }

   public interface ValueValidator<V> {
      @Nullable class_2561 validate(V var1);
   }

   @FunctionalInterface
   public interface ValueWriter<S, V> {
      void write(S var1, V var2);
   }
}
