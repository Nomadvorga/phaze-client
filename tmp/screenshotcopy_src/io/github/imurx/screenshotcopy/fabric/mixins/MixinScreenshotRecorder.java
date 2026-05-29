package io.github.imurx.screenshotcopy.fabric.mixins;

import io.github.imurx.screenshotcopy.ScreencopyConfig;
import io.github.imurx.screenshotcopy.ScreenshotCopy;
import java.io.File;
import java.util.function.Consumer;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.class_1011;
import net.minecraft.class_2561;
import net.minecraft.class_318;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_318.class})
public abstract class MixinScreenshotRecorder {
   @Inject(
      at = {@At("HEAD")},
      method = {"method_1661"},
      cancellable = true
   )
   private static void onInnerScreenshot(class_1011 image, File _file, Consumer<class_2561> messageReceiver, CallbackInfo ci) {
      try {
         ScreenshotCopy.copyScreenshot(image);
         if (!((ScreencopyConfig)AutoConfig.getConfigHolder(ScreencopyConfig.class).getConfig()).saveScreenshot) {
            messageReceiver.accept(class_2561.method_43471("text.screencopy.success"));
            ci.cancel();
         }
      } catch (Exception ex) {
         messageReceiver.accept(class_2561.method_43469("text.screencopy.failure", new Object[]{ex.toString()}));
         if (!((ScreencopyConfig)AutoConfig.getConfigHolder(ScreencopyConfig.class).getConfig()).saveScreenshot) {
            ci.cancel();
         }
      }

   }
}
