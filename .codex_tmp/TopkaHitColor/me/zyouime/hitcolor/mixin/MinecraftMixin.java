package me.zyouime.hitcolor.mixin;

import me.zyouime.hitcolor.render.font.FontRenderers;
import net.minecraft.class_1041;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_310.class})
public abstract class MinecraftMixin {
   @Shadow
   public abstract class_1041 method_22683();

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void init(CallbackInfo info) {
      FontRenderers.init(this.method_22683().method_4495());
   }
}
