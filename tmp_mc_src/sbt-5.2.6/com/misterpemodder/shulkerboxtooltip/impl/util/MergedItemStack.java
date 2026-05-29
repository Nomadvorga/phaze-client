package com.misterpemodder.shulkerboxtooltip.impl.util;

import com.misterpemodder.shulkerboxtooltip.api.config.ItemStackMergingStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import net.minecraft.class_1799;
import net.minecraft.class_2371;

public class MergedItemStack implements Comparable<MergedItemStack> {
   private class_1799 merged;
   private final class_2371<class_1799> subItems;
   private int firstSlot;

   public MergedItemStack(int slotCount) {
      this.merged = class_1799.field_8037;
      this.subItems = class_2371.method_10213(slotCount, class_1799.field_8037);
      this.firstSlot = Integer.MAX_VALUE;
   }

   public class_1799 get() {
      return this.merged;
   }

   public void add(class_1799 stack, int slot, ItemStackMergingStrategy mergingStrategy) {
      if (slot >= 0 && slot < this.subItems.size()) {
         this.subItems.set(slot, stack.method_7972());
         if (slot < this.firstSlot) {
            this.firstSlot = slot;
         }

         if (this.merged.method_7960()) {
            if (mergingStrategy == ItemStackMergingStrategy.IGNORE) {
               this.merged = copyStackWithoutComponents(stack);
            } else {
               this.merged = stack.method_7972();
            }
         } else {
            this.merged.method_7933(stack.method_7947());
         }

      }
   }

   private static class_1799 copyStackWithoutComponents(class_1799 stack) {
      if (stack.method_7960()) {
         return class_1799.field_8037;
      } else {
         class_1799 copy = new class_1799(stack.method_7909(), stack.method_7947());
         copy.method_7912(stack.method_7965());
         return copy;
      }
   }

   public class_1799 getSubStack(int slot) {
      return slot >= 0 && slot < this.subItems.size() ? (class_1799)this.subItems.get(slot) : class_1799.field_8037;
   }

   public int size() {
      return this.subItems.size();
   }

   public int compareTo(MergedItemStack other) {
      int ret = this.merged.method_7947() - other.merged.method_7947();
      return ret != 0 ? ret : other.firstSlot - this.firstSlot;
   }

   public static List<MergedItemStack> mergeInventory(List<class_1799> inventory, int maxSize, ItemStackMergingStrategy mergingStrategy) {
      ArrayList<MergedItemStack> items = new ArrayList();
      if (!inventory.isEmpty()) {
         HashMap<ItemKey, MergedItemStack> mergedStacks = new HashMap();
         int i = 0;

         for(int len = inventory.size(); i < len; ++i) {
            class_1799 s = (class_1799)inventory.get(i);
            if (!s.method_7960()) {
               ItemKey k = new ItemKey(s, mergingStrategy != ItemStackMergingStrategy.SEPARATE);
               MergedItemStack mergedStack = (MergedItemStack)mergedStacks.get(k);
               if (mergedStack == null) {
                  mergedStack = new MergedItemStack(maxSize);
                  mergedStacks.put(k, mergedStack);
               }

               mergedStack.add(s, i, mergingStrategy);
            }
         }

         items.addAll(mergedStacks.values());
         items.sort(Comparator.reverseOrder());
      }

      return items;
   }
}
