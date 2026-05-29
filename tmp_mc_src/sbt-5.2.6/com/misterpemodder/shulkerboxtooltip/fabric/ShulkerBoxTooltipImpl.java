package com.misterpemodder.shulkerboxtooltip.fabric;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ShulkerBoxTooltipImpl extends ShulkerBoxTooltip implements ModInitializer {
   public void onInitialize() {
      ShulkerBoxTooltip.init();
   }

   public static boolean isClient() {
      return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
   }

   public static Path getConfigDir() {
      return FabricLoader.getInstance().getConfigDir();
   }
}
