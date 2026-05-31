package me.zyouime.hitcolor.util;

import me.zyouime.hitcolor.render.font.FontRenderer;
import me.zyouime.hitcolor.render.font.FontRenderers;

public class Util {
   public static String substringToWidth(FontRenderer renderer, String s, float width) {
      char[] chars = s.toCharArray();
      StringBuilder builder = new StringBuilder();

      for(char c : chars) {
         if (!(renderer.getStringWidth(builder.toString()) < width)) {
            builder.append("...");
            break;
         }

         builder.append(c);
      }

      return builder.toString();
   }

   public static String substringToWidth(String s, float width) {
      return substringToWidth(FontRenderers.mainFont, s, width);
   }
}
