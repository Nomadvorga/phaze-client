package me.zyouime.hitcolor.render.shader;

import java.awt.Color;
import net.minecraft.class_10156;

public class GradientRectangleShader extends AbstractShader {
   public GradientRectangleShader(class_10156 shader) {
      super(shader);
   }

   public void setUniforms(float x, float y, float width, float height, float radius, int dir, Color startColor, Color endColor) {
      this.shader.method_34582("rectPos").method_1255(x, y);
      this.shader.method_34582("rectSize").method_1255(width, height);
      this.shader.method_34582("radius").method_1251(radius);
      this.shader.method_34582("startColor").method_1253(this.getColor(startColor));
      this.shader.method_34582("endColor").method_1253(this.getColor(endColor));
      this.shader.method_34582("gradientDirection").method_35649(dir);
   }
}
