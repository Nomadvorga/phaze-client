package me.zyouime.hitcolor.render.animation;

public record WidgetAnim(float x, float y, float width, float height, float radius) {
   public static WidgetAnim getAnim(float x, float y, float width, float height, float radius, float progress) {
      float radius1 = radius * progress;
      float centerX = x + width / 2.0F;
      float centerY = y + height / 2.0F;
      float width1 = width * progress;
      float height1 = height * progress;
      float x1 = centerX + (x - centerX) * progress;
      float y1 = centerY + (y - centerY) * progress;
      return new WidgetAnim(x1, y1, width1, height1, radius1);
   }
}
