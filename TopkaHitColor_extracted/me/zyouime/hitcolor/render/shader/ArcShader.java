package me.zyouime.hitcolor.render.shader;

import java.awt.Color;
import net.minecraft.class_10156;

public class ArcShader extends AbstractShader {
   public ArcShader(class_10156 shaderKey) {
      super(shaderKey);
   }

   public void setUniforms(float x, float y, float width, float height, float radius, float arcWidth, Color color) {
      this.shader.method_34582("rectPos").method_1255(x, y);
      this.shader.method_34582("rectSize").method_1255(width, height);
      this.shader.method_34582("radius").method_1251(radius);
      this.shader.method_34582("rectColor").method_1253(this.getColor(color));
      this.shader.method_34582("arcWidth").method_1251(arcWidth);
   }
}
