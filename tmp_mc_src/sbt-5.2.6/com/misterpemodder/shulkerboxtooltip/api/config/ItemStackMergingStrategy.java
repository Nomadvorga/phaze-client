package com.misterpemodder.shulkerboxtooltip.api.config;

public enum ItemStackMergingStrategy {
   IGNORE,
   FIRST_ITEM,
   SEPARATE;

   public String toString() {
      return "shulkerboxtooltip.config.item_stack_merging_strategy." + this.name().toLowerCase();
   }

   // $FF: synthetic method
   private static ItemStackMergingStrategy[] $values() {
      return new ItemStackMergingStrategy[]{IGNORE, FIRST_ITEM, SEPARATE};
   }
}
