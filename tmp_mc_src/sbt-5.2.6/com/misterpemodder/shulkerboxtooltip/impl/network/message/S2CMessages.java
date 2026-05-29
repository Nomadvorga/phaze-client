package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.impl.network.ServerNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.S2CChannel;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class S2CMessages {
   public static final S2CChannel<S2CHandshakeResponse> HANDSHAKE_RESPONSE = ServerNetworking.<S2CHandshakeResponse>createS2CChannel(ShulkerBoxTooltipUtil.id("s2c_handshake"), new S2CHandshakeResponse.Type());
   public static final S2CChannel<S2CEnderChestUpdate> ENDER_CHEST_UPDATE = ServerNetworking.<S2CEnderChestUpdate>createS2CChannel(ShulkerBoxTooltipUtil.id("ec_update"), new S2CEnderChestUpdate.Type());

   private S2CMessages() {
   }

   public static void registerPayloadTypes() {
      HANDSHAKE_RESPONSE.registerPayloadType();
      ENDER_CHEST_UPDATE.registerPayloadType();
   }

   @Environment(EnvType.CLIENT)
   public static void registerAll() {
      HANDSHAKE_RESPONSE.register();
      ENDER_CHEST_UPDATE.register();
   }

   @Environment(EnvType.CLIENT)
   public static void unregisterAll() {
      HANDSHAKE_RESPONSE.unregister();
      ENDER_CHEST_UPDATE.unregister();
   }
}
