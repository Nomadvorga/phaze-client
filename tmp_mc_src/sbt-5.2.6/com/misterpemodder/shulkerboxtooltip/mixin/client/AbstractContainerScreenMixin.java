package com.misterpemodder.shulkerboxtooltip.mixin.client;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltipClient;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.impl.hook.ContainerScreenDrawTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.hook.ContainerScreenLockTooltip;
import com.misterpemodder.shulkerboxtooltip.impl.hook.GuiGraphicsExtensions;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_465;
import net.minecraft.class_5632;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_465.class})
public class AbstractContainerScreenMixin implements ContainerScreenLockTooltip {
   @Shadow
   @Nullable
   protected class_1735 field_2787;
   @Final
   @Shadow
   protected class_1703 field_2797;
   @Unique
   @Nullable
   private class_1735 mouseLockSlot = null;
   @Unique
   private int mouseLockX = 0;
   @Unique
   private int mouseLockY = 0;

   @Shadow
   protected List<class_2561> method_51454(class_1799 stack) {
      return null;
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z"},
      cancellable = true
   )
   private void forceFocusSlot(class_1735 slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
      if (this.mouseLockSlot != null) {
         if (this.mouseLockSlot.method_7681() && this.field_2797.field_7761.contains(this.mouseLockSlot)) {
            cir.setReturnValue(slot == this.mouseLockSlot && this.field_2797.method_34255().method_7960());
         } else {
            this.mouseLockSlot = null;
         }
      }

   }

   @Inject(
      at = {@At("HEAD")},
      method = {"render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"}
   )
   private void captureMousePosition(class_332 graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      GuiGraphicsExtensions extensions = (GuiGraphicsExtensions)graphics;
      extensions.setMouseY(mouseY);
      extensions.setMouseX(mouseX);
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"}
   )
   private void enableLockKeyHints(CallbackInfo ci) {
      ShulkerBoxTooltipClient.setLockKeyHintsEnabled(true);
   }

   @Inject(
      at = {@At("RETURN")},
      method = {"renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"}
   )
   private void disableLockKeyHints(CallbackInfo ci) {
      ShulkerBoxTooltipClient.setLockKeyHintsEnabled(false);
   }

   public void shulkerboxtooltip$lockTooltipPosition(class_332 graphics, class_327 font, List<class_2561> text, Optional<class_5632> data, class_1799 stack, int x, int y, class_2960 backgroundTexture) {
      class_1735 mouseLockSlot = this.mouseLockSlot;
      if (ShulkerBoxTooltipClient.isLockPreviewKeyPressed()) {
         if (mouseLockSlot == null) {
            mouseLockSlot = this.field_2787;
            this.mouseLockX = x;
            this.mouseLockY = y;
         }
      } else {
         mouseLockSlot = null;
      }

      if (mouseLockSlot != null) {
         class_1799 mouseStack = mouseLockSlot.method_7677();
         PreviewContext context = PreviewContext.builder(mouseStack).withOwner(ShulkerBoxTooltipClient.client == null ? null : ShulkerBoxTooltipClient.client.field_1724).build();
         if (ShulkerBoxTooltipApi.isPreviewAvailable(context)) {
            text = this.method_51454(mouseStack);
            data = mouseStack.method_32347();
            stack = mouseStack;
            x = this.mouseLockX;
            y = this.mouseLockY;
         } else {
            mouseLockSlot = null;
         }
      }

      this.mouseLockSlot = mouseLockSlot;
      ContainerScreenDrawTooltip self = (ContainerScreenDrawTooltip)this;
      self.shulkerboxtooltip$renderTooltip(graphics, font, text, data, stack, x, y, backgroundTexture);
   }
}
