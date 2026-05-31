package me.zyouime.hitcolor.screen.widget.impl;

import java.awt.Color;
import java.util.function.Consumer;
import me.zyouime.hitcolor.render.RenderHelper;
import me.zyouime.hitcolor.render.animation.EaseAnim;
import me.zyouime.hitcolor.render.animation.WidgetAnim;
import me.zyouime.hitcolor.setting.Setting;
import me.zyouime.hitcolor.util.Util;
import net.minecraft.class_332;

public class ToggleWidget extends ButtonWidget {
   private final Color activeColor;
   private final EaseAnim sizeAnimation = new EaseAnim(10);
   private final Setting<Boolean> setting;
   private final float size;

   public ToggleWidget(float x, float y, float width, float size, Setting<Boolean> setting, Consumer<ButtonWidget> callback, Color color, String optionName) {
      super(x, y, width, size, callback, optionName);
      this.activeColor = color;
      this.setting = setting;
      this.size = size;
   }

   public void method_25394(class_332 context, int mouseX, int mouseY, float deltaTick) {
      float sizeProgress = (float)this.sizeAnimation.getAnimationd();
      Color currentColor = (Boolean)this.setting.getValue() ? this.activeColor : new Color(196, 196, 196, 196);
      float radius = this.height / 4.0F;
      WidgetAnim anim = WidgetAnim.getAnim(this.x + this.width - this.size, this.y, this.size, this.size, radius, sizeProgress);
      RenderHelper.drawRoundedRect(context.method_51448(), anim.x(), anim.y(), anim.width(), anim.height(), anim.radius(), currentColor);
      String renderText = Util.substringToWidth(this.msg, this.width / 1.5F + 2.0F);
      RenderHelper.drawAnimatedText(context, renderText, this.x, this.y + this.height / 4.0F, sizeProgress, 1.0F, Color.WHITE);
      this.renderDarkening(context, sizeProgress, radius);
   }

   public void renderDarkening(class_332 context, float animProgress, float radius) {
      if (!this.active) {
         WidgetAnim anim = WidgetAnim.getAnim(this.getX() + this.width - this.size, this.getY(), this.size, this.size, this.height / 4.0F, animProgress);
         RenderHelper.drawRoundedRect(context.method_51448(), anim.x(), anim.y(), anim.width(), anim.height(), anim.radius(), new Color(0, 0, 0, 80));
      }

   }

   public void updateAnim(boolean bl) {
      super.updateAnim(bl);
      this.sizeAnimation.update(bl);
   }

   public void updateAnim() {
      super.updateAnim();
      this.sizeAnimation.update();
   }

   public boolean method_25405(double mouseX, double mouseY) {
      return mouseX >= (double)(this.x + this.width - this.size) && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
   }

   public void resetAnim() {
      this.sizeAnimation.reset();
   }
}
