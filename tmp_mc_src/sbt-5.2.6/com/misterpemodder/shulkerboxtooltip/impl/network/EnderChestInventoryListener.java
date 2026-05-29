package com.misterpemodder.shulkerboxtooltip.impl.network;

import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.S2CEnderChestUpdate;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.S2CMessages;
import java.util.List;
import net.minecraft.class_1263;
import net.minecraft.class_1265;
import net.minecraft.class_1730;
import net.minecraft.class_3222;

public final class EnderChestInventoryListener implements class_1265 {
   private final class_3222 player;

   private EnderChestInventoryListener(class_3222 player) {
      this.player = player;
   }

   public void method_5453(class_1263 inv) {
      if (!ShulkerBoxTooltipApi.hasModAvailable(this.player)) {
         detachFrom(this.player);
      } else {
         S2CMessages.ENDER_CHEST_UPDATE.sendTo(this.player, S2CEnderChestUpdate.create((class_1730)inv, this.player.method_56673()));
      }
   }

   public static void attachTo(class_3222 player) {
      class_1730 inventory = player == null ? null : player.method_7274();
      List<class_1265> listeners = inventory == null ? null : inventory.field_5829;
      if (listeners != null) {
         for(class_1265 listener : listeners) {
            if (listener instanceof EnderChestInventoryListener) {
               return;
            }
         }
      }

      if (inventory != null) {
         inventory.method_5489(new EnderChestInventoryListener(player));
      }

   }

   public static void detachFrom(class_3222 player) {
      class_1730 inventory = player == null ? null : player.method_7274();
      List<class_1265> listeners = inventory == null ? null : inventory.field_5829;
      if (listeners != null) {
         for(class_1265 listener : listeners) {
            if (listener instanceof EnderChestInventoryListener) {
               inventory.method_5488(listener);
               return;
            }
         }

      }
   }
}
