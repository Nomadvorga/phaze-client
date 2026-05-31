package me.zyouime.hitcolor.render.shader;

import java.awt.Color;
import net.minecraft.class_10156;

public class RectangleShader extends AbstractShader {
   public RectangleShader(class_10156 shader) {
      super(shader);
   }

   public void setUniforms(float x, float y, float width, float height, float radius, Color color) {
      this.shader.method_34582("rectPos").method_1255(x, y);
      this.shader.method_34582("rectSize").method_1255(width, height);
      this.shader.method_34582("radius").method_1251(radius);
      this.shader.method_34582("rectColor").method_1253(this.getColor(color));
   }
}
