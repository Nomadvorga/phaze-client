package com.misterpemodder.shulkerboxtooltip.impl;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_7225;

public record PreviewContextImpl(class_1799 stack, @Nullable class_1657 owner, Configuration config, @Nullable class_7225.class_7874 registryLookup) implements PreviewContext {
   public static class Builder implements PreviewContext.Builder {
      private final class_1799 stack;
      private class_1657 owner;
      private class_7225.class_7874 registryLookup;

      public Builder(class_1799 stack) {
         this.stack = stack;
      }

      public Builder withOwner(@Nullable class_1657 owner) {
         this.owner = owner;
         return this;
      }

      public Builder withRegistryLookup(@Nullable class_7225.class_7874 registryLookup) {
         this.registryLookup = registryLookup;
         return this;
      }

      @Nonnull
      public PreviewContext build() {
         if (this.registryLookup == null && this.owner != null) {
            this.registryLookup = this.owner.method_56673();
         }

         return new PreviewContextImpl(this.stack, this.owner, ShulkerBoxTooltip.config, this.registryLookup);
      }
   }
}
