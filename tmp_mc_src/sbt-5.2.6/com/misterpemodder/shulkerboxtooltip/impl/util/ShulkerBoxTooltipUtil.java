package com.misterpemodder.shulkerboxtooltip.impl.util;

import net.minecraft.class_2960;

public final class ShulkerBoxTooltipUtil {
   private ShulkerBoxTooltipUtil() {
   }

   public static class_2960 id(String id) {
      return class_2960.method_60655("shulkerboxtooltip", id);
   }

   public static String abbreviateInteger(int count) {
      if (count == Integer.MIN_VALUE) {
         return "-2G";
      } else if (count > -1000 && count < 1000) {
         return Integer.toString(count);
      } else {
         StringBuilder str = new StringBuilder();
         if (count < 0) {
            str.append('-');
            count = -count;
         }

         int decimal = 0;
         char unit;
         int integral;
         switch ((int)Math.log10((double)count)) {
            case 3:
               integral = count / 1000;
               decimal = count % 1000 / 100;
               unit = 'k';
               break;
            case 4:
            case 5:
               integral = count / 1000;
               unit = 'k';
               break;
            case 6:
               integral = count / 1000000;
               decimal = count % 1000000 / 100000;
               unit = 'M';
               break;
            case 7:
            case 8:
               integral = count / 1000000;
               unit = 'M';
               break;
            default:
               integral = count / 1000000000;
               decimal = count % 1000000000 / 100000000;
               unit = 'G';
         }

         str.append(integral);
         if (decimal > 0) {
            str.append('.').append(decimal);
         }

         str.append(unit);
         return str.toString();
      }
   }

   public static float[] rgbToComponents(int rgb) {
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      return new float[]{(float)r / 255.0F, (float)g / 255.0F, (float)b / 255.0F};
   }

   public static int componentsToRgb(float[] components) {
      int r = (int)(255.0F * components[0]);
      int g = (int)(255.0F * components[1]);
      int b = (int)(255.0F * components[2]);
      return r << 16 | g << 8 | b;
   }

   public static String snakeCase(String str) {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < str.length(); ++i) {
         char c = str.charAt(i);
         if (Character.isUpperCase(c)) {
            if (i > 0) {
               sb.append('_');
            }

            sb.append(Character.toLowerCase(c));
         } else {
            sb.append(c);
         }
      }

      return sb.toString();
   }
}
