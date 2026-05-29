package io.github.imurx.screenshotcopy;

import io.github.imurx.arboard.Clipboard;
import io.github.imurx.arboard.ImageData;
import io.github.imurx.screenshotcopy.mixins.NativeImageInvoker;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.class_1011;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenshotCopy {
   public static final String MOD_ID = "screencopy";
   private static final Logger LOGGER = LoggerFactory.getLogger("Screencopy");
   private static Clipboard clipboard;

   public static void init() {
      if (clipboard != null) {
         LOGGER.warn("Someone tried to init me again", new IllegalStateException("Clipboard is already defined, can't init it again"));
      } else {
         clipboard = new Clipboard();
      }
   }

   public static void stop() {
      clipboard.close();
      clipboard = null;
   }

   public static void copyScreenshot(class_1011 image) {
      NativeImageInvoker invoker = (NativeImageInvoker)image;
      ByteBuffer imageBytes = ByteBuffer.allocate(image.method_4307() * image.method_4323() * 4).order(ByteOrder.LITTLE_ENDIAN);

      for(int y = 0; y < image.method_4323(); ++y) {
         for(int x = 0; x < image.method_4307(); ++x) {
            imageBytes.putInt(invoker.invokeGetColor(x, y));
         }
      }

      copyScreenshot(image.method_4307(), image.method_4323(), imageBytes.array());
   }

   public static void copyScreenshot(int width, int height, byte[] array) {
      ImageData data = new ImageData(width, height, array);

      try {
         clipboard.setImage(data);
      } catch (Throwable var7) {
         try {
            data.close();
         } catch (Throwable var6) {
            var7.addSuppressed(var6);
         }

         throw var7;
      }

      data.close();
   }
}
