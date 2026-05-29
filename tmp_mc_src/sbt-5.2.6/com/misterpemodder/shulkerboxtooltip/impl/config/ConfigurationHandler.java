package com.misterpemodder.shulkerboxtooltip.impl.config;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2487;
import net.minecraft.class_2540;
import net.minecraft.class_2561;

public final class ConfigurationHandler {
   private static ShulkerBoxTooltipConfigSerializer serializer;

   private ConfigurationHandler() {
   }

   public static Configuration register() {
      serializer = new ShulkerBoxTooltipConfigSerializer();
      Configuration config = loadFromFile();
      saveToFile(config);
      return config;
   }

   public static Configuration loadFromFile() {
      try {
         Configuration config = serializer.deserialize();
         class_2561 errorMsg = ShulkerBoxTooltip.configTree.validate(config);
         if (errorMsg != null) {
            ShulkerBoxTooltip.LOGGER.error("Failed to load configuration, using default values: " + String.valueOf(errorMsg));
            return EnvironmentUtil.getInstance().makeConfiguration();
         } else {
            return config;
         }
      } catch (SerializationException e) {
         ShulkerBoxTooltip.LOGGER.error("Failed to load configuration, using default values", e);
         return EnvironmentUtil.getInstance().makeConfiguration();
      }
   }

   public static void saveToFile(Configuration toSave) {
      if (ShulkerBoxTooltip.savedConfig != null) {
         ShulkerBoxTooltip.configTree.copy(toSave, ShulkerBoxTooltip.savedConfig);
      }

      if (ShulkerBoxTooltip.config != null) {
         class_2487 serverConfigNbt = new class_2487();
         ShulkerBoxTooltip.configTree.writeToNbt(ShulkerBoxTooltip.config, serverConfigNbt);
         ShulkerBoxTooltip.configTree.copy(toSave, ShulkerBoxTooltip.config);
         ShulkerBoxTooltip.configTree.readFromNbt(ShulkerBoxTooltip.config, serverConfigNbt);
      }

      try {
         serializer.serialize(toSave);
      } catch (SerializationException e) {
         ShulkerBoxTooltip.LOGGER.error("Failed to save configuration", e);
      }

   }

   @Environment(EnvType.CLIENT)
   public static void reinitClientSideSyncedValues(Configuration config) {
      config.server.clientIntegration = false;
      config.server.enderChestSyncType = Configuration.EnderChestSyncType.NONE;
   }

   public static void readFromPacketBuf(Configuration config, class_2540 buf) {
      class_2487 compound = buf.method_10798();
      if (compound != null) {
         ShulkerBoxTooltip.configTree.readFromNbt(config, compound);
      }

   }

   public static void writeToPacketBuf(Configuration config, class_2540 buf) {
      class_2487 compound = new class_2487();
      ShulkerBoxTooltip.configTree.writeToNbt(config, compound);
      buf.method_10794(compound);
   }
}
