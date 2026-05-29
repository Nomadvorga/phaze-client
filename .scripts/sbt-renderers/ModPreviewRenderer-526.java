package com.misterpemodder.shulkerboxtooltip.impl.renderer;

import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;

@Environment(EnvType.CLIENT)
public class ModPreviewRenderer extends BasePreviewRenderer {
   public static final ModPreviewRenderer INSTANCE = new ModPreviewRenderer();
   private static final class_2960 DEFAULT_TEXTURE_LIGHT = ShulkerBoxTooltipUtil.id("shulker_box_tooltip");
   private static final class_2960 SLOT_HIGHLIGHT_BACK_SPRITE = class_2960.method_60656("container/slot_highlight_back");
   private static final class_2960 SLOT_HIGHLIGHT_FRONT_SPRITE = class_2960.method_60656("container/slot_highlight_front");

   ModPreviewRenderer() {
      super(18, 18, 8, 8);
   }

   public int getWidth() {
      return 14 + Math.min(this.getMaxRowSize(), this.getInvSize()) * 18;
   }

   public int getHeight() {
      return 14 + (int)Math.ceil((double)this.getInvSize() / (double)this.getMaxRowSize()) * 18;
   }

   private int getInvSize() {
      return this.previewType == PreviewType.COMPACT ? Math.max(1, this.compactItems.size()) : this.provider.getInventoryMaxSize(this.previewContext);
   }

   private int getColor() {
      ColorKey key;
      if (this.config.useColors()) {
         key = this.provider.getWindowColorKey(this.previewContext);
      } else {
         key = ColorKey.DEFAULT;
      }

      return -16777216 | key.rgb();
   }

   private class_2960 getTexture() {
      return this.textureOverride != null ? this.textureOverride : DEFAULT_TEXTURE_LIGHT;
   }

   private void drawBackground(int x, int y, class_332 graphics) {
      int invSize = this.getInvSize();
      int slotSize = 18;
      int rows = Math.min(this.getMaxRowSize(), invSize);
      int cols = (int)Math.ceil((double)invSize / (double)rows);
      graphics.method_52707(class_1921::method_62275, this.getTexture(), x, y, 14 + rows * slotSize, 14 + cols * slotSize, this.getColor());
   }

   public void draw(int x, int y, int viewportWidth, int viewportHeight, class_332 graphics, class_327 font, int mouseX, int mouseY) {
      if (!this.compactItems.isEmpty() && this.previewType != PreviewType.NO_PREVIEW) {
         this.drawBackground(x, y, graphics);
         this.drawSlots(x, y, graphics, font, mouseX, mouseY, Integer.MAX_VALUE);
         this.drawInnerTooltip(x, y, graphics, font, mouseX, mouseY);
      }
   }

   protected void drawSlot(class_1799 stack, int x, int y, class_332 graphics, class_327 font, int slot, boolean isHighlighted, boolean shortItemCount) {
      int maxRowSize = this.getMaxRowSize();
      int sx = this.slotXOffset + x + this.slotWidth * (slot % maxRowSize);
      int sy = this.slotYOffset + y + this.slotHeight * (slot / maxRowSize);
      if (isHighlighted) {
         graphics.method_52706(class_1921::method_62277, SLOT_HIGHLIGHT_BACK_SPRITE, sx - 4, sy - 4, 24, 24);
      }

      if (!stack.method_7960()) {
         this.drawItem(stack, sx, sy, graphics, font, shortItemCount);
      }

      if (isHighlighted) {
         graphics.method_52706(class_1921::method_62275, SLOT_HIGHLIGHT_FRONT_SPRITE, sx - 4, sy - 4, 24, 24);
      }

   }
}
