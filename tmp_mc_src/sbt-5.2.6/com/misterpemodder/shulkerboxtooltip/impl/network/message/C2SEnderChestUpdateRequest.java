package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import net.minecraft.class_2540;
import net.minecraft.class_3222;

public record C2SEnderChestUpdateRequest() {
   public static class Type implements MessageType<C2SEnderChestUpdateRequest> {
      public void encode(C2SEnderChestUpdateRequest message, class_2540 buf) {
      }

      public C2SEnderChestUpdateRequest decode(class_2540 buf) {
         return new C2SEnderChestUpdateRequest();
      }

      public void onReceive(C2SEnderChestUpdateRequest message, MessageContext<C2SEnderChestUpdateRequest> context) {
         class_3222 player = (class_3222)context.getPlayer();
         S2CMessages.ENDER_CHEST_UPDATE.sendTo(player, S2CEnderChestUpdate.create(player.method_7274(), player.method_56673()));
      }
   }
}
