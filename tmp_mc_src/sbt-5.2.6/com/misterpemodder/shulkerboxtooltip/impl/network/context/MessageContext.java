package com.misterpemodder.shulkerboxtooltip.impl.network.context;

import com.misterpemodder.shulkerboxtooltip.impl.network.channel.Channel;
import net.minecraft.class_1657;

public sealed interface MessageContext<T> permits C2SMessageContext, S2CMessageContext {
   void execute(Runnable var1);

   class_1657 getPlayer();

   Channel<T> getChannel();

   Side getReceivingSide();

   public static enum Side {
      CLIENT,
      SERVER;

      // $FF: synthetic method
      private static Side[] $values() {
         return new Side[]{CLIENT, SERVER};
      }
   }
}
