package me.zyouime.hitcolor.render.animation;

public class EaseAnim extends OutBack {
   public EaseAnim(int maxTick) {
      super(maxTick);
   }

   public double dropAnimation(double value) {
      return (double)1.0F - Math.pow((double)1.0F - value, (double)3.0F);
   }
}
