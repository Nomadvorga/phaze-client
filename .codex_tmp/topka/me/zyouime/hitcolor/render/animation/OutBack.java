package me.zyouime.hitcolor.render.animation;

import lombok.Generated;
import net.minecraft.class_310;
import net.minecraft.class_3532;

public class OutBack {
   private float prevTick;
   private float tick;
   private float maxTick;
   private boolean update;

   public OutBack(int maxTick) {
      this.update = true;
      this.maxTick = (float)maxTick;
   }

   public OutBack() {
      this(10);
   }

   public double dropAnimation(double value) {
      return (double)1.0F + 2.70158 * Math.pow(value - (double)1.0F, (double)3.0F) + 1.70158 * Math.pow(value - (double)1.0F, (double)2.0F);
   }

   public void update(boolean update) {
      this.update = update;
      this.update();
   }

   public void update() {
      this.prevTick = this.tick;
      float speed = 1.0F * Anim.deltaTime() * 60.0F;
      this.tick += this.update ? speed : -speed;
      this.tick = class_3532.method_15363(this.tick, 0.0F, this.maxTick);
   }

   public double getAnimationd() {
      float t = this.prevTick + (this.tick - this.prevTick) * class_310.method_1551().method_61966().method_60637(true);
      return this.dropAnimation((double)(t / this.maxTick));
   }

   public void reset() {
      this.prevTick = 0.0F;
      this.tick = 0.0F;
   }

   @Generated
   public void setMaxTick(float maxTick) {
      this.maxTick = maxTick;
   }

   @Generated
   public void setUpdate(boolean update) {
      this.update = update;
   }

   @Generated
   public boolean isUpdate() {
      return this.update;
   }
}
