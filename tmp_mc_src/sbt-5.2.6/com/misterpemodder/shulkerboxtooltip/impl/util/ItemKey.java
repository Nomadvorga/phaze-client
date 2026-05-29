package com.misterpemodder.shulkerboxtooltip.impl.util;

import java.util.Objects;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_7923;
import net.minecraft.class_9323;

public class ItemKey {
   private final class_1792 item;
   private final int id;
   private final class_9323 components;
   private final boolean ignoreComponents;

   public ItemKey(class_1799 stack, boolean ignoreComponents) {
      this.item = stack.method_7909();
      this.id = class_7923.field_41178.method_10206(this.item);
      this.components = stack.method_57353();
      this.ignoreComponents = ignoreComponents;
   }

   public int hashCode() {
      return 31 * this.id + (!this.ignoreComponents && this.components != null ? this.components.hashCode() : 0);
   }

   public boolean equals(Object other) {
      if (this == other) {
         return true;
      } else if (!(other instanceof ItemKey)) {
         return false;
      } else {
         ItemKey key = (ItemKey)other;
         return key.item == this.item && key.id == this.id && (this.ignoreComponents || Objects.equals(key.components, this.components));
      }
   }
}
