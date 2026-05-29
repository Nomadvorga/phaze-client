package com.misterpemodder.shulkerboxtooltip.impl.util;

import com.misterpemodder.shulkerboxtooltip.impl.config.ClientConfiguration;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public final class ClientEnvironmentUtil extends EnvironmentUtil {
   public @NotNull Configuration makeConfiguration() {
      return new ClientConfiguration();
   }

   public @NotNull Class<? extends Configuration> getConfigurationClass() {
      return ClientConfiguration.class;
   }
}
