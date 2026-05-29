package com.misterpemodder.shulkerboxtooltip.impl.tree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.class_2487;
import net.minecraft.class_2561;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CategoryConfigNode<C> implements ConfigNode<C> {
   private String name;
   private class_2561 title;
   private class_2561 tooltip;
   private ImmutableList<ConfigNode<C>> children;
   public static final class_2561 MULTIPLE_ERRORS = class_2561.method_43471("shulkerboxtooltip.config.validator.multiple_errors");

   private CategoryConfigNode() {
   }

   public static <C> Builder<C> builder() {
      return new Builder<C>();
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
      return null;
   }

   public void resetToDefault() {
      this.children.forEach(ConfigNode::resetToDefault);
   }

   public void resetToActive(C config) {
      this.children.forEach((child) -> child.resetToActive(config));
   }

   public boolean restartRequired(C config) {
      return this.children.stream().anyMatch((configNode) -> configNode.restartRequired(config));
   }

   public boolean isDefaultValue(C config) {
      return this.children.stream().allMatch((node) -> node.isDefaultValue(config));
   }

   public boolean isActiveValue(C config) {
      return this.children.stream().allMatch((node) -> node.isActiveValue(config));
   }

   public @Nullable class_2561 validate(C config) {
      class_2561 error = null;
      UnmodifiableIterator var3 = this.children.iterator();

      while(var3.hasNext()) {
         ConfigNode<C> node = (ConfigNode)var3.next();
         class_2561 result = node.validate(config);
         if (result != null) {
            if (error != null) {
               return MULTIPLE_ERRORS;
            }

            error = result;
         }
      }

      return error;
   }

   public @NotNull ImmutableList<ConfigNode<C>> getChildren() {
      return this.children;
   }

   public void writeToNbt(C config, class_2487 compound) {
      class_2487 subTag = new class_2487();
      this.children.forEach((node) -> node.writeToNbt(config, subTag));
      if (!subTag.method_33133()) {
         compound.method_10566(this.getName(), subTag);
      }

   }

   public void readFromNbt(C config, class_2487 compound) {
      if (compound.method_10573(this.getName(), 10)) {
         class_2487 subTag = compound.method_10562(this.getName());
         this.children.forEach((node) -> node.readFromNbt(config, subTag));
      }
   }

   public void copy(C from, C to) {
      this.children.forEach((node) -> node.copy(from, to));
   }

   public void writeEditingToConfig(C config) {
      this.children.forEach((node) -> node.writeEditingToConfig(config));
   }

   public static final class Builder<C> {
      private CategoryConfigNode<C> node = new CategoryConfigNode<C>();
      private ImmutableList.Builder<ConfigNode<C>> childrenBuilder = ImmutableList.builder();

      private Builder() {
      }

      public Builder<C> name(String name) {
         this.node.name = name;
         return this;
      }

      public Builder<C> title(class_2561 title) {
         this.node.title = title;
         return this;
      }

      public Builder<C> tooltip(class_2561 tooltip) {
         this.node.tooltip = tooltip;
         return this;
      }

      public <T, V> Builder<C> value(UnaryOperator<ValueConfigNode.Builder<C, T, V>> valueBuilder) {
         this.childrenBuilder.add(((ValueConfigNode.Builder)valueBuilder.apply(ValueConfigNode.builder())).category(this.node).build());
         return this;
      }

      public Builder<C> category(UnaryOperator<Builder<C>> categoryBuilder) {
         this.childrenBuilder.add(((Builder)categoryBuilder.apply(new Builder())).build());
         return this;
      }

      public CategoryConfigNode<C> build() {
         CategoryConfigNode<C> n = this.node;
         Objects.requireNonNull(n.name);
         Objects.requireNonNull(n.title);
         n.children = this.childrenBuilder.build();
         this.node = null;
         this.childrenBuilder = null;
         return n;
      }
   }
}
