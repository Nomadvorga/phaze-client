package me.zyouime.hitcolor.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import me.zyouime.hitcolor.client.HitColorClient;
import me.zyouime.hitcolor.util.overlay.OverlayReloadListener;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_4608;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_4608.class})
public class OverlayTextureMixin implements OverlayReloadListener {
   @Shadow
   @Final
   private class_1043 field_21013;

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      this.setColor();
      OverlayReloadListener.registerOverlay(this);
   }

   @Unique
   private static int getColorInt(int red, int green, int blue, int alpha) {
      alpha = 255 - alpha;
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   public void setColor() {
      class_1011 nativeImage = this.field_21013.method_4525();

      for(int i = 0; i < 16; ++i) {
         for(int j = 0; j < 16; ++j) {
            if (i < 8) {
               Color color = HitColorClient.getInstance().settings.overlayColor.getValue();
               nativeImage.method_61941(j, i, getColorInt(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
            }
         }
      }

      RenderSystem.activeTexture(33985);
      this.field_21013.method_23207();
      this.field_21013.method_4527(false, false);
      this.field_21013.method_65924(true);
      nativeImage.method_22619(0, 0, 0, 0, 0, nativeImage.method_4307(), nativeImage.method_4323(), false);
      RenderSystem.activeTexture(33984);
   }
}
