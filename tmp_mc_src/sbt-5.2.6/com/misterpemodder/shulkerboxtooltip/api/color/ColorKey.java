package com.misterpemodder.shulkerboxtooltip.api.color;

import com.misterpemodder.shulkerboxtooltip.impl.color.ColorKeyImpl;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1767;
import org.jetbrains.annotations.ApiStatus.NonExtendable;

@NonExtendable
@Environment(EnvType.CLIENT)
public interface ColorKey {
   ColorKey DEFAULT = ofRgb(16777215);
   ColorKey ENDER_CHEST = ofRgb(740161);
   ColorKey SHULKER_BOX = ofRgb(9922455);
   ColorKey WHITE_SHULKER_BOX = ofDye(class_1767.field_7952);
   ColorKey ORANGE_SHULKER_BOX = ofDye(class_1767.field_7946);
   ColorKey MAGENTA_SHULKER_BOX = ofDye(class_1767.field_7958);
   ColorKey LIGHT_BLUE_SHULKER_BOX = ofDye(class_1767.field_7951);
   ColorKey YELLOW_SHULKER_BOX = ofDye(class_1767.field_7947);
   ColorKey LIME_SHULKER_BOX = ofDye(class_1767.field_7961);
   ColorKey PINK_SHULKER_BOX = ofDye(class_1767.field_7954);
   ColorKey GRAY_SHULKER_BOX = ofDye(class_1767.field_7944);
   ColorKey LIGHT_GRAY_SHULKER_BOX = ofDye(class_1767.field_7967);
   ColorKey CYAN_SHULKER_BOX = ofDye(class_1767.field_7955);
   ColorKey PURPLE_SHULKER_BOX = ofDye(class_1767.field_7945);
   ColorKey BLUE_SHULKER_BOX = ofDye(class_1767.field_7966);
   ColorKey BROWN_SHULKER_BOX = ofDye(class_1767.field_7957);
   ColorKey GREEN_SHULKER_BOX = ofDye(class_1767.field_7942);
   ColorKey RED_SHULKER_BOX = ofDye(class_1767.field_7964);
   ColorKey BLACK_SHULKER_BOX = ofDye(class_1767.field_7963);

   int rgb();

   float[] rgbComponents();

   int defaultRgb();

   float[] defaultRgbComponents();

   void setRgb(int var1);

   void setRgb(float[] var1);

   static ColorKey copyOf(ColorKey original) {
      return ofRgb(original.rgbComponents());
   }

   static ColorKey ofRgb(float[] rgb) {
      return new ColorKeyImpl(new float[]{rgb[0], rgb[1], rgb[2]}, new float[]{rgb[0], rgb[1], rgb[2]});
   }

   static ColorKey ofRgb(int rgb) {
      float[] components = ShulkerBoxTooltipUtil.rgbToComponents(rgb);
      return new ColorKeyImpl(components, new float[]{components[0], components[1], components[2]});
   }

   private static ColorKey ofDye(class_1767 dye) {
      int color = dye.method_7787();
      float[] clamped = new float[]{Math.max(0.15F, (float)(color >> 16 & 255) / 255.0F), Math.max(0.15F, (float)(color >> 8 & 255) / 255.0F), Math.max(0.15F, (float)(color & 255) / 255.0F)};
      return new ColorKeyImpl(Arrays.copyOf(clamped, 3), clamped);
   }
}
