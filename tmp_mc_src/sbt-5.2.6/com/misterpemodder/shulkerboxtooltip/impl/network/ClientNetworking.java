package com.misterpemodder.shulkerboxtooltip.impl.network;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.PluginManager;
import com.misterpemodder.shulkerboxtooltip.impl.config.ConfigurationHandler;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.C2SChannel;
import com.misterpemodder.shulkerboxtooltip.impl.network.fabric.ClientNetworkingImpl;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.C2SMessages;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.injectables.annotations.ExpectPlatform.Transformed;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2960;
import net.minecraft.class_310;

public class ClientNetworking {
   @Nullable
   @Environment(EnvType.CLIENT)
   public static ProtocolVersion serverProtocolVersion;

   private ClientNetworking() {
   }

   @Environment(EnvType.CLIENT)
   public static void onJoinServer(class_310 client) {
      client.execute(() -> {
         PluginManager.loadColors();
         PluginManager.loadProviders();
      });
      ShulkerBoxTooltip.configTree.copy(ShulkerBoxTooltip.savedConfig, ShulkerBoxTooltip.config);
      serverProtocolVersion = null;
      if (!class_310.method_1551().method_1496()) {
         ConfigurationHandler.reinitClientSideSyncedValues(ShulkerBoxTooltip.config);
      }

      C2SMessages.attemptHandshake();
   }

   @ExpectPlatform
   @Environment(EnvType.CLIENT)
   @Transformed
   public static void init() {
      ClientNetworkingImpl.init();
   }

   @ExpectPlatform
   @Transformed
   public static <T> C2SChannel<T> createC2SChannel(class_2960 id, MessageType<T> type) {
      return ClientNetworkingImpl.<T>createC2SChannel(id, type);
   }
}
