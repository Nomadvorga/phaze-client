package com.misterpemodder.shulkerboxtooltip.impl.color;

import com.google.common.base.Preconditions;
import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2960;

@Environment(EnvType.CLIENT)
public final class ColorRegistryImpl implements ColorRegistry {
   private final Map<class_2960, Category> categories = new HashMap();
   private final Map<class_2960, Category> emptyCategories = new HashMap();
   private final Map<class_2960, ColorRegistry.Category> categoriesView;
   private boolean locked;
   private int registeredKeysCount;
   public static final ColorRegistryImpl INSTANCE = new ColorRegistryImpl();

   public ColorRegistryImpl() {
      this.categoriesView = Collections.unmodifiableMap(this.categories);
      this.locked = true;
      this.registeredKeysCount = 0;
   }

   @Nonnull
   public Category category(class_2960 categoryId) {
      Category category = (Category)this.categories.get(categoryId);
      return category == null ? (Category)this.emptyCategories.computeIfAbsent(categoryId, (x$0) -> new Category(x$0)) : category;
   }

   @Nonnull
   public ColorRegistry.Category defaultCategory() {
      return this.category(ShulkerBoxTooltipUtil.id("default"));
   }

   @Nonnull
   public Map<class_2960, ColorRegistry.Category> categories() {
      return this.categoriesView;
   }

   public void setLocked(boolean locked) {
      this.locked = locked;
   }

   public void resetRegisteredKeysCount() {
      this.registeredKeysCount = 0;
   }

   public int registeredKeysCount() {
      return this.registeredKeysCount;
   }

   public final class Category implements ColorRegistry.Category {
      private final class_2960 id;
      private Map<String, ColorKey> keys = null;
      private Map<ColorKey, String> unlocalizedNames = Collections.emptyMap();
      private Map<String, ColorKey> keysView = Collections.emptyMap();
      private Map<String, Integer> lateKeyValues = null;

      public Category(class_2960 id) {
         this.id = id;
      }

      @Nullable
      public ColorKey key(String colorId) {
         return (ColorKey)this.keysView.get(colorId);
      }

      public void setRgbKeyLater(String colorId, int rgb) {
         if (this.lateKeyValues == null) {
            this.lateKeyValues = new HashMap();
         }

         this.lateKeyValues.put(colorId, rgb);
      }

      @Nullable
      public String keyUnlocalizedName(ColorKey key) {
         return (String)this.unlocalizedNames.get(key);
      }

      public ColorRegistry.Category register(ColorKey key, String colorId, @Nullable String unlocalizedName) {
         Preconditions.checkNotNull(key, "cannot register null color key");
         Preconditions.checkNotNull(colorId, "cannot register null color ID");
         if (ColorRegistryImpl.this.locked) {
            throw new IllegalStateException("Cannot register color keys outside the scope of ShulkerBoxTooltipApi.registerColors()");
         } else {
            this.registerSelf();
            this.registerKey(key, colorId, unlocalizedName);
            this.setLateKeyValue(key, colorId);
            return this;
         }
      }

      private void registerSelf() {
         if (this.keys == null) {
            this.keys = new LinkedHashMap();
            this.unlocalizedNames = new HashMap();
            this.keysView = Collections.unmodifiableMap(this.keys);
            ColorRegistryImpl.this.categories.put(this.id, this);
            ColorRegistryImpl.this.emptyCategories.remove(this.id);
         }
      }

      private void registerKey(ColorKey key, String colorId, @Nullable String unlocalizedName) {
         if (this.keys.containsKey(colorId)) {
            ShulkerBoxTooltip.LOGGER.warn("Overriding color key " + colorId + " for category " + String.valueOf(this.id));
         }

         if (unlocalizedName == null) {
            String var10000 = this.id.method_12836();
            unlocalizedName = "shulkerboxtooltip.colors." + var10000 + "." + this.id.method_12832() + "." + colorId;
         }

         this.keys.put(colorId, key);
         this.unlocalizedNames.put(key, unlocalizedName);
         ++ColorRegistryImpl.this.registeredKeysCount;
      }

      private void setLateKeyValue(ColorKey key, String colorId) {
         if (this.lateKeyValues != null && this.lateKeyValues.containsKey(colorId)) {
            key.setRgb((Integer)this.lateKeyValues.get(colorId));
            this.lateKeyValues.remove(colorId);
         }

      }

      public Map<String, ColorKey> keys() {
         return this.keysView;
      }
   }
}
