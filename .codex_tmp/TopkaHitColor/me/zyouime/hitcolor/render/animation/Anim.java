package me.zyouime.hitcolor.render.animation;

import net.minecraft.class_310;

public class Anim {
   public static float deltaTime() {
      return class_310.method_1551().method_47599() > 5 ? 1.0F / (float)class_310.method_1551().method_47599() : 0.016F;
   }

   public static float fast(float end, float start, float multiple) {
      float clampedDelta = Math.clamp(deltaTime() * multiple, 0.0F, 1.0F);
      return (1.0F - clampedDelta) * end + clampedDelta * start;
   }
}
