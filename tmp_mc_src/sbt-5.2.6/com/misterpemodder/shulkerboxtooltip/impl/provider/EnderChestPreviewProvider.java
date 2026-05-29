package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.provider.BlockEntityPreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.C2SEnderChestUpdateRequest;
import com.misterpemodder.shulkerboxtooltip.impl.network.message.C2SMessages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1657;
import net.minecraft.class_1730;
import net.minecraft.class_1799;
import net.minecraft.class_2371;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public class EnderChestPreviewProvider implements PreviewProvider {
   public List<class_1799> getInventory(PreviewContext context) {
      class_1657 owner = context.owner();
      if (owner == null) {
         return Collections.emptyList();
      } else {
         class_1730 inventory = owner.method_7274();
         int size = inventory.method_5439();
         List<class_1799> items = class_2371.method_10213(size, class_1799.field_8037);

         for(int i = 0; i < size; ++i) {
            items.set(i, inventory.method_5438(i).method_7972());
         }

         return items;
      }
   }

   public int getInventoryMaxSize(PreviewContext context) {
      class_1657 owner = context.owner();
      return owner == null ? 0 : owner.method_7274().method_5439();
   }

   public boolean shouldDisplay(PreviewContext context) {
      class_1657 owner = context.owner();
      if (owner == null) {
         return false;
      } else {
         return ShulkerBoxTooltip.config.preview.serverIntegration && ShulkerBoxTooltip.config.server.clientIntegration && ShulkerBoxTooltip.config.server.enderChestSyncType != Configuration.EnderChestSyncType.NONE && !owner.method_7274().method_5442();
      }
   }

   @Environment(EnvType.CLIENT)
   public ColorKey getWindowColorKey(PreviewContext context) {
      return ColorKey.ENDER_CHEST;
   }

   public void onInventoryAccessStart(PreviewContext context) {
      if (ShulkerBoxTooltip.config.server.enderChestSyncType == Configuration.EnderChestSyncType.PASSIVE && class_310.method_1551().method_1562() != null) {
         C2SMessages.ENDER_CHEST_UPDATE_REQUEST.sendToServer(new C2SEnderChestUpdateRequest());
      }

   }

   public boolean showTooltipHints(PreviewContext context) {
      return ShulkerBoxTooltip.config.preview.serverIntegration && ShulkerBoxTooltip.config.server.clientIntegration && ShulkerBoxTooltip.config.server.enderChestSyncType != Configuration.EnderChestSyncType.NONE;
   }

   public List<class_2561> addTooltip(PreviewContext context) {
      return ShulkerBoxTooltipApi.getCurrentPreviewType(this.isFullPreviewAvailable(context)) == PreviewType.FULL ? Collections.emptyList() : BlockEntityPreviewProvider.getItemCountTooltip(new ArrayList(), this.getInventory(context));
   }
}
