package com.misterpemodder.shulkerboxtooltip.impl.network.fabric;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.network.ServerNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.S2CChannel;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.C2SMessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.C2SMessages;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.S2CMessages;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.class_2960;
import net.minecraft.class_3222;

public final class ServerNetworkingImpl {
   private static final Map<class_2960, FabricS2CChannel<?>> S2C_CHANNELS = new HashMap();

   private ServerNetworkingImpl() {
   }

   public static void init() {
      if (ShulkerBoxTooltip.config.server.clientIntegration) {
         S2CMessages.registerPayloadTypes();
         C2SMessages.registerPayloadTypes();
         ServerPlayConnectionEvents.INIT.register((ServerPlayConnectionEvents.Init)(handler, server) -> C2SMessages.registerAllFor(handler.field_14140));
         ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayConnectionEvents.Disconnect)(handler, server) -> ServerNetworking.removeClient(handler.field_14140));
         S2CPlayChannelEvents.REGISTER.register((S2CPlayChannelEvents.Register)(handler, sender, server, ids) -> ids.forEach((id) -> onRegisterChannel(id, handler.method_32311())));
         S2CPlayChannelEvents.UNREGISTER.register((S2CPlayChannelEvents.Unregister)(handler, sender, server, ids) -> ids.forEach((id) -> onUnregisterChannel(id, handler.method_32311())));
         ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((ServerEntityWorldChangeEvents.AfterPlayerChange)(player, origin, destination) -> ServerNetworking.onPlayerChangeWorld(player));
      }
   }

   public static <T> S2CChannel<T> createS2CChannel(class_2960 id, MessageType<T> type) {
      FabricS2CChannel<T> channel = new FabricS2CChannel<T>(id, type);
      S2C_CHANNELS.put(id, channel);
      return channel;
   }

   private static <T> void onRegisterChannel(class_2960 id, class_3222 player) {
      FabricS2CChannel<T> channel = (FabricS2CChannel)S2C_CHANNELS.get(id);
      if (channel != null) {
         channel.onRegister(new C2SMessageContext(player, channel));
      }

   }

   private static <T> void onUnregisterChannel(class_2960 id, class_3222 player) {
      FabricS2CChannel<T> channel = (FabricS2CChannel)S2C_CHANNELS.get(id);
      if (channel != null) {
         channel.onUnregister(new C2SMessageContext(player, channel));
      }

   }
}
