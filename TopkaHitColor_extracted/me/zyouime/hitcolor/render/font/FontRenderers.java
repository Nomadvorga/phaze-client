package me.zyouime.hitcolor.render.font;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import me.zyouime.hitcolor.client.HitColorClient;
import org.jetbrains.annotations.NotNull;

public class FontRenderers {
   public static FontRenderer mainFont;

   private static @NotNull FontRenderer create(String font, float size) throws IOException, FontFormatException {
      return new FontRenderer(Font.createFont(0, (InputStream)Objects.requireNonNull(HitColorClient.class.getClassLoader().getResourceAsStream("assets/hitcolor/fonts/" + font + ".ttf"))).deriveFont(0, size / 2.0F), size / 2.0F);
   }

   public static void init(double scaleFactor) {
      try {
         mainFont = create("sf_medium", FontRenderer.getSizeToScale(16.0F, (int)scaleFactor));
      } catch (FontFormatException | IOException e) {
         throw new RuntimeException(e);
      }
   }
}
