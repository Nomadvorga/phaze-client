package com.misterpemodder.shulkerboxtooltip.impl.network.channel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_3222;

public interface S2CChannel<T> extends Channel<T> {
   @Environment(EnvType.CLIENT)
   void register();

   @Environment(EnvType.CLIENT)
   void unregister();

   void sendTo(class_3222 var1, T var2);
}
