package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.config.ConfigurationHandler;
import com.misterpemodder.shulkerboxtooltip.impl.network.ClientNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.network.ProtocolVersion;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import com.misterpemodder.shulkerboxtooltip.impl.util.NamedLogger;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.class_2540;

public record S2CHandshakeResponse(@Nullable ProtocolVersion serverVersion, Configuration config) {
   public static class Type implements MessageType<S2CHandshakeResponse> {
      public void encode(S2CHandshakeResponse message, class_2540 buf) {
         ((ProtocolVersion)Objects.requireNonNull(message.serverVersion)).writeToPacketBuf(buf);
         ConfigurationHandler.writeToPacketBuf(message.config, buf);
      }

      public S2CHandshakeResponse decode(class_2540 buf) {
         ProtocolVersion serverVersion = ProtocolVersion.readFromPacketBuf(buf);
         Configuration config = EnvironmentUtil.getInstance().makeConfiguration();
         ShulkerBoxTooltip.configTree.copy(ShulkerBoxTooltip.config, config);
         if (serverVersion != null && serverVersion.major() == ProtocolVersion.CURRENT.major()) {
            try {
               ConfigurationHandler.readFromPacketBuf(config, buf);
            } catch (RuntimeException e) {
               ShulkerBoxTooltip.LOGGER.error("failed to read server configuration", e);
            }
         }

         return new S2CHandshakeResponse(serverVersion, config);
      }

      public void onReceive(S2CHandshakeResponse message, MessageContext<S2CHandshakeResponse> context) {
         ShulkerBoxTooltip.LOGGER.info("Handshake succeeded");
         if (message.serverVersion != null) {
            if (message.serverVersion.major() == ProtocolVersion.CURRENT.major()) {
               ShulkerBoxTooltip.LOGGER.info("Server protocol version: " + String.valueOf(message.serverVersion));
               ClientNetworking.serverProtocolVersion = message.serverVersion;
               ShulkerBoxTooltip.config = message.config;
               S2CMessages.HANDSHAKE_RESPONSE.unregister();
               return;
            }

            NamedLogger var10000 = ShulkerBoxTooltip.LOGGER;
            int var10001 = ProtocolVersion.CURRENT.major();
            var10000.error("Incompatible server protocol version, expected " + var10001 + ", got " + message.serverVersion.major());
         } else {
            ShulkerBoxTooltip.LOGGER.error("Could not read server protocol version");
         }

         S2CMessages.unregisterAll();
      }
   }
}
