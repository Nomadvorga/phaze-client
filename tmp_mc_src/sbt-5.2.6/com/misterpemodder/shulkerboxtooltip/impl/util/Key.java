package com.misterpemodder.shulkerboxtooltip.impl.util;

import java.util.Objects;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_3675;
import net.minecraft.class_3675.class_307;

@Environment(EnvType.CLIENT)
public final class Key {
   public static final Key UNKNOWN_KEY;
   class_3675.class_306 inner;

   public Key(class_3675.class_306 key) {
      this.inner = key;
   }

   public class_3675.class_306 get() {
      return this.inner;
   }

   public boolean isUnbound() {
      return this.inner.equals(class_3675.field_16237);
   }

   public void set(class_3675.class_306 key) {
      this.inner = key;
   }

   @Nullable
   public static Key defaultPreviewKey() {
      return new Key(class_307.field_1668.method_1447(340));
   }

   @Nullable
   public static Key defaultFullPreviewKey() {
      return new Key(class_307.field_1668.method_1447(342));
   }

   @Nullable
   public static Key defaultLockTooltipKey() {
      return new Key(class_307.field_1668.method_1447(341));
   }

   public static Key fromTranslationKey(@Nullable String translationKey) {
      if (translationKey == null) {
         return UNKNOWN_KEY;
      } else {
         try {
            return new Key(class_3675.method_15981(translationKey));
         } catch (Exception var2) {
            return UNKNOWN_KEY;
         }
      }
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Key key = (Key)o;
         return Objects.equals(this.inner, key.inner);
      } else {
         return false;
      }
   }

   public int hashCode() {
      return Objects.hashCode(this.inner);
   }

   static {
      UNKNOWN_KEY = new Key(class_3675.field_16237);
   }
}
