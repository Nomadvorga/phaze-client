package com.misterpemodder.shulkerboxtooltip.impl.renderer;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_3532;

@Environment(EnvType.CLIENT)
public class VanillaPreviewRenderer extends BasePreviewRenderer {
   public static final VanillaPreviewRenderer INSTANCE = new VanillaPreviewRenderer();
   private static final class_2960 SLOT_HIGHLIGHT_BACK_SPRITE = class_2960.method_60656("container/bundle/slot_highlight_back");
   private static final class_2960 SLOT_HIGHLIGHT_FRONT_SPRITE = class_2960.method_60656("container/bundle/slot_highlight_front");
   private static final class_2960 SLOT_BACKGROUND_SPRITE = class_2960.method_60656("container/bundle/slot_background");
   private int lastNonEmptySlot;

   VanillaPreviewRenderer() {
      super(24, 24, 0, 0);
   }

   protected int getMaxRowSize() {
      return Math.min(super.getMaxRowSize(), this.getInvSize());
   }

   public int getWidth() {
      return this.getMaxRowSize() * 24;
   }

   public int getHeight() {
      return this.getRowCount() * 24;
   }

   private int getRowCount() {
      return (int)Math.ceil((double)this.getInvSize() / (double)this.getMaxRowSize());
   }

   protected int getInvSize() {
      return this.previewType == PreviewType.COMPACT ? Math.max(1, this.compactItems.size()) : this.lastNonEmptySlot + 1;
   }

   public void setPreview(PreviewContext context, PreviewProvider provider) {
      super.setPreview(context, provider);

      for(this.lastNonEmptySlot = this.fullItems.size() - 1; this.lastNonEmptySlot >= 0 && ((class_1799)this.fullItems.get(this.lastNonEmptySlot)).method_7960(); --this.lastNonEmptySlot) {
      }

   }

   protected int getSlotAt(int x, int y) {
      return class_3532.method_28139(this.getInvSize(), this.getMaxRowSize()) - super.getSlotAt(x - 1, y - 1) - 1;
   }

   public void draw(int x, int y, int viewportWidth, int viewportHeight, class_332 graphics, class_327 font, int mouseX, int mouseY) {
      if (!this.compactItems.isEmpty() && this.previewType != PreviewType.NO_PREVIEW) {
         x += (viewportWidth - this.getWidth()) / 2;
         this.drawSlots(x, y, graphics, font, mouseX, mouseY, this.lastNonEmptySlot);
         this.drawInnerTooltip(x, y, graphics, font, mouseX, mouseY);
      }
   }

   protected void drawSlot(class_1799 stack, int x, int y, class_332 graphics, class_327 font, int slot, boolean isHighlighted, boolean shortItemCount) {
      int maxRowSize = this.getMaxRowSize();
      slot = class_3532.method_28139(this.getInvSize(), maxRowSize) - slot - 1;
      int sx = this.slotXOffset + x + this.slotWidth * (slot % maxRowSize);
      int sy = this.slotYOffset + y + this.slotHeight * (slot / maxRowSize);
      if (isHighlighted) {
         graphics.method_52706(class_1921::method_62277, SLOT_HIGHLIGHT_BACK_SPRITE, sx, sy, 24, 24);
      } else {
         graphics.method_52706(class_1921::method_62277, SLOT_BACKGROUND_SPRITE, sx, sy, 24, 24);
      }

      if (!stack.method_7960()) {
         this.drawItem(stack, sx + 4, sy + 4, graphics, font, shortItemCount);
      }

      if (isHighlighted) {
         graphics.method_52706(class_1921::method_62275, SLOT_HIGHLIGHT_FRONT_SPRITE, sx, sy, 24, 24);
      }

   }
}
