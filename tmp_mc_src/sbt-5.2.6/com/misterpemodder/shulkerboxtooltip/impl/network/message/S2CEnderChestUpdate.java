package com.misterpemodder.shulkerboxtooltip.impl.network.message;

import com.misterpemodder.shulkerboxtooltip.impl.network.context.MessageContext;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.class_1730;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2520;
import net.minecraft.class_2540;
import net.minecraft.class_310;
import net.minecraft.class_7225;
import net.minecraft.class_746;

public record S2CEnderChestUpdate(@Nullable class_2499 nbtInventory) {
   public static S2CEnderChestUpdate create(class_1730 inventory, class_7225.class_7874 registries) {
      return new S2CEnderChestUpdate(inventory.method_7660(registries));
   }

   public static class Type implements MessageType<S2CEnderChestUpdate> {
      public void encode(S2CEnderChestUpdate message, class_2540 buf) {
         class_2487 compound = new class_2487();
         compound.method_10566("inv", (class_2520)Objects.requireNonNull(message.nbtInventory));
         buf.method_10794(compound);
      }

      public S2CEnderChestUpdate decode(class_2540 buf) {
         class_2487 compound = buf.method_10798();
         return compound != null && compound.method_10573("inv", 9) ? new S2CEnderChestUpdate(compound.method_10554("inv", 10)) : new S2CEnderChestUpdate((class_2499)null);
      }

      public void onReceive(S2CEnderChestUpdate message, MessageContext<S2CEnderChestUpdate> context) {
         if (message.nbtInventory != null) {
            class_310.method_1551().execute(() -> {
               if (class_310.method_1551().field_1724 != null) {
                  class_746 player = class_310.method_1551().field_1724;
                  player.method_7274().method_7659(message.nbtInventory, player.method_56673());
               }

            });
         }
      }
   }
}
