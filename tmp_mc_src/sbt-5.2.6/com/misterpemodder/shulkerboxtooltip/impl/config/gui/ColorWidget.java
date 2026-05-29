package com.misterpemodder.shulkerboxtooltip.impl.config.gui;

import java.util.function.IntSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1921;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_6382;
import net.minecraft.class_8666;

@Environment(EnvType.CLIENT)
public class ColorWidget extends class_339 {
   private static final class_8666 SPRITES = new class_8666(class_2960.method_60656("widget/text_field"), class_2960.method_60656("widget/text_field_highlighted"));
   private final class_339 neighbor;
   private final IntSupplier colorSupplier;

   public ColorWidget(class_2561 label, class_339 neighbor, IntSupplier colorSupplier) {
      super(0, 0, 18, 18, label);
      this.neighbor = neighbor;
      this.colorSupplier = colorSupplier;
      this.field_22763 = false;
   }

   protected void method_48579(class_332 guiGraphics, int mouseX, int mouseY, float delta) {
      if (this.field_22764) {
         class_2960 resourceLocation = SPRITES.method_52729(this.method_37303(), this.neighbor.method_25370());
         guiGraphics.method_52706(class_1921::method_62277, resourceLocation, this.method_46426(), this.method_46427(), this.method_25368(), this.method_25364());
         guiGraphics.method_25294(this.method_46426() + 1, this.method_46427() + 1, this.method_46426() + this.method_25368() - 1, this.method_46427() + this.method_25364() - 1, -16777216 | this.colorSupplier.getAsInt());
      }
   }

   protected void method_47399(class_6382 narrationElementOutput) {
   }
}
