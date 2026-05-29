package com.misterpemodder.shulkerboxtooltip.mixin.client;

import com.misterpemodder.shulkerboxtooltip.impl.hook.GuiGraphicsExtensions;
import com.misterpemodder.shulkerboxtooltip.impl.tooltip.PreviewClientTooltipComponent;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_5684;
import net.minecraft.class_8000;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({class_332.class})
public class GuiGraphicsMixin implements GuiGraphicsExtensions {
   @Unique
   private int tooltipTopY = 0;
   @Unique
   private int mouseX = 0;
   @Unique
   private int mouseY = 0;

   @Redirect(
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"
),
      method = {"renderTooltipInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/ResourceLocation;)V"},
      require = 0
   )
   private Vector2ic captureTooltipYPosition(class_8000 positioner, int guiWidth, int guiHeight, int x, int y, int totalWidth, int totalHeight) {
      Vector2ic result = positioner.method_47944(guiWidth, guiHeight, x, y, totalWidth, totalHeight);
      this.setTooltipTopYPosition(result.y());
      return result;
   }

   @Redirect(
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;renderImage(Lnet/minecraft/client/gui/Font;IIIILnet/minecraft/client/gui/GuiGraphics;)V"
),
      method = {"renderTooltipInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/ResourceLocation;)V"}
   )
   private void renderImageWithMouse(class_5684 component, class_327 font, int x, int y, int totalWidth, int totalHeight, class_332 graphics) {
      if (component instanceof PreviewClientTooltipComponent previewComponent) {
         previewComponent.renderImageExtended(font, x, y, totalWidth, totalHeight, graphics, this.getMouseX(), this.getMouseY(), this.getTooltipTopYPosition());
      } else {
         component.method_32666(font, x, y, totalWidth, totalHeight, graphics);
      }

   }

   @Intrinsic
   public void setTooltipTopYPosition(int topY) {
      this.tooltipTopY = topY;
   }

   @Intrinsic
   public int getTooltipTopYPosition() {
      return this.tooltipTopY;
   }

   @Intrinsic
   public void setMouseX(int mouseX) {
      this.mouseX = mouseX;
   }

   @Intrinsic
   public int getMouseX() {
      return this.mouseX;
   }

   @Intrinsic
   public void setMouseY(int mouseY) {
      this.mouseY = mouseY;
   }

   @Intrinsic
   public int getMouseY() {
      return this.mouseY;
   }
}
