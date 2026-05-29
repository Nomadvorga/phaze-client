package com.misterpemodder.shulkerboxtooltip.impl.network;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.PluginManager;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.S2CChannel;
import com.misterpemodder.shulkerboxtooltip.impl.network.fabric.ServerNetworkingImpl;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.S2CEnderChestUpdate;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.S2CMessages;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.injectables.annotations.ExpectPlatform.Transformed;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.class_2960;
import net.minecraft.class_3222;

public class ServerNetworking {
   private static final Map<class_3222, ProtocolVersion> CLIENTS = new WeakHashMap();

   private ServerNetworking() {
   }

   public static boolean hasModAvailable(class_3222 player) {
      return CLIENTS.containsKey(player);
   }

   public static void addClient(class_3222 client, ProtocolVersion version) {
      CLIENTS.put(client, version);
      PluginManager.loadProviders();
      Configuration.EnderChestSyncType ecSyncType = ShulkerBoxTooltip.config.server.enderChestSyncType;
      if (ecSyncType != Configuration.EnderChestSyncType.NONE) {
         S2CMessages.ENDER_CHEST_UPDATE.sendTo(client, S2CEnderChestUpdate.create(client.method_7274(), client.method_56673()));
      }

      if (ecSyncType == Configuration.EnderChestSyncType.ACTIVE) {
         EnderChestInventoryListener.attachTo(client);
      }

   }

   public static void removeClient(class_3222 client) {
      CLIENTS.remove(client);
      EnderChestInventoryListener.detachFrom(client);
   }

   public static void onPlayerChangeWorld(class_3222 player) {
      Configuration.EnderChestSyncType ecSyncType = ShulkerBoxTooltip.config.server.enderChestSyncType;
      if (CLIENTS.containsKey(player) && ecSyncType != Configuration.EnderChestSyncType.NONE) {
         S2CMessages.ENDER_CHEST_UPDATE.sendTo(player, S2CEnderChestUpdate.create(player.method_7274(), player.method_56673()));
      }

   }

   @ExpectPlatform
   @Transformed
   public static void init() {
      ServerNetworkingImpl.init();
   }

   @ExpectPlatform
   @Transformed
   public static <T> S2CChannel<T> createS2CChannel(class_2960 id, MessageType<T> type) {
      return ServerNetworkingImpl.<T>createS2CChannel(id, type);
   }
}
