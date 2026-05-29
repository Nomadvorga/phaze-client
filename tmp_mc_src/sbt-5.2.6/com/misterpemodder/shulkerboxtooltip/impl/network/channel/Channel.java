package com.misterpemodder.shulkerboxtooltip.impl.network.channel;

import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.MessageType;
import net.minecraft.class_2960;

public interface Channel<T> {
   class_2960 getId();

   MessageType<T> getMessageType();

   void registerPayloadType();

   void onRegister(MessageContext<T> var1);

   void onUnregister(MessageContext<T> var1);
}
