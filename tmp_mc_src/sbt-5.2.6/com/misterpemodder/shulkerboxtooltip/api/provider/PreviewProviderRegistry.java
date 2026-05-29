package com.misterpemodder.shulkerboxtooltip.api.provider;

import com.misterpemodder.shulkerboxtooltip.impl.provider.PreviewProviderRegistryImpl;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import org.jetbrains.annotations.ApiStatus.NonExtendable;

@NonExtendable
public interface PreviewProviderRegistry {
   @Nonnull
   static PreviewProviderRegistry getInstance() {
      return PreviewProviderRegistryImpl.INSTANCE;
   }

   void register(class_2960 var1, PreviewProvider var2, Iterable<class_1792> var3);

   void register(class_2960 var1, PreviewProvider var2, class_1792... var3);

   @Nullable
   PreviewProvider get(class_2960 var1);

   @Nullable
   PreviewProvider get(class_1799 var1);

   @Nullable
   PreviewProvider get(class_1792 var1);

   @Nullable
   class_2960 getId(PreviewProvider var1);

   @Nonnull
   Set<class_1792> getItems(PreviewProvider var1);

   @Nonnull
   Set<PreviewProvider> getProviders();

   @Nonnull
   Set<class_2960> getIds();
}
