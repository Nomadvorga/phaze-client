package me.zyouime.hitcolor.mixin;

import me.zyouime.hitcolor.util.overlay.OverlayRendered;
import net.minecraft.class_10197;
import net.minecraft.class_4073;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({class_4073.class})
public class HorseArmorFeatureRendererMixin implements OverlayRendered {
   @Shadow
   @Final
   private class_10197 field_54182;

   public void setOverlay(int coords) {
      ((OverlayRendered)this.field_54182).setOverlay(coords);
   }
}
