package com.misterpemodder.shulkerboxtooltip.api.renderer;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.renderer.ModPreviewRenderer;
import com.misterpemodder.shulkerboxtooltip.impl.renderer.VanillaPreviewRenderer;
import javax.annotation.Nonnull;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_327;
import net.minecraft.class_332;

@Environment(EnvType.CLIENT)
public interface PreviewRenderer {
   @Nonnull
   static PreviewRenderer getDefaultRendererInstance() {
      return ShulkerBoxTooltip.config.preview.theme == Configuration.Theme.VANILLA ? getVanillaRendererInstance() : getModRendererInstance();
   }

   @Nonnull
   static PreviewRenderer getModRendererInstance() {
      return ModPreviewRenderer.INSTANCE;
   }

   @Nonnull
   static PreviewRenderer getVanillaRendererInstance() {
      return VanillaPreviewRenderer.INSTANCE;
   }

   int getHeight();

   int getWidth();

   void setPreview(PreviewContext var1, PreviewProvider var2);

   void setPreviewType(PreviewType var1);

   /** @deprecated */
   @Deprecated(
      forRemoval = true,
      since = "5.2.0"
   )
   default void draw(int x, int y, class_332 graphics, class_327 font, int mouseX, int mouseY) {
      throw new UnsupportedOperationException("Method not implemented");
   }

   default void draw(int x, int y, int viewportWidth, int viewportHeight, class_332 graphics, class_327 font, int mouseX, int mouseY) {
      this.draw(x, y, graphics, font, mouseX, mouseY);
   }
}
