package me.zyouime.hitcolor.render.shader;

import java.awt.Color;
import net.minecraft.class_10156;

public class CircleShader extends AbstractShader {
   public CircleShader(class_10156 shaderKey) {
      super(shaderKey);
   }

   public void setUniforms(float x, float y, float radius, Color color) {
      this.shader.method_34582("pos").method_1255(x, y);
      this.shader.method_34582("radius").method_1251(radius);
      this.shader.method_34582("color").method_1253(this.getColor(color));
   }
}
