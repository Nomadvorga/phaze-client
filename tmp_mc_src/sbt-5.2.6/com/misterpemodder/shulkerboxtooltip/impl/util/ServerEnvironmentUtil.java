package com.misterpemodder.shulkerboxtooltip.impl.util;

import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import org.jetbrains.annotations.NotNull;

public final class ServerEnvironmentUtil extends EnvironmentUtil {
   public @NotNull Configuration makeConfiguration() {
      return new Configuration();
   }

   public @NotNull Class<? extends Configuration> getConfigurationClass() {
      return Configuration.class;
   }
}
