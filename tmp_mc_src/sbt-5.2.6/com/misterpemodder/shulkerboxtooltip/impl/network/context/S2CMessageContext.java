package com.misterpemodder.shulkerboxtooltip.impl.network.context;

import com.misterpemodder.shulkerboxtooltip.impl.network.channel.Channel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_746;

@Environment(EnvType.CLIENT)
public record S2CMessageContext<T>(Channel<T> channel) implements MessageContext<T> {
   public void execute(Runnable task) {
      class_310.method_1551().execute(task);
   }

   public class_746 getPlayer() {
      return class_310.method_1551().field_1724;
   }

   public Channel<T> getChannel() {
      return this.channel;
   }

   public MessageContext.Side getReceivingSide() {
      return MessageContext.Side.CLIENT;
   }
}
