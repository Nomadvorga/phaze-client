package com.misterpemodder.shulkerboxtooltip.impl.tree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.ConfigCategory;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.RequiresRestart;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.Synchronize;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.Validator;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1074;
import net.minecraft.class_2487;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_5250;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RootConfigNode<C> implements ConfigNode<C> {
   public static final class_2561 TITLE = class_2561.method_43471("shulkerboxtooltip.config.title");
   private ImmutableList<CategoryConfigNode<C>> categories;

   private RootConfigNode(ImmutableList<CategoryConfigNode<C>> categories) {
      this.categories = categories;
   }

   public static <C> RootConfigNode<C> create(C defaultConfig) {
      return (new Builder<C>(defaultConfig)).build();
   }

   public void reload(C defaultConfig) {
      this.categories = (new Builder<CategoryConfigNode<C>>(defaultConfig)).build().getCategories();
   }

   public @NotNull String getName() {
      return "";
   }

   public @NotNull class_2561 getTitle() {
      return TITLE;
   }

   public @Nullable class_2561 getTooltip() {
      return null;
   }

   public @Nullable class_2561 getPrefix() {
      return null;
   }

   public void resetToDefault() {
      this.categories.forEach(ConfigNode::resetToDefault);
   }

   public void resetToActive(C config) {
      this.categories.forEach((category) -> category.resetToActive(config));
   }

   public boolean restartRequired(C config) {
      return this.categories.stream().anyMatch((categoryConfigNode) -> categoryConfigNode.restartRequired(config));
   }

   public boolean isDefaultValue(C config) {
      return this.categories.stream().allMatch((node) -> node.isDefaultValue(config));
   }

   public boolean isActiveValue(C config) {
      return this.categories.stream().allMatch((node) -> node.isActiveValue(config));
   }

   public @Nullable class_2561 validate(C config) {
      class_2561 error = null;
      UnmodifiableIterator var3 = this.categories.iterator();

      while(var3.hasNext()) {
         CategoryConfigNode<C> node = (CategoryConfigNode)var3.next();
         class_2561 result = node.validate(config);
         if (result != null) {
            if (error != null) {
               return CategoryConfigNode.MULTIPLE_ERRORS;
            }

            error = result;
         }
      }

      return error;
   }

   public @NotNull ImmutableList<CategoryConfigNode<C>> getCategories() {
      return this.categories;
   }

   public void writeToNbt(C config, class_2487 compound) {
      this.categories.forEach((node) -> node.writeToNbt(config, compound));
   }

   public void readFromNbt(C config, class_2487 compound) {
      this.categories.forEach((node) -> node.readFromNbt(config, compound));
   }

   public void copy(C from, C to) {
      this.categories.forEach((node) -> node.copy(from, to));
   }

   public void writeEditingToConfig(C config) {
      this.categories.forEach((node) -> node.writeEditingToConfig(config));
   }

   private static class Builder<C> {
      private Object defaultConfig;

      private Builder(C defaultConfig) {
         this.defaultConfig = defaultConfig;
      }

      public @NotNull RootConfigNode<C> build() {
         Class<?> configClass = this.defaultConfig.getClass();
         ImmutableList<CategoryConfigNode<C>> categories = (ImmutableList)Arrays.stream(configClass.getFields()).filter((field) -> field.isAnnotationPresent(ConfigCategory.class)).map((field) -> Pair.of(((ConfigCategory)field.getAnnotation(ConfigCategory.class)).ordinal(), field)).sorted(Comparator.comparingInt(Pair::getFirst)).map((pair) -> this.createCategoryNode((Field)pair.getSecond())).collect(ImmutableList.toImmutableList());
         this.defaultConfig = null;
         return new RootConfigNode<C>(categories);
      }

      private CategoryConfigNode<C> createCategoryNode(Field categoryField) {
         Object defaultCategory;
         try {
            categoryField.setAccessible(true);
            defaultCategory = categoryField.get(this.defaultConfig);
         } catch (InaccessibleObjectException | SecurityException | IllegalAccessException e) {
            throw new IllegalArgumentException("Failed to get category field", e);
         }

         Class<?> categoryClass = categoryField.getType();
         String categoryName = categoryField.getName();
         CategoryConfigNode.Builder<C> categoryBuilder = CategoryConfigNode.<C>builder().name(categoryName).title(class_2561.method_43471("shulkerboxtooltip.config.category." + ShulkerBoxTooltipUtil.snakeCase(categoryName)));

         for(Field valueField : categoryClass.getDeclaredFields()) {
            this.addValueNode(defaultCategory, categoryField, valueField, categoryBuilder);
         }

         return categoryBuilder.build();
      }

      private void addValueNode(Object defaultCategory, Field categoryField, Field valueField, CategoryConfigNode.Builder<C> categoryBuilder) {
         Object defaultValue;
         try {
            valueField.setAccessible(true);
            defaultValue = valueField.get(defaultCategory);
         } catch (InaccessibleObjectException | SecurityException | IllegalAccessException e) {
            throw new IllegalArgumentException("Failed to get value field", e);
         }

         if (EnvironmentUtil.isClient() && defaultValue instanceof ColorRegistry colorRegistry) {
            this.addColorRegistryField(colorRegistry, categoryBuilder);
         } else {
            this.addSingleValueField(defaultValue.getClass(), defaultValue, categoryField, valueField, categoryBuilder);
         }
      }

      private <T> void addSingleValueField(Class<? extends T> type, T defaultValue, Field categoryField, Field valueField, CategoryConfigNode.Builder<C> categoryBuilder) {
         String valueName = valueField.getName();
         String var10000 = ShulkerBoxTooltipUtil.snakeCase(categoryField.getName());
         String titleKey = "shulkerboxtooltip.config.option." + var10000 + "." + ShulkerBoxTooltipUtil.snakeCase(valueName);
         class_5250 title = class_2561.method_43471(titleKey);
         class_5250 tooltip = class_2561.method_43471(titleKey + ".tooltip");
         String prefixKey = titleKey + ".prefix";
         categoryBuilder.value((valueBuilder) -> {
            valueBuilder.type(type).valueType(type).name(valueName).title(title).tooltip(tooltip).defaultValue(defaultValue).valueReader(this.makeValueReader(type, categoryField, valueField)).valueWriter(this.makeValueWriter(type, categoryField, valueField)).requiresRestart(valueField.isAnnotationPresent(RequiresRestart.class));
            if (EnvironmentUtil.isClient() && class_1074.method_4663(prefixKey)) {
               valueBuilder.prefix(class_2561.method_43471(prefixKey));
            }

            if (valueField.isAnnotationPresent(Synchronize.class)) {
               valueBuilder.nbtReader(this.makeNbtReader(type, valueField.getName(), defaultValue)).nbtWriter(this.makeNbtWriter(valueField.getName(), defaultValue));
            }

            Validator validatorAnnotation = (Validator)valueField.getAnnotation(Validator.class);
            if (validatorAnnotation != null) {
               valueBuilder.validator(this.makeValueValidator(validatorAnnotation, valueField));
            }

            return valueBuilder;
         });
      }

      @Environment(EnvType.CLIENT)
      private void addColorRegistryField(ColorRegistry colorRegistry, CategoryConfigNode.Builder<C> categoryBuilder) {
         this.addColorRegistryCategoryNode(colorRegistry.defaultCategory(), ShulkerBoxTooltipUtil.id("default"), categoryBuilder);

         for(Map.Entry<class_2960, ColorRegistry.Category> entry : colorRegistry.categories().entrySet()) {
            class_2960 categoryId = (class_2960)entry.getKey();
            ColorRegistry.Category colorCategory = (ColorRegistry.Category)entry.getValue();
            if (colorCategory != colorRegistry.defaultCategory()) {
               this.addColorRegistryCategoryNode(colorCategory, categoryId, categoryBuilder);
            }
         }

      }

      @Environment(EnvType.CLIENT)
      private void addColorRegistryCategoryNode(ColorRegistry.Category colorCategory, class_2960 categoryId, CategoryConfigNode.Builder<C> categoryBuilder) {
         categoryBuilder.category((subCategoryBuilder) -> {
            String var10000 = categoryId.method_12836();
            String titleKey = "shulkerboxtooltip.colors." + var10000 + "." + categoryId.method_12832();
            class_5250 title = class_2561.method_43471(titleKey);
            subCategoryBuilder.name(categoryId.toString()).title(title);

            for(Map.Entry<String, ColorKey> entry : colorCategory.keys().entrySet()) {
               this.addColorKeyValueNode((ColorKey)entry.getValue(), (String)entry.getKey(), colorCategory, subCategoryBuilder);
            }

            return subCategoryBuilder;
         });
      }

      @Environment(EnvType.CLIENT)
      private void addColorKeyValueNode(ColorKey colorKey, String colorKeyId, ColorRegistry.Category colorCategory, CategoryConfigNode.Builder<C> subCategoryBuilder) {
         String titleKey = colorCategory.keyUnlocalizedName(colorKey);
         subCategoryBuilder.value((valueBuilder) -> valueBuilder.type(ColorKey.class).valueType(Integer.class).name(colorKeyId).title(titleKey == null ? class_2561.method_43470(colorKeyId) : class_2561.method_43471(titleKey)).defaultValue(colorKey.defaultRgb()).valueReader((s) -> colorKey.rgb()).valueWriter((s, v) -> colorKey.setRgb(v)).validator((v) -> v != null && (v & -16777216) == 0 ? null : class_2561.method_43471("shulkerboxtooltip.config.validator.invalid_color")));
      }

      private <T> ValueConfigNode.ValueReader<C, T> makeValueReader(Class<? extends T> type, Field categoryField, Field valueField) {
         try {
            valueField.setAccessible(true);
         } catch (SecurityException | InaccessibleObjectException e) {
            throw new IllegalArgumentException("Failed to set value field accessible", e);
         }

         return (config) -> {
            try {
               return type.cast(valueField.get(categoryField.get(config)));
            } catch (ClassCastException | IllegalAccessException e) {
               throw new IllegalArgumentException("Failed to get value field", e);
            }
         };
      }

      private <T> ValueConfigNode.ValueWriter<C, T> makeValueWriter(Class<? extends T> type, Field categoryField, Field valueField) {
         try {
            valueField.setAccessible(true);
         } catch (SecurityException | InaccessibleObjectException e) {
            throw new IllegalArgumentException("Failed to set value field accessible", e);
         }

         return (config, value) -> {
            try {
               valueField.set(categoryField.get(config), type.cast(value));
            } catch (ClassCastException | IllegalAccessException e) {
               throw new IllegalArgumentException("Failed to set value field", e);
            }
         };
      }

      private <T> ValueConfigNode.ValueReader<class_2487, T> makeNbtReader(Class<? extends T> type, String valueName, T defaultValue) {
         Objects.requireNonNull(defaultValue);
         byte var5 = 0;
         ValueConfigNode.ValueReader var10000;
         //$FF: var5->value
         //0->java/lang/Enum
         //1->java/lang/Boolean
         //2->java/lang/String
         //3->java/lang/Integer
         switch (((Class)defaultValue).typeSwitch<invokedynamic>(defaultValue, var5)) {
            case 0:
               Enum<?> ignored = (Enum)defaultValue;
               var10000 = (tag) -> Enum.valueOf(type, tag.method_10558(valueName));
               break;
            case 1:
               Boolean ignored = (Boolean)defaultValue;
               var10000 = (tag) -> tag.method_10577(valueName);
               break;
            case 2:
               String ignored = (String)defaultValue;
               var10000 = (tag) -> tag.method_10558(valueName);
               break;
            case 3:
               Integer ignored = (Integer)defaultValue;
               var10000 = (tag) -> tag.method_10550(valueName);
               break;
            default:
               throw new IllegalArgumentException("Unsupported value type: " + String.valueOf(defaultValue.getClass()));
         }

         return var10000;
      }

      private <T> ValueConfigNode.ValueWriter<class_2487, T> makeNbtWriter(String valueName, T defaultValue) {
         Objects.requireNonNull(defaultValue);
         byte var4 = 0;
         ValueConfigNode.ValueWriter var10000;
         //$FF: var4->value
         //0->java/lang/Enum
         //1->java/lang/Boolean
         //2->java/lang/String
         //3->java/lang/Integer
         switch (((Class)defaultValue).typeSwitch<invokedynamic>(defaultValue, var4)) {
            case 0:
               Enum<?> ignored = (Enum)defaultValue;
               var10000 = (tag, value) -> tag.method_10582(valueName, ((Enum)value).name());
               break;
            case 1:
               Boolean ignored = (Boolean)defaultValue;
               var10000 = (tag, value) -> tag.method_10556(valueName, (Boolean)value);
               break;
            case 2:
               String ignored = (String)defaultValue;
               var10000 = (tag, value) -> tag.method_10582(valueName, (String)value);
               break;
            case 3:
               Integer ignored = (Integer)defaultValue;
               var10000 = (tag, value) -> tag.method_10569(valueName, (Integer)value);
               break;
            default:
               throw new IllegalArgumentException("Unsupported value type: " + String.valueOf(defaultValue.getClass()));
         }

         return var10000;
      }

      private <T> ValueConfigNode.ValueValidator<T> makeValueValidator(Validator validatorAnnotation, Field valueField) {
         try {
            return (ValueConfigNode.ValueValidator)validatorAnnotation.value().getDeclaredConstructor().newInstance();
         } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Failed to create validator for config field " + String.valueOf(valueField), e);
         }
      }
   }
}
