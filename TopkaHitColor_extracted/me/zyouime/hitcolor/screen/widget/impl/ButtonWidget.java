package me.zyouime.hitcolor.screen.widget.impl;

import java.awt.Color;
import java.util.function.Consumer;
import lombok.Generated;
import me.zyouime.hitcolor.render.RenderHelper;
import me.zyouime.hitcolor.render.animation.EaseAnim;
import me.zyouime.hitcolor.render.animation.WidgetAnim;
import me.zyouime.hitcolor.screen.widget.api.AbstractWidget;
import net.minecraft.class_332;
import net.minecraft.class_4587;

public class ButtonWidget extends AbstractWidget {
   private final Consumer<ButtonWidget> runnable;
   protected String msg;
   protected Color backgroundColor;
   protected Color arcColor;
   protected Color textColor;
   protected final EaseAnim anim;

   public ButtonWidget(float width, float height, Consumer<ButtonWidget> onPress, String msg) {
      this(0.0F, 0.0F, width, height, onPress, msg);
   }

   public ButtonWidget(float x, float y, float width, float height, Consumer<ButtonWidget> onPress, String msg) {
      super(x, y, width, height);
      this.backgroundColor = new Color(47, 74, 102);
      this.arcColor = new Color(164, 199, 235);
      this.textColor = Color.WHITE;
      this.anim = new EaseAnim(10);
      this.runnable = onPress;
      this.msg = msg;
   }

   public void updateAnim() {
      this.anim.update();
   }

   public void updateAnim(boolean bl) {
      this.anim.update(bl);
   }

   public void method_25394(class_332 context, int mouseX, int mouseY, float deltaTIck) {
      float progress = (float)this.anim.getAnimationd();
      class_4587 matrixStack = context.method_51448();
      WidgetAnim arcAnim = WidgetAnim.getAnim(this.x, this.y, this.width, this.height, 4.0F, progress);
      WidgetAnim backAnim = WidgetAnim.getAnim(this.x + 1.0F, this.y + 1.0F, this.width - 2.0F, this.height - 2.0F, 3.0F, progress);
      RenderHelper.drawArc(matrixStack, arcAnim.x(), arcAnim.y(), arcAnim.width(), arcAnim.height(), arcAnim.radius(), 1.0F * progress, this.arcColor);
      RenderHelper.drawRoundedRect(matrixStack, backAnim.x(), backAnim.y(), backAnim.width(), backAnim.height(), backAnim.radius(), this.backgroundColor);
      this.renderHeader(context, progress);
      this.renderDarkening(context, progress, 4.0F);
   }

   public void renderHeader(class_332 context, float animProgress) {
      RenderHelper.drawCenteredAnimatedText(context, this.msg, this.x + this.width / 2.0F, this.y + 5.0F, (float)this.anim.getAnimationd(), this.textColor);
   }

   public void setArcAndBackgroundColor(Color arc, Color background) {
      this.setArcColor(arc);
      this.setBackgroundColor(background);
   }

   public void resetAnim() {
      this.anim.reset();
   }

   public boolean method_25402(double mouseX, double mouseY, int button) {
      if (this.method_25405(mouseX, mouseY)) {
         if (!this.active) {
            return true;
         } else {
            this.runnable.accept(this);
            this.resetAnim();
            return true;
         }
      } else {
         return false;
      }
   }

   @Generated
   public void setMsg(String msg) {
      this.msg = msg;
   }

   @Generated
   public void setBackgroundColor(Color backgroundColor) {
      this.backgroundColor = backgroundColor;
   }

   @Generated
   public void setArcColor(Color arcColor) {
      this.arcColor = arcColor;
   }

   @Generated
   public void setTextColor(Color textColor) {
      this.textColor = textColor;
   }
}
