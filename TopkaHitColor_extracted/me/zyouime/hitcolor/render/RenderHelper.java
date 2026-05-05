package me.zyouime.hitcolor.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import me.zyouime.hitcolor.render.font.FontRenderers;
import me.zyouime.hitcolor.render.shader.MyShaders;
import net.minecraft.class_10142;
import net.minecraft.class_1921;
import net.minecraft.class_286;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_293.class_5596;
import org.joml.Matrix4f;

public class RenderHelper {
   private static final class_289 tessellator = class_289.method_1348();

   public static void drawRect(class_4587 matrixStack, float x, float y, float width, float height, Color color) {
      setupRender();
      drawRectWithoutSetup(matrixStack, x, y, width, height, color);
      endRender();
   }

   public static void drawCenteredAnimatedText(class_332 context, String text, float x, float y, float animProgress, Color textColor) {
      drawCenteredAnimatedText(context, text, x, y, animProgress, textColor, 1.0F);
   }

   public static void drawCenteredAnimatedText(class_332 context, String text, float x, float y, float animProgress, Color textColor, float scale) {
      class_4587 matrixStack = context.method_51448();
      matrixStack.method_22903();
      float f = scale * animProgress;
      matrixStack.method_22905(f, f, 1.0F);
      FontRenderers.mainFont.drawCenteredString(matrixStack, text, (double)(x / f), (double)(y / f), textColor.getRGB());
      matrixStack.method_22909();
   }

   public static void drawAnimatedText(class_332 context, String text, float x, float y, float animProgress, float scale, Color textColor) {
      class_4587 matrixStack = context.method_51448();
      matrixStack.method_22903();
      float f = scale * animProgress;
      float centerX = x + FontRenderers.mainFont.getStringWidth(text) / 2.0F;
      float centerY = y + FontRenderers.mainFont.getFontHeight(text) / 2.0F;
      matrixStack.method_46416(centerX, centerY, 0.0F);
      matrixStack.method_22905(f, f, 1.0F);
      matrixStack.method_46416(-centerX, -centerY, 0.0F);
      FontRenderers.mainFont.drawString(context.method_51448(), text, (double)x, (double)y, textColor.getRGB());
      matrixStack.method_22909();
   }

   public static void drawRectWithoutSetup(class_4587 matrixStack, float x, float y, float width, float height, Color color) {
      float r = (float)color.getRed() / 255.0F;
      float g = (float)color.getGreen() / 255.0F;
      float b = (float)color.getBlue() / 255.0F;
      float a = (float)color.getAlpha() / 255.0F;
      RenderSystem.setShader(class_10142.field_53876);
      Matrix4f matrix4f = matrixStack.method_23760().method_23761();
      class_287 bufferBuilder = tessellator.method_60827(class_5596.field_27382, class_290.field_1576);
      bufferBuilder.method_22918(matrix4f, x, y, 0.0F).method_22915(r, g, b, a);
      bufferBuilder.method_22918(matrix4f, x, y + height, 0.0F).method_22915(r, g, b, a);
      bufferBuilder.method_22918(matrix4f, x + width, y + height, 0.0F).method_22915(r, g, b, a);
      bufferBuilder.method_22918(matrix4f, x + width, y, 0.0F).method_22915(r, g, b, a);
      class_286.method_43433(bufferBuilder.method_60800());
   }

   public static void drawTexture(class_332 context, float x, float y, float width, float height, float u, float v, float regionWidth, float regionHeight, float textureWidth, float textureHeight, class_2960 texture) {
      Matrix4f matrix4f = context.method_51448().method_23760().method_23761();
      setupRender();
      class_4588 vertexConsumer = class_310.method_1551().method_22940().method_23000().getBuffer(class_1921.method_62277(texture));
      vertexConsumer.method_22918(matrix4f, x, y, 0.0F).method_22913(u / textureHeight, v / textureHeight).method_39415(-1);
      vertexConsumer.method_22918(matrix4f, x, y + height, 0.0F).method_22913(u / textureWidth, (v + regionHeight) / textureHeight).method_39415(-1);
      vertexConsumer.method_22918(matrix4f, x + width, y + height, 0.0F).method_22913((u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight).method_39415(-1);
      vertexConsumer.method_22918(matrix4f, x + width, y, 0.0F).method_22913((u + regionWidth) / textureWidth, v / textureHeight).method_39415(-1);
      endRender();
   }

   public static void drawTexture(class_4587 matrixStack, float x, float y, float width, float height, float u, float v, float regionWidth, float regionHeight, float textureWidth, float textureHeight, int texture) {
      Matrix4f matrix4f = matrixStack.method_23760().method_23761();
      setupRender();
      RenderSystem.setShaderTexture(0, texture);
      class_289 tessellator = class_289.method_1348();
      class_287 bufferBuilder = tessellator.method_60827(class_5596.field_27382, class_290.field_1585);
      bufferBuilder.method_22918(matrix4f, x, y, 0.0F).method_22913(u / textureHeight, v / textureHeight);
      bufferBuilder.method_22918(matrix4f, x, y + height, 0.0F).method_22913(u / textureWidth, (v + regionHeight) / textureHeight);
      bufferBuilder.method_22918(matrix4f, x + width, y + height, 0.0F).method_22913((u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight);
      bufferBuilder.method_22918(matrix4f, x + width, y, 0.0F).method_22913((u + regionWidth) / textureWidth, v / textureHeight);
      class_286.method_43433(bufferBuilder.method_60800());
      endRender();
   }

   public static void drawArc(class_4587 matrixStack, float x, float y, float width, float height, float radius, float arcWidth, Color color) {
      setupRender();
      MyShaders.ARC_SHADER.bind();
      MyShaders.ARC_SHADER.setUniforms(x, y, width, height, radius, arcWidth, color);
      drawShader(matrixStack, x, y, width, height);
      endRender();
   }

   private static void drawShader(class_4587 matrixStack, float x, float y, float width, float height) {
      class_287 bufferBuilder = tessellator.method_60827(class_5596.field_27382, class_290.field_1592);
      Matrix4f matrix4f = matrixStack.method_23760().method_23761();
      bufferBuilder.method_22918(matrix4f, x, y + height, 0.0F);
      bufferBuilder.method_22918(matrix4f, x + width, y + height, 0.0F);
      bufferBuilder.method_22918(matrix4f, x + width, y, 0.0F);
      bufferBuilder.method_22918(matrix4f, x, y, 0.0F);
      class_286.method_43433(bufferBuilder.method_60800());
   }

   public static void drawCircle(class_4587 matrixStack, float x, float y, float radius, Color color) {
      setupRender();
      MyShaders.CIRCLE_SHADER.bind();
      MyShaders.CIRCLE_SHADER.setUniforms(x, y, radius, color);
      class_287 bufferBuilder = tessellator.method_60827(class_5596.field_27382, class_290.field_1592);
      Matrix4f matrix4f = matrixStack.method_23760().method_23761();
      bufferBuilder.method_22918(matrix4f, x - radius, y + radius, 0.0F);
      bufferBuilder.method_22918(matrix4f, x + radius, y + radius, 0.0F);
      bufferBuilder.method_22918(matrix4f, x + radius, y - radius, 0.0F);
      bufferBuilder.method_22918(matrix4f, x - radius, y - radius, 0.0F);
      class_286.method_43433(bufferBuilder.method_60800());
      endRender();
   }

   public static void drawRoundedGradientRect(class_4587 matrixStack, float x, float y, float width, float height, float radius, Color startColor, Color endColor, int dir) {
      setupRender();
      MyShaders.GRADIENT_RECTANGLE_SHADER.bind();
      MyShaders.GRADIENT_RECTANGLE_SHADER.setUniforms(x, y, width, height, radius, dir, startColor, endColor);
      drawShader(matrixStack, x, y, width, height);
      endRender();
   }

   public static void drawRoundedRect(class_4587 matrixStack, float x, float y, float width, float height, float radius, Color color) {
      setupRender();
      drawRoundedRectWithoutSetup(matrixStack, x, y, width, height, radius, color);
      endRender();
   }

   public static void drawRoundedRectWithoutSetup(class_4587 matrixStack, float x, float y, float width, float height, float radius, Color color) {
      MyShaders.RECTANGLE_SHADER.bind();
      MyShaders.RECTANGLE_SHADER.setUniforms(x, y, width, height, radius, color);
      drawShader(matrixStack, x, y, width, height);
   }

   private static void endRender() {
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void setupRender() {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
