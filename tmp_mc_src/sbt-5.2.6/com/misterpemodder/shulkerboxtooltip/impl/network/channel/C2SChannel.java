package com.misterpemodder.shulkerboxtooltip.impl.network.channel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_3222;

public interface C2SChannel<T> extends Channel<T> {
   void registerFor(class_3222 var1);

   void unregisterFor(class_3222 var1);

   @Environment(EnvType.CLIENT)
   void sendToServer(T var1);

   @Environment(EnvType.CLIENT)
   boolean canSendToServer();

   @Environment(EnvType.CLIENT)
   void onDisconnect();
}
