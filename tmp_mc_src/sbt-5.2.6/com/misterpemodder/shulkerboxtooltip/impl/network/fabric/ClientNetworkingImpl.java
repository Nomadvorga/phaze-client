package com.misterpemodder.shulkerboxtooltip.impl.network.fabric;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.network.ClientNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.C2SChannel;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.S2CMessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.C2SMessages;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.S2CMessages;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.class_2960;

public final class ClientNetworkingImpl {
   private static final Map<class_2960, FabricC2SChannel<?>> C2S_CHANNELS = new HashMap();

   private ClientNetworkingImpl() {
   }

   @Environment(EnvType.CLIENT)
   public static void init() {
      if (ShulkerBoxTooltip.config.preview.serverIntegration) {
         S2CMessages.registerPayloadTypes();
         C2SMessages.registerPayloadTypes();
         ClientPlayConnectionEvents.INIT.register((ClientPlayConnectionEvents.Init)(handler, client) -> S2CMessages.registerAll());
         ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, client) -> C2SMessages.onDisconnectFromServer());
         C2SPlayChannelEvents.REGISTER.register((C2SPlayChannelEvents.Register)(handler, sender, server, ids) -> ids.forEach(ClientNetworkingImpl::onRegisterChannel));
         C2SPlayChannelEvents.UNREGISTER.register((C2SPlayChannelEvents.Unregister)(handler, sender, server, ids) -> ids.forEach(ClientNetworkingImpl::onUnregisterChannel));
      }

      ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join)(handler, sender, client) -> ClientNetworking.onJoinServer(client));
   }

   public static <T> C2SChannel<T> createC2SChannel(class_2960 id, MessageType<T> type) {
      FabricC2SChannel<T> channel = new FabricC2SChannel<T>(id, type);
      C2S_CHANNELS.put(id, channel);
      return channel;
   }

   @Environment(EnvType.CLIENT)
   private static <T> void onRegisterChannel(class_2960 id) {
      FabricC2SChannel<T> channel = (FabricC2SChannel)C2S_CHANNELS.get(id);
      if (channel != null) {
         channel.onRegister(new S2CMessageContext(channel));
      }

   }

   @Environment(EnvType.CLIENT)
   private static <T> void onUnregisterChannel(class_2960 id) {
      FabricC2SChannel<T> channel = (FabricC2SChannel)C2S_CHANNELS.get(id);
      if (channel != null) {
         channel.onUnregister(new S2CMessageContext(channel));
      }

   }
}
