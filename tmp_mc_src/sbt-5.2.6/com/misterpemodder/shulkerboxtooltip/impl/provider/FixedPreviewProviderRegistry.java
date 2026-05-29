package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProviderRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.class_1263;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

public record FixedPreviewProviderRegistry<I extends class_1263>(PreviewProviderRegistry registry, BiFunction<Integer, Supplier<I>, PreviewProvider> providerFactory) {
   public FixedPreviewProviderRegistry<I> register(String id, int maxRowSize, BiFunction<class_2338, class_2680, I> inventoryFactory, class_2248 block) {
      PreviewProvider provider = (PreviewProvider)this.providerFactory.apply(maxRowSize, (Supplier)() -> (class_1263)inventoryFactory.apply(class_2338.field_10980, block.method_9564()));
      this.registry.register(ShulkerBoxTooltipUtil.id(id), provider, block.method_8389());
      return this;
   }
}
