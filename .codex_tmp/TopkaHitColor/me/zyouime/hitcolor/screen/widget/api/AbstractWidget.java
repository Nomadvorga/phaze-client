package me.zyouime.hitcolor.screen.widget.api;

import java.awt.Color;
import lombok.Generated;
import me.zyouime.hitcolor.render.RenderHelper;
import me.zyouime.hitcolor.render.animation.WidgetAnim;
import me.zyouime.hitcolor.screen.widget.CustomTooltip;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_8030;

public class AbstractWidget implements class_364, class_4068 {
   public float x;
   public float y;
   public float width;
   public float height;
   public boolean active = true;
   private CustomTooltip tooltip;

   public AbstractWidget(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
   }

   public boolean method_25405(double mouseX, double mouseY) {
      return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
   }

   public void method_25365(boolean focused) {
   }

   public void updateAnim() {
   }

   public void resetAnim() {
   }

   public void updateAnim(boolean bl) {
   }

   public boolean method_25370() {
      return false;
   }

   public void updatePos(float x, float y) {
      this.updatePos(x, y, this.width, this.height);
   }

   public void updatePos(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
   }

   public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
   }

   public void renderTooltip(class_332 context, int mouseX, int mouseY) {
      if (this.tooltip != null) {
         this.tooltip.render(context, (double)mouseX, (double)mouseY);
      }

   }

   public void renderDarkening(class_332 context, float animProgress, float radius) {
      if (!this.active) {
         WidgetAnim anim = WidgetAnim.getAnim(this.getX(), this.getY(), this.getWidth(), this.getHeight(), radius, animProgress);
         RenderHelper.drawRoundedRect(context.method_51448(), anim.x(), anim.y(), anim.width(), anim.height(), anim.radius(), new Color(0, 0, 0, 80));
      }

   }

   public AbstractWidget getWidgetAtPos(int mouseX, int mouseY) {
      return this.method_25405((double)mouseX, (double)mouseY) ? this : null;
   }

   public class_8030 method_48202() {
      return new class_8030((int)this.getX(), (int)this.getY(), (int)this.getWidth(), (int)this.getHeight());
   }

   @Generated
   public float getX() {
      return this.x;
   }

   @Generated
   public float getY() {
      return this.y;
   }

   @Generated
   public float getWidth() {
      return this.width;
   }

   @Generated
   public float getHeight() {
      return this.height;
   }

   @Generated
   public boolean isActive() {
      return this.active;
   }

   @Generated
   public CustomTooltip getTooltip() {
      return this.tooltip;
   }

   @Generated
   public void setX(float x) {
      this.x = x;
   }

   @Generated
   public void setY(float y) {
      this.y = y;
   }

   @Generated
   public void setWidth(float width) {
      this.width = width;
   }

   @Generated
   public void setHeight(float height) {
      this.height = height;
   }

   @Generated
   public void setActive(boolean active) {
      this.active = active;
   }

   @Generated
   public void setTooltip(CustomTooltip tooltip) {
      this.tooltip = tooltip;
   }
}
