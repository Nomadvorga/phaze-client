package com.misterpemodder.shulkerboxtooltip.mixin.client;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import net.minecraft.class_309;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_309.class})
public class KeyboardHandlerMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"keyPress(JIIII)V"}
   )
   private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
      if (window == class_310.method_1551().method_22683().method_4490()) {
         ShulkerBoxTooltipClient.updatePreviewKeys();
      }

   }
}
