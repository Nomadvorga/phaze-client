package com.misterpemodder.shulkerboxtooltip.impl.tooltip;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.renderer.PreviewRenderer;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_5684;
import org.jetbrains.annotations.NotNull;

public class PreviewClientTooltipComponent implements class_5684 {
   private final PreviewRenderer renderer;

   public PreviewClientTooltipComponent(PreviewTooltipComponent data) {
      PreviewRenderer renderer = data.provider().getRenderer();
      if (renderer == null) {
         renderer = PreviewRenderer.getDefaultRendererInstance();
      }

      this.renderer = renderer;
      PreviewProvider provider = data.provider();
      PreviewContext context = data.context();
      renderer.setPreview(context, provider);
      renderer.setPreviewType(ShulkerBoxTooltipApi.getCurrentPreviewType(provider.isFullPreviewAvailable(context)));
   }

   public int method_32661(@NotNull class_327 font) {
      return ShulkerBoxTooltip.config.preview.position == Configuration.PreviewPosition.INSIDE ? this.renderer.getHeight() + 4 : 0;
   }

   public int method_32664(@NotNull class_327 font) {
      return ShulkerBoxTooltip.config.preview.position == Configuration.PreviewPosition.INSIDE ? this.renderer.getWidth() : 0;
   }

   public void method_32666(@NotNull class_327 font, int x, int y, int totalWidth, int totalHeight, @NotNull class_332 graphics) {
      this.renderImageExtended(font, x, y, totalWidth, totalHeight, graphics, 0, 0, Integer.MIN_VALUE);
   }

   public void renderImageExtended(@NotNull class_327 font, int x, int y, int totalWidth, int totalHeight, @NotNull class_332 graphics, int mouseX, int mouseY, int tooltipTopY) {
      Configuration.PreviewPosition position = ShulkerBoxTooltip.config.preview.position;
      int viewportHeight = this.renderer.getHeight();
      if (tooltipTopY == Integer.MIN_VALUE) {
         position = Configuration.PreviewPosition.INSIDE;
      }

      if (position == Configuration.PreviewPosition.OUTSIDE) {
         int screenH = graphics.method_51443();
         position = tooltipTopY + totalHeight + viewportHeight > screenH ? Configuration.PreviewPosition.OUTSIDE_TOP : Configuration.PreviewPosition.OUTSIDE_BOTTOM;
      }

      if (position == Configuration.PreviewPosition.OUTSIDE_TOP) {
         x -= 4;
         y = tooltipTopY - viewportHeight - 4;
      } else if (position == Configuration.PreviewPosition.OUTSIDE_BOTTOM) {
         x -= 4;
         y = tooltipTopY + totalHeight + 4;
      }

      this.renderer.draw(x, y, totalWidth, viewportHeight, graphics, font, mouseX, mouseY);
   }
}
