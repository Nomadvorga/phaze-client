package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProviderRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.util.NamedLogger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public class PreviewProviderRegistryImpl implements PreviewProviderRegistry {
   private boolean locked = true;
   private final BiMap<class_2960, PreviewProvider> providerIds = HashBiMap.create();
   private final Map<class_1792, PreviewProvider> providerItems = new HashMap();
   public static final PreviewProviderRegistryImpl INSTANCE = new PreviewProviderRegistryImpl();

   private PreviewProviderRegistryImpl() {
   }

   public void setLocked(boolean locked) {
      this.locked = locked;
   }

   public void register(class_2960 id, PreviewProvider provider, Iterable<class_1792> items) {
      if (this.locked) {
         throw new IllegalStateException("attempted to register PreviewProvider outside ShulkerBoxTooltipApi.registerProviders");
      } else if (this.providerIds.containsValue(provider)) {
         throw new IllegalStateException("attempted to register PreviewProvider twice");
      } else {
         if (this.providerIds.containsKey(id)) {
            ShulkerBoxTooltip.LOGGER.warn("registering PreviewProvider with an existing id: " + String.valueOf(id));
         }

         int priority = provider.getPriority();
         this.providerIds.put(id, provider);

         for(class_1792 item : items) {
            PreviewProvider previousProvider = (PreviewProvider)this.providerItems.get(item);
            if (previousProvider == null) {
               this.providerItems.put(item, provider);
            } else {
               class_2960 previousId = this.getId(previousProvider);
               class_2960 itemId = class_7923.field_41178.method_10221(item);
               if (priority > previousProvider.getPriority()) {
                  NamedLogger var10000 = ShulkerBoxTooltip.LOGGER;
                  String var10001 = String.valueOf(previousId);
                  var10000.info("overriding preview provider " + var10001 + " with " + String.valueOf(id) + " for item " + String.valueOf(itemId));
                  this.providerItems.put(item, provider);
               } else {
                  NamedLogger var10 = ShulkerBoxTooltip.LOGGER;
                  String var11 = String.valueOf(id);
                  var10.info("overriding preview provider " + var11 + " with " + String.valueOf(previousId) + " for item " + String.valueOf(itemId));
               }
            }
         }

      }
   }

   public void register(class_2960 id, PreviewProvider provider, class_1792... items) {
      this.register(id, provider, Arrays.asList(items));
   }

   public PreviewProvider get(class_2960 id) {
      return (PreviewProvider)this.providerIds.get(id);
   }

   public PreviewProvider get(class_1799 stack) {
      return (PreviewProvider)this.providerItems.get(stack.method_7909());
   }

   public PreviewProvider get(class_1792 item) {
      return (PreviewProvider)this.providerItems.get(item);
   }

   public class_2960 getId(PreviewProvider provider) {
      return (class_2960)this.providerIds.inverse().get(provider);
   }

   @Nonnull
   public Set<class_1792> getItems(PreviewProvider provider) {
      ImmutableSet.Builder<class_1792> builder = ImmutableSet.builder();

      for(Map.Entry<class_1792, PreviewProvider> entry : this.providerItems.entrySet()) {
         if (entry.getValue() == provider) {
            builder.add((class_1792)entry.getKey());
         }
      }

      return builder.build();
   }

   @Nonnull
   public Set<PreviewProvider> getProviders() {
      return this.providerIds.values();
   }

   @Nonnull
   public Set<class_2960> getIds() {
      return this.providerIds.keySet();
   }
}
