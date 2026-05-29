package com.misterpemodder.shulkerboxtooltip.mixin.client.fabric;

import com.misterpemodder.shulkerboxtooltip.impl.hook.ContainerScreenDrawTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.hook.ContainerScreenLockTooltip;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_465;
import net.minecraft.class_5632;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({class_465.class})
public class AbstractContainerScreenMixin implements ContainerScreenDrawTooltip {
   @Shadow
   @Nullable
   protected class_1735 field_2787;

   @Redirect(
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/ResourceLocation;)V"
),
      method = {"renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"}
   )
   private void lockTooltipPosition(class_332 drawContext, class_327 font, List<class_2561> text, Optional<class_5632> data, int x, int y, class_2960 backgroundTexture) {
      class_1799 stack = this.field_2787 == null ? null : this.field_2787.method_7677();
      ContainerScreenLockTooltip self = (ContainerScreenLockTooltip)this;
      self.shulkerboxtooltip$lockTooltipPosition(drawContext, font, text, data, stack, x, y, backgroundTexture);
   }

   public void shulkerboxtooltip$renderTooltip(@Nonnull class_332 graphics, class_327 font, List<class_2561> text, Optional<class_5632> data, class_1799 stack, int x, int y, class_2960 backgroundTexture) {
      graphics.method_51437(font, text, data, x, y, backgroundTexture);
   }
}
