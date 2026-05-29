package com.misterpemodder.shulkerboxtooltip.impl.util;

import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.util.fabric.EnvironmentUtilImpl;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.injectables.annotations.ExpectPlatform.Transformed;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public abstract sealed class EnvironmentUtil permits ClientEnvironmentUtil, ServerEnvironmentUtil {
   private static EnvironmentUtil instance;
   private static final String PACKAGE_NAME = EnvironmentUtil.class.getPackageName();
   private static final String CLIENT_ENVIRONMENT_UTIL;
   private static final String SERVER_ENVIRONMENT_UTIL;

   public static EnvironmentUtil getInstance() {
      if (instance == null) {
         String className = isClient() ? CLIENT_ENVIRONMENT_UTIL : SERVER_ENVIRONMENT_UTIL;

         try {
            Class<?> clazz = Class.forName(className);
            instance = (EnvironmentUtil)clazz.getDeclaredConstructor().newInstance();
         } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
         }
      }

      return instance;
   }

   public abstract @NotNull Configuration makeConfiguration();

   public abstract @NotNull Class<? extends Configuration> getConfigurationClass();

   @ExpectPlatform
   @Contract(
      value = "-> _",
      pure = true
   )
   @Transformed
   public static boolean isClient() {
      return EnvironmentUtilImpl.isClient();
   }

   static {
      CLIENT_ENVIRONMENT_UTIL = PACKAGE_NAME + ".ClientEnvironmentUtil";
      SERVER_ENVIRONMENT_UTIL = PACKAGE_NAME + ".ServerEnvironmentUtil";
   }
}
