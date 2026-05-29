package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.network.ClientNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.network.ProtocolVersion;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.C2SChannel;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_3222;

public final class C2SMessages {
   public static final C2SChannel<C2SHandshakeStart> HANDSHAKE_START = ClientNetworking.<C2SHandshakeStart>createC2SChannel(ShulkerBoxTooltipUtil.id("c2s_handshake"), new C2SHandshakeStart.Type());
   public static final C2SChannel<C2SEnderChestUpdateRequest> ENDER_CHEST_UPDATE_REQUEST = ClientNetworking.<C2SEnderChestUpdateRequest>createC2SChannel(ShulkerBoxTooltipUtil.id("ec_update_req"), new C2SEnderChestUpdateRequest.Type());

   private C2SMessages() {
   }

   public static void registerPayloadTypes() {
      HANDSHAKE_START.registerPayloadType();
      ENDER_CHEST_UPDATE_REQUEST.registerPayloadType();
   }

   @Environment(EnvType.CLIENT)
   public static void onDisconnectFromServer() {
      HANDSHAKE_START.onDisconnect();
      ENDER_CHEST_UPDATE_REQUEST.onDisconnect();
   }

   public static void registerAllFor(class_3222 player) {
      HANDSHAKE_START.registerFor(player);
      ENDER_CHEST_UPDATE_REQUEST.registerFor(player);
   }

   public static void attemptHandshake() {
      if (ShulkerBoxTooltip.config.preview.serverIntegration && ClientNetworking.serverProtocolVersion == null && HANDSHAKE_START.canSendToServer()) {
         ShulkerBoxTooltip.LOGGER.info("Server integration enabled, attempting handshake...");
         HANDSHAKE_START.sendToServer(new C2SHandshakeStart(ProtocolVersion.CURRENT));
      }

   }
}
