package me.zyouime.hitcolor.mixin;

import me.zyouime.hitcolor.util.overlay.OverlayRendered;
import net.minecraft.class_10197;
import net.minecraft.class_970;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
   value = {class_970.class},
   priority = 999
)
public class ArmorFeatureRendererMixin implements OverlayRendered {
   @Shadow
   @Final
   private class_10197 field_54183;

   public void setOverlay(int coords) {
      ((OverlayRendered)this.field_54183).setOverlay(coords);
   }
}
