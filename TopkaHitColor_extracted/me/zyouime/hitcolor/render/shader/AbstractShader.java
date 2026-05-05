package me.zyouime.hitcolor.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import net.minecraft.class_10156;
import net.minecraft.class_310;
import net.minecraft.class_5944;

public class AbstractShader {
   protected class_5944 shader;
   protected final class_10156 shaderKey;

   public AbstractShader(class_10156 shaderKey) {
      this.shaderKey = shaderKey;
      this.reload();
   }

   public void reload() {
      this.shader = class_310.method_1551().method_62887().method_62947(this.shaderKey);
   }

   public void bind() {
      RenderSystem.setShader(this.shader);
   }

   public float[] getColor(Color color) {
      return new float[]{(float)color.getRed() / 255.0F, (float)color.getGreen() / 255.0F, (float)color.getBlue() / 255.0F, (float)color.getAlpha() / 255.0F};
   }
}
