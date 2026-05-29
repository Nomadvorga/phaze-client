package com.misterpemodder.shulkerboxtooltip.impl;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.impl.color.ColorRegistryImpl;
import com.misterpemodder.shulkerboxtooltip.impl.fabric.PluginManagerImpl;
import com.misterpemodder.shulkerboxtooltip.impl.provider.PreviewProviderRegistryImpl;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import com.misterpemodder.shulkerboxtooltip.impl.util.NamedLogger;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.injectables.annotations.ExpectPlatform.Transformed;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.logging.log4j.LogManager;

public final class PluginManager {
   private static final NamedLogger LOGGER = new NamedLogger(LogManager.getFormatterLogger("ShulkerBoxTooltip Plugins"));
   private static Map<String, ShulkerBoxTooltipApi> plugins = null;
   private static final AtomicBoolean colorsLoaded = new AtomicBoolean(false);
   private static final AtomicBoolean providersLoaded = new AtomicBoolean(false);

   private static void gatherPlugins() {
      if (plugins == null) {
         List<PluginContainer> pluginList = getPluginContainers();
         String var10000;
         switch (pluginList.size()) {
            case 0 -> var10000 = "Loading %d plugins";
            case 1 -> var10000 = "Loading %d plugin: %s";
            default -> var10000 = "Loading %d plugins: %s";
         }

         String pluginText = var10000;
         LOGGER.info(pluginText, pluginList.size(), pluginList.stream().map(PluginContainer::modId).collect(Collectors.joining(", ")));
         plugins = new HashMap();

         for(PluginContainer plugin : pluginList) {
            try {
               plugins.put(plugin.modId(), (ShulkerBoxTooltipApi)plugin.apiImplSupplier().get());
            } catch (Exception e) {
               LOGGER.error("Failed to instantiate plugin of mod " + plugin.modId(), e);
            }
         }

      }
   }

   @Environment(EnvType.CLIENT)
   public static synchronized void loadColors() {
      if (!colorsLoaded.get()) {
         gatherPlugins();
         ColorRegistryImpl colorRegistry = ColorRegistryImpl.INSTANCE;

         for(Map.Entry<String, ShulkerBoxTooltipApi> plugin : plugins.entrySet()) {
            String name = (String)plugin.getKey();
            ShulkerBoxTooltipApi instance = (ShulkerBoxTooltipApi)plugin.getValue();
            colorRegistry.resetRegisteredKeysCount();
            colorRegistry.setLocked(false);

            try {
               instance.registerColors(colorRegistry);
            } catch (Exception e) {
               LOGGER.error("Failed to register colors for mod " + name, e);
               continue;
            }

            colorRegistry.setLocked(true);
            int registered = colorRegistry.registeredKeysCount();
            if (registered != 0) {
               String countText = registered == 1 ? "Registered %d color key for mod %s" : "Registered %d color keys for mod %s";
               LOGGER.info(countText, registered, name);
            }
         }

         colorsLoaded.set(true);
         ShulkerBoxTooltip.configTree.reload(EnvironmentUtil.getInstance().makeConfiguration());
      }
   }

   @Environment(EnvType.CLIENT)
   public static boolean areColorsLoaded() {
      return colorsLoaded.get();
   }

   public static synchronized void loadProviders() {
      if (!providersLoaded.get()) {
         gatherPlugins();
         PreviewProviderRegistryImpl providerRegistry = PreviewProviderRegistryImpl.INSTANCE;

         for(Map.Entry<String, ShulkerBoxTooltipApi> plugin : plugins.entrySet()) {
            String name = (String)plugin.getKey();
            ShulkerBoxTooltipApi instance = (ShulkerBoxTooltipApi)plugin.getValue();
            int prevSize = providerRegistry.getIds().size();
            providerRegistry.setLocked(false);

            try {
               instance.registerProviders(providerRegistry);
            } catch (Exception e) {
               LOGGER.error("Failed to register providers for mod " + name, e);
               continue;
            }

            providerRegistry.setLocked(true);
            int registered = providerRegistry.getIds().size() - prevSize;
            String providerText = registered == 1 ? "Registered %d provider for mod %s" : "Registered %d providers for mod %s";
            LOGGER.info(providerText, registered, name);
         }

         providersLoaded.set(true);
      }
   }

   @ExpectPlatform
   @Transformed
   public static List<PluginContainer> getPluginContainers() {
      return PluginManagerImpl.getPluginContainers();
   }

   public static record PluginContainer(String modId, Supplier<ShulkerBoxTooltipApi> apiImplSupplier) {
   }
}
