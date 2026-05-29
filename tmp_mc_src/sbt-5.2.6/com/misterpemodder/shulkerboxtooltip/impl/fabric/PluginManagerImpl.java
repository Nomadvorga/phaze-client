package com.misterpemodder.shulkerboxtooltip.impl.fabric;

import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.impl.PluginManager;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;

public final class PluginManagerImpl {
   public static List<PluginManager.PluginContainer> getPluginContainers() {
      return (List)FabricLoader.getInstance().getEntrypointContainers("shulkerboxtooltip", ShulkerBoxTooltipApi.class).stream().map((entrypointContainer) -> {
         String var10002 = entrypointContainer.getProvider().getMetadata().getId();
         Objects.requireNonNull(entrypointContainer);
         return new PluginManager.PluginContainer(var10002, entrypointContainer::getEntrypoint);
      }).collect(Collectors.toList());
   }
}
