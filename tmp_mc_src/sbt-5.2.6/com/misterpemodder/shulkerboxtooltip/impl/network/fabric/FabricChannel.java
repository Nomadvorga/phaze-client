package com.misterpemodder.shulkerboxtooltip.impl.network.fabric;

import com.misterpemodder.shulkerboxtooltip.impl.network.Payload;
import com.misterpemodder.shulkerboxtooltip.impl.network.channel.Channel;
import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

abstract class FabricChannel<T> implements Channel<T> {
   protected final class_8710.class_9154<Payload<T>> id;
   protected final MessageType<T> type;
   protected final class_9139<class_9129, Payload<T>> codec;
   private boolean payloadTypeRegistered = false;

   protected FabricChannel(class_2960 id, MessageType<T> type) {
      this.id = new class_8710.class_9154(id);
      this.type = type;
      this.codec = class_9139.method_56437(this::encodePayload, this::decodePayload);
   }

   public class_2960 getId() {
      return this.id.comp_2242();
   }

   public MessageType<T> getMessageType() {
      return this.type;
   }

   public void registerPayloadType() {
      if (!this.payloadTypeRegistered) {
         PayloadTypeRegistry.playC2S().register(this.id, this.codec);
         PayloadTypeRegistry.playS2C().register(this.id, this.codec);
         this.payloadTypeRegistered = true;
      }
   }

   public void onRegister(MessageContext<T> context) {
      this.type.onRegister(context);
   }

   public void onUnregister(MessageContext<T> context) {
      this.type.onUnregister(context);
   }

   private void encodePayload(class_9129 buf, Payload<T> message) {
      this.type.encode(message.value(), buf);
   }

   private Payload<T> decodePayload(class_9129 buf) {
      return new Payload<T>(this.id, this.type.decode(buf));
   }
}
