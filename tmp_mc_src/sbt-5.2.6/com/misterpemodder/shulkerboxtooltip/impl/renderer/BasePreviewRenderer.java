package com.misterpemodder.shulkerboxtooltip.impl.renderer;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.config.PreviewConfiguration;
import com.misterpemodder.shulkerboxtooltip.api.provider.EmptyPreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.renderer.PreviewRenderer;
import com.misterpemodder.shulkerboxtooltip.impl.util.MergedItemStack;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_638;

@Environment(EnvType.CLIENT)
public abstract class BasePreviewRenderer implements PreviewRenderer {
   protected PreviewType previewType;
   protected PreviewConfiguration config;
   protected int compactMaxRowSize;
   protected int maxRowSize;
   protected class_2960 textureOverride;
   protected PreviewProvider provider;
   protected List<class_1799> fullItems = List.of();
   protected List<MergedItemStack> compactItems = List.of();
   protected PreviewContext previewContext;
   protected final int slotWidth;
   protected final int slotHeight;
   protected final int slotXOffset;
   protected final int slotYOffset;

   protected BasePreviewRenderer(int slotWidth, int slotHeight, int slotXOffset, int slotYOffset) {
      this.previewType = PreviewType.FULL;
      this.maxRowSize = 9;
      this.slotWidth = slotWidth;
      this.slotHeight = slotHeight;
      this.slotXOffset = slotXOffset;
      this.slotYOffset = slotYOffset;
      class_638 world = ShulkerBoxTooltipClient.client == null ? null : ShulkerBoxTooltipClient.client.field_1687;
      this.setPreview(PreviewContext.builder(class_1799.field_8037).withRegistryLookup(world == null ? null : world.method_30349()).build(), EmptyPreviewProvider.INSTANCE);
   }

   protected int getMaxRowSize() {
      return this.previewType == PreviewType.COMPACT ? this.compactMaxRowSize : this.maxRowSize;
   }

   public void setPreviewType(PreviewType type) {
      this.previewType = type;
   }

   public void setPreview(PreviewContext context, PreviewProvider provider) {
      int rowSize = provider.getMaxRowSize(context);
      int compactRowSize = provider.getCompactMaxRowSize(context);
      this.config = context.config();
      if (compactRowSize <= 0) {
         compactRowSize = this.config.defaultMaxRowSize();
      }

      if (compactRowSize <= 0) {
         compactRowSize = 9;
      }

      if (rowSize <= 0) {
         rowSize = compactRowSize;
      }

      this.maxRowSize = rowSize;
      this.compactMaxRowSize = compactRowSize;
      this.textureOverride = provider.getTextureOverride(context);
      this.provider = provider;
      this.fullItems = provider.getInventory(context);
      this.compactItems = MergedItemStack.mergeInventory(this.fullItems, provider.getInventoryMaxSize(context), this.config.itemStackMergingStrategy());
      this.previewContext = context;
   }

   protected int getSlotAt(int x, int y) {
      int slot = -1;
      if (x + 1 >= this.slotXOffset && y + 1 >= this.slotYOffset) {
         int maxRowSize = this.getMaxRowSize();
         int slotX = (x + 1 - this.slotXOffset) / this.slotWidth;
         int slotY = (y + 1 - this.slotYOffset) / this.slotHeight;
         if (slotX < maxRowSize) {
            slot = slotX + slotY * maxRowSize;
         }
      }

      return slot;
   }

   private class_1799 getStackAt(int x, int y) {
      int slot = this.getSlotAt(x, y);
      if (this.previewType == PreviewType.COMPACT) {
         if (slot >= 0 && slot < this.compactItems.size()) {
            MergedItemStack merged = (MergedItemStack)this.compactItems.get(slot);
            return merged == null ? class_1799.field_8037 : merged.get();
         } else {
            return class_1799.field_8037;
         }
      } else {
         return slot >= 0 && slot < this.fullItems.size() ? (class_1799)this.fullItems.get(slot) : class_1799.field_8037;
      }
   }

   protected void drawSlots(int x, int y, class_332 graphics, class_327 font, int mouseX, int mouseY, int maxSlot) {
      int highlightedSlot = this.getSlotAt(mouseX - x, mouseY - y);
      if (this.previewType == PreviewType.COMPACT) {
         boolean shortItemCounts = this.config.shortItemCounts();
         int slot = 0;

         for(int size = this.compactItems.size(); slot < size; ++slot) {
            if (slot <= maxSlot) {
               this.drawSlot(((MergedItemStack)this.compactItems.get(slot)).get(), x, y, graphics, font, slot, highlightedSlot == slot, shortItemCounts);
            }
         }
      } else {
         int slot = 0;

         for(int size = this.fullItems.size(); slot < size; ++slot) {
            if (slot <= maxSlot) {
               this.drawSlot((class_1799)this.fullItems.get(slot), x, y, graphics, font, slot, highlightedSlot == slot, false);
            }
         }
      }

   }

   protected abstract void drawSlot(class_1799 var1, int var2, int var3, class_332 var4, class_327 var5, int var6, boolean var7, boolean var8);

   protected void drawItem(class_1799 stack, int x, int y, class_332 graphics, class_327 font, boolean shortItemCount) {
      String countLabel = "";
      if (stack.method_7947() != 1) {
         if (shortItemCount) {
            countLabel = ShulkerBoxTooltipUtil.abbreviateInteger(stack.method_7947());
         } else {
            countLabel = String.valueOf(stack.method_7947());
         }
      }

      graphics.method_51427(stack, x, y);
      graphics.method_51432(font, stack, x, y, countLabel);
   }

   protected void drawInnerTooltip(int x, int y, class_332 graphics, class_327 font, int mouseX, int mouseY) {
      class_1799 stack = this.getStackAt(mouseX - x, mouseY - y);
      if (!stack.method_7960()) {
         graphics.method_51446(font, stack, mouseX, mouseY);
      }

   }
}
