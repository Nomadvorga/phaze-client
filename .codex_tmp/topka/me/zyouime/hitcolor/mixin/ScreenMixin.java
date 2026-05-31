package me.zyouime.hitcolor.mixin;

import java.util.List;
import net.minecraft.class_156;
import net.minecraft.class_2561;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_4185;
import net.minecraft.class_433;
import net.minecraft.class_437;
import net.minecraft.class_6379;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.landsproject.main.button.TopkaButton;

@Mixin({class_437.class})
public abstract class ScreenMixin {
   @Shadow
   public int field_22789;
   @Shadow
   @Final
   private List<class_364> field_22786;

   @Shadow
   protected abstract <T extends class_364 & class_4068 & class_6379> T method_37063(T var1);

   @Inject(
      method = {"addDrawableChild"},
      at = {@At("HEAD")}
   )
   private <T extends class_364 & class_4068 & class_6379> void addDrawableChild(T drawableElement, CallbackInfoReturnable<T> cir) {
      class_437 screen = (class_437)this;
      if (drawableElement instanceof class_4185 button) {
         if (screen instanceof class_433) {
            class_2561 buttonMessage = button.method_25369();
            if (buttonMessage.equals(class_2561.method_43471("menu.returnToMenu")) || buttonMessage.equals(class_2561.method_43471("menu.disconnect"))) {
               boolean hasTopkaButton = false;

               for(class_364 child : this.field_22786) {
                  if (child instanceof TopkaButton) {
                     hasTopkaButton = true;
                     break;
                  }
               }

               if (!hasTopkaButton) {
                  this.method_37063(new TopkaButton(this.field_22789 / 2 - 102, button.method_46427(), 204, 20, class_2561.method_30163("Лучшие моды только тут"), (pressedButton) -> class_156.method_668().method_670("https://topkaproduct.com/")));
                  button.method_46419(button.method_46427() + 24);
               }
            }
         }
      }

   }
}
