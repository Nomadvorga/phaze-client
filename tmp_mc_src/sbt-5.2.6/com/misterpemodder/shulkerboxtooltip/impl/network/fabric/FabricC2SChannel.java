package com.misterpemodder.shulkerboxtooltip.impl.network.fabric;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.network.Payload;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.C2SChannel;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.C2SMessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3222;
import net.minecraft.class_3244;

class FabricC2SChannel<T> extends FabricChannel<T> implements C2SChannel<T> {
   @Environment(EnvType.CLIENT)
   private boolean serverRegistered;

   public FabricC2SChannel(class_2960 id, MessageType<T> type) {
      super(id, type);
      if (EnvironmentUtil.isClient()) {
         this.serverRegistered = false;
      }

   }

   public void registerFor(class_3222 player) {
      class_3244 handler = player.field_13987;
      if (handler == null) {
         ShulkerBoxTooltip.LOGGER.error("Cannot register packet receiver for " + String.valueOf(this.getId()) + ", player is not in game");
      } else {
         ServerPlayNetworking.registerReceiver(handler, this.id, this::onReceive);
      }
   }

   public void unregisterFor(class_3222 player) {
      class_3244 handler = player.field_13987;
      if (handler != null) {
         ServerPlayNetworking.unregisterReceiver(handler, this.getId());
      }

   }

   @Environment(EnvType.CLIENT)
   public void sendToServer(T message) {
      ClientPlayNetworking.send(new Payload(this.id, message));
   }

   @Environment(EnvType.CLIENT)
   public boolean canSendToServer() {
      return this.serverRegistered && class_310.method_1551().method_1562() != null;
   }

   public void onRegister(MessageContext<T> context) {
      if (context.getReceivingSide() == MessageContext.Side.CLIENT) {
         this.serverRegistered = true;
      }

      super.onRegister(context);
   }

   public void onUnregister(MessageContext<T> context) {
      if (context.getReceivingSide() == MessageContext.Side.CLIENT) {
         this.serverRegistered = false;
      }

      super.onUnregister(context);
   }

   @Environment(EnvType.CLIENT)
   public void onDisconnect() {
      this.serverRegistered = false;
   }

   private void onReceive(Payload<T> payload, ServerPlayNetworking.Context context) {
      this.type.onReceive(payload.value(), new C2SMessageContext(context.player(), this));
   }
}
