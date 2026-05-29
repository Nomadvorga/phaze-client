package io.github.imurx.screenshotcopy.fabric;

import io.github.imurx.screenshotcopy.ScreencopyConfig;
import io.github.imurx.screenshotcopy.ScreenshotCopy;
import me.ramidzkh.fabrishot.event.FramebufferCaptureCallback;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public class ScreenshotCopyFabric implements ClientModInitializer {
   public void onInitializeClient() {
      AutoConfig.register(ScreencopyConfig.class, Toml4jConfigSerializer::new);
      ScreenshotCopy.init();
      if (FabricLoader.getInstance().isModLoaded("fabrishot")) {
         this.initFabrishot();
      }

      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(_client) -> ScreenshotCopy.stop());
   }

   private void initFabrishot() {
      FramebufferCaptureCallback.EVENT.register((FramebufferCaptureCallback)(dimension, byteBuffer) -> {
         byte[] array = new byte[dimension.height() * dimension.width() * 4];
         int offset = 0;

         for(int i = 0; i < byteBuffer.capacity(); ++i) {
            if (i % 3 == 0 && i != 0) {
               array[i + offset] = -1;
               ++offset;
            }

            array[offset + i] = byteBuffer.get(i);
         }

         try {
            ScreenshotCopy.copyScreenshot(dimension.width(), dimension.height(), array);
            class_310.method_1551().field_1705.method_1743().method_1812(class_2561.method_43471("text.screencopy.success"));
         } catch (Exception ex) {
            class_310.method_1551().field_1705.method_1743().method_1812(class_2561.method_43469("text.screencopy.failure", new Object[]{ex.toString()}));
         }

      });
   }
}
