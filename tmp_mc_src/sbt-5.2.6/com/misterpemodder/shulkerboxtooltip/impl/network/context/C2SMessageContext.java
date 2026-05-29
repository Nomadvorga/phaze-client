package com.misterpemodder.shulkerboxtooltip.impl.network.context;

import com.misterpemodder.shulkerboxtooltip.impl.network.channel.Channel;
import net.minecraft.class_3222;

public record C2SMessageContext<T>(class_3222 player, Channel<T> channel) implements MessageContext<T> {
   public void execute(Runnable task) {
      this.player.field_13995.execute(task);
   }

   public class_3222 getPlayer() {
      return this.player;
   }

   public Channel<T> getChannel() {
      return this.channel;
   }

   public MessageContext.Side getReceivingSide() {
      return MessageContext.Side.SERVER;
   }
}
