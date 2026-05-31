package me.zyouime.hitcolor.render.shader;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_10149;
import net.minecraft.class_10156;
import net.minecraft.class_290;
import net.minecraft.class_293;
import net.minecraft.class_2960;

public class MyShaders {
   public static List<AbstractShader> shaders = new ArrayList();
   public static RectangleShader RECTANGLE_SHADER;
   public static GradientRectangleShader GRADIENT_RECTANGLE_SHADER;
   public static CircleShader CIRCLE_SHADER;
   public static ArcShader ARC_SHADER;

   private static class_10156 createShaderKey(String id, class_293 format) {
      return new class_10156(class_2960.method_60655("hitcolor", "core/" + id), format, class_10149.field_53930);
   }

   private static <T extends AbstractShader> T registerShader(T shader) {
      shaders.add(shader);
      return shader;
   }

   static {
      RECTANGLE_SHADER = (RectangleShader)registerShader(new RectangleShader(createShaderKey("rectangle", class_290.field_1592)));
      GRADIENT_RECTANGLE_SHADER = (GradientRectangleShader)registerShader(new GradientRectangleShader(createShaderKey("gradient_rectangle", class_290.field_1592)));
      CIRCLE_SHADER = (CircleShader)registerShader(new CircleShader(createShaderKey("circle", class_290.field_1592)));
      ARC_SHADER = (ArcShader)registerShader(new ArcShader(createShaderKey("arc", class_290.field_1592)));
   }
}
