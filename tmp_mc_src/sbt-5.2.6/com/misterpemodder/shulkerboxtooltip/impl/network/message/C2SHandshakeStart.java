package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.network.ProtocolVersion;
import com.misterpemodder.shulkerboxtooltip.impl.network.ServerNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.C2SChannel;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.util.NamedLogger;
import net.minecraft.class_2540;
import net.minecraft.class_3222;

public record C2SHandshakeStart(ProtocolVersion clientVersion) {
   public static class Type implements MessageType<C2SHandshakeStart> {
      public void encode(C2SHandshakeStart message, class_2540 buf) {
         message.clientVersion.writeToPacketBuf(buf);
      }

      public C2SHandshakeStart decode(class_2540 buf) {
         return new C2SHandshakeStart(ProtocolVersion.readFromPacketBuf(buf));
      }

      public void onReceive(C2SHandshakeStart message, MessageContext<C2SHandshakeStart> context) {
         class_3222 player = (class_3222)context.getPlayer();
         C2SChannel<C2SHandshakeStart> channel = (C2SChannel)context.getChannel();
         if (message.clientVersion == null) {
            ShulkerBoxTooltip.LOGGER.error(player.method_7334().getName() + ": received invalid handshake packet");
            channel.unregisterFor(player);
         } else {
            NamedLogger var10000 = ShulkerBoxTooltip.LOGGER;
            String var10001 = player.method_7334().getName();
            var10000.info(var10001 + ": protocol version: " + String.valueOf(message.clientVersion));
            if (message.clientVersion.major() != ProtocolVersion.CURRENT.major()) {
               var10000 = ShulkerBoxTooltip.LOGGER;
               var10001 = player.method_7334().getName();
               var10000.error(var10001 + ": incompatible client protocol version, expected " + ProtocolVersion.CURRENT.major() + ", got " + message.clientVersion.major());
               channel.unregisterFor(player);
            } else {
               context.execute(() -> {
                  S2CMessages.HANDSHAKE_RESPONSE.sendTo(player, new S2CHandshakeResponse(ProtocolVersion.CURRENT, ShulkerBoxTooltip.config));
                  ServerNetworking.addClient(player, message.clientVersion);
               });
            }
         }
      }

      public void onRegister(MessageContext<C2SHandshakeStart> context) {
         if (context.getReceivingSide() == MessageContext.Side.CLIENT) {
            C2SMessages.attemptHandshake();
         }

      }
   }
}
