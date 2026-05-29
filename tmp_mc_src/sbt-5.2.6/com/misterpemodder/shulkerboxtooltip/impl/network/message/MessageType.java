package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import net.minecraft.class_2540;

public interface MessageType<T> {
   void encode(T var1, class_2540 var2);

   T decode(class_2540 var1);

   void onReceive(T var1, MessageContext<T> var2);

   default void onRegister(MessageContext<T> context) {
   }

   default void onUnregister(MessageContext<T> context) {
   }
}
