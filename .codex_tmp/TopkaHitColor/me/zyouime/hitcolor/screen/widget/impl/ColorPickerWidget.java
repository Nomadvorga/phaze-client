package me.zyouime.hitcolor.screen.widget.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import me.zyouime.hitcolor.render.RenderHelper;
import me.zyouime.hitcolor.render.animation.EaseAnim;
import me.zyouime.hitcolor.render.animation.WidgetAnim;
import me.zyouime.hitcolor.render.font.FontRenderers;
import me.zyouime.hitcolor.screen.widget.api.AbstractWidget;
import me.zyouime.hitcolor.setting.Setting;
import me.zyouime.hitcolor.util.ColorHelper;
import net.minecraft.class_332;
import net.minecraft.class_4587;

public class ColorPickerWidget extends AbstractWidget {
   private float hue;
   private float saturation;
   private float brightness;
   private final EaseAnim animation = new EaseAnim(10);
   private final EaseAnim alphaAnimation = new EaseAnim(25);
   private final boolean hasAlpha;
   private int alpha;
   private final Setting<Color> colorSetting;
   private boolean dragPalet;
   private boolean dragHue;
   private boolean dragAlpha;

   public ColorPickerWidget(float x, float y, float width, float height, Setting<Color> setting, boolean hasAlpha) {
      super(x, y, width, height);
      this.hasAlpha = hasAlpha;
      this.colorSetting = setting;
      this.setup();
   }

   private void drawPalette(class_4587 matrixStack, float x, float y, float width, float height, float hue, float radius) {
      Color A = Color.getHSBColor(hue, 0.0F, 1.0F);
      Color B = Color.getHSBColor(hue, 1.0F, 1.0F);
      RenderHelper.drawRoundedGradientRect(matrixStack, x, y, width, height, radius, A, B, 1);
      Color C = new Color(0, 0, 0, 0);
      Color D = new Color(0, 0, 0);
      RenderHelper.drawRoundedGradientRect(matrixStack, x, y, width, height, radius, C, D, 0);
      float palettePointerX = x + this.saturation * width;
      float palettePointerY = y + (1.0F - this.brightness) * height;
      RenderHelper.drawCircle(matrixStack, palettePointerX, palettePointerY, radius + 0.4F, Color.WHITE);
      RenderHelper.drawCircle(matrixStack, palettePointerX, palettePointerY, radius - 0.5F, Color.getHSBColor(hue, this.saturation, this.brightness));
   }

   private void drawHueSlider(class_4587 matrixStack, float x, float y, float width, float height, float radius) {
      int segments = 512;
      float segmentHeight = height / (float)segments;
      RenderSystem.enableBlend();
      RenderSystem.colorMask(false, false, false, true);
      RenderSystem.defaultBlendFunc();
      RenderHelper.drawRoundedRectWithoutSetup(matrixStack, x, y, width, height, radius, Color.BLACK);
      RenderSystem.colorMask(true, true, true, true);
      RenderSystem.blendFunc(772, 773);

      for(int i = 0; i < segments; ++i) {
         float hue = (float)i / (float)segments;
         Color color1 = Color.getHSBColor(hue, 1.0F, 1.0F);
         RenderHelper.drawRectWithoutSetup(matrixStack, x, y + (float)i * segmentHeight, width, segmentHeight, color1);
      }

      RenderSystem.disableBlend();
      float huePointX = x + radius;
      float huePointY = y + this.hue * height;
      RenderHelper.drawCircle(matrixStack, huePointX, huePointY, radius - 1.6F, Color.WHITE);
      RenderHelper.drawCircle(matrixStack, huePointX, huePointY, radius - 2.5F, Color.getHSBColor(this.hue, 1.0F, 1.0F));
   }

   private void drawAlphaSlider(class_4587 matrixStack, float x, float y, float width, float height, float radius) {
      RenderHelper.drawArc(matrixStack, x, y, width, height, radius, 1.0F, Color.GRAY);
      RenderHelper.drawRoundedGradientRect(matrixStack, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, 5.0F, Color.WHITE, new Color(0, 0, 0, 30), 0);
      float alphaPointerX = x + 6.0F;
      float alphaPointerY = y + (1.0F - (float)this.alpha / 255.0F) * height;
      RenderHelper.drawCircle(matrixStack, alphaPointerX, alphaPointerY, radius - 2.5F, Color.WHITE);
   }

   public void method_25394(class_332 context, int mouseX, int mouseY, float tickDelta) {
      Color color = Color.getHSBColor(this.hue, this.saturation, this.brightness);
      class_4587 matrixStack = context.method_51448();
      float animProgress = (float)this.animation.getAnimationd();
      float alphaProgress = (float)this.alphaAnimation.getAnimationd();
      float hueWidth = 12.0F;
      float paletteX = this.x + 15.0F;
      float paletteHueAlphaY = this.y + 15.0F;
      WidgetAnim paletteAnim = WidgetAnim.getAnim(paletteX, paletteHueAlphaY, this.width - 20.0F, this.height, 4.0F, animProgress);
      WidgetAnim hueAnim = WidgetAnim.getAnim(this.x + this.width, paletteHueAlphaY, hueWidth, this.height, 6.0F, animProgress);
      WidgetAnim alphaAnim = WidgetAnim.getAnim(this.x + this.width + 17.0F, paletteHueAlphaY, hueWidth, this.height, 6.0F, animProgress);
      this.drawPalette(matrixStack, paletteAnim.x(), paletteAnim.y(), paletteAnim.width(), paletteAnim.height(), this.hue, paletteAnim.radius());
      this.drawHueSlider(matrixStack, hueAnim.x(), hueAnim.y(), hueAnim.width(), hueAnim.height(), hueAnim.radius());
      if (this.hasAlpha) {
         this.drawAlphaSlider(matrixStack, alphaAnim.x(), alphaAnim.y(), alphaAnim.width(), alphaAnim.height(), alphaAnim.radius());
      }

      matrixStack.method_22903();
      float scale = 1.0F * animProgress;
      matrixStack.method_22905(scale, scale, 1.0F);
      FontRenderers.mainFont.drawCenteredYString(matrixStack, "Color: " + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + ", " + this.alpha, (double)(paletteX / scale), (double)((paletteHueAlphaY + this.height - 5.0F) / scale), ColorHelper.injectAlpha(Color.WHITE, 255.0F * alphaProgress));
      matrixStack.method_22909();
      this.animation.update(true);
      this.alphaAnimation.update(true);
      super.method_25394(context, mouseX, mouseY, tickDelta);
   }

   public boolean method_25405(double mouseX, double mouseY) {
      return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width + 41.0F) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
   }

   public boolean method_25402(double mouseX, double mouseY, int button) {
      float hueX = this.x + this.width;
      float alphaX = hueX + 17.0F;
      if (mouseX >= (double)(this.x + 15.0F) && mouseX <= (double)(this.x + 15.0F + this.width - 20.0F) && mouseY >= (double)(this.y + 15.0F) && mouseY <= (double)(this.y + 15.0F + this.height)) {
         this.calcPalette(mouseX, mouseY);
         this.dragPalet = true;
         return true;
      } else if (mouseX >= (double)hueX && mouseX <= (double)(hueX + 12.0F) && mouseY >= (double)(this.y + 15.0F) && mouseY <= (double)(this.y + 15.0F + this.height)) {
         this.calcHue(mouseY);
         this.dragHue = true;
         return true;
      } else if (this.hasAlpha && mouseX >= (double)alphaX && mouseX <= (double)(alphaX + 12.0F) && mouseY >= (double)(this.y + 14.5F) && mouseY <= (double)(this.y + 15.5F + this.height)) {
         this.calcAlpha(mouseY);
         this.dragAlpha = true;
         return true;
      } else {
         return false;
      }
   }

   public boolean method_25406(double mouseX, double mouseY, int button) {
      this.dragAlpha = false;
      this.dragHue = false;
      this.dragPalet = false;
      return true;
   }

   public boolean method_25403(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.dragHue) {
         this.calcHue(mouseY);
      } else if (this.dragPalet) {
         this.calcPalette(mouseX, mouseY);
      } else if (this.dragAlpha) {
         this.calcAlpha(mouseY);
      }

      return false;
   }

   private void calcPalette(double mouseX, double mouseY) {
      this.saturation = Math.max(0.0F, Math.min(1.0F, (float)((mouseX - (double)(this.x + 15.0F)) / (double)(this.width - 20.0F))));
      this.brightness = Math.max(0.0F, Math.min(1.0F, (float)((double)1.0F - (mouseY - (double)(this.y + 15.0F)) / (double)this.height)));
      this.save();
   }

   private void calcHue(double mouseY) {
      this.hue = Math.max(0.0F, Math.min(1.0F, (float)((mouseY - (double)(this.y + 15.0F)) / (double)this.height)));
      this.save();
   }

   private void calcAlpha(double mouseY) {
      this.alpha = (int)((double)255.0F * ((double)1.0F - (mouseY - (double)(this.y + 15.0F)) / (double)this.height));
      this.alpha = Math.max(0, Math.min(255, this.alpha));
      this.save();
   }

   private void save() {
      int rgb = Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
      Color saveColor = new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, this.alpha);
      if (this.colorSetting != null) {
         this.colorSetting.setValue(saveColor);
      }

   }

   private void setup() {
      Color color1 = this.colorSetting.getValue();
      float[] hsb = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), (float[])null);
      this.hue = hsb[0];
      this.saturation = hsb[1];
      this.brightness = hsb[2];
      this.alpha = color1.getAlpha();
   }

   public void resetAnim() {
      this.animation.reset();
   }
}
