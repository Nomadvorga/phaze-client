package me.zyouime.hitcolor.util;

import java.awt.Color;

public class ColorHelper {
   public static Color injectAlpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)Math.max(0.0F, Math.min(255.0F, alpha)));
   }
}
