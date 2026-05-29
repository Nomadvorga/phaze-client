package com.misterpemodder.shulkerboxtooltip.api.color;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2960;
import org.jetbrains.annotations.ApiStatus.NonExtendable;

@NonExtendable
@Environment(EnvType.CLIENT)
public interface ColorRegistry {
   @Nonnull
   Category category(class_2960 var1);

   @Nonnull
   Category defaultCategory();

   @Nonnull
   Map<class_2960, Category> categories();

   public interface Category {
      @Nullable
      ColorKey key(String var1);

      @Nullable
      String keyUnlocalizedName(ColorKey var1);

      default Category register(ColorKey key, String colorId) {
         return this.register(key, colorId, (String)null);
      }

      Category register(ColorKey var1, String var2, @Nullable String var3);

      Map<String, ColorKey> keys();
   }
}
