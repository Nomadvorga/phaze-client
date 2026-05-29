package com.misterpemodder.shulkerboxtooltip.api;

import com.misterpemodder.shulkerboxtooltip.api.config.PreviewConfiguration;
import com.misterpemodder.shulkerboxtooltip.impl.PreviewContextImpl;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_7225;
import org.jetbrains.annotations.Contract;

public interface PreviewContext {
   @Nonnull
   @Contract("_ -> new")
   static Builder builder(class_1799 stack) {
      return new PreviewContextImpl.Builder(stack);
   }

   @Nonnull
   class_1799 stack();

   @Nullable
   class_1657 owner();

   @Nonnull
   PreviewConfiguration config();

   @Nullable
   class_7225.class_7874 registryLookup();

   public interface Builder {
      Builder withOwner(@Nullable class_1657 var1);

      Builder withRegistryLookup(@Nullable class_7225.class_7874 var1);

      @Nonnull
      PreviewContext build();
   }
}
