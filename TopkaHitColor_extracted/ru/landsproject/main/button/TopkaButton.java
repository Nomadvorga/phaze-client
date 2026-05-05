package ru.landsproject.main.button;

import me.zyouime.hitcolor.render.RenderHelper;
import net.minecraft.class_1921;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3532;
import net.minecraft.class_4185;
import net.minecraft.class_4587;
import net.minecraft.class_8666;
import net.minecraft.class_9848;

public class TopkaButton extends class_4185 {
   private final class_2960 buttonLogo = class_2960.method_60655("hitcolor", "textures/topkalogo.png");
   private static final class_8666 TEXTURES = new class_8666(class_2960.method_60656("widget/button"), class_2960.method_60656("widget/button_disabled"), class_2960.method_60656("widget/button_highlighted"));

   public TopkaButton(int x, int y, int width, int height, class_2561 message, class_4185.class_4241 onPress) {
      super(x, y, width, height, message, onPress, field_40754);
   }

   protected void method_48579(class_332 context, int mouseX, int mouseY, float delta) {
      this.renderTopkaButton(context, mouseX, mouseY, delta);
   }

   public void renderTopkaButton(class_332 context, int mouseX, int mouseY, float delta) {
      context.method_52707(class_1921::method_62277, TEXTURES.method_52729(this.field_22763, this.method_25367()), this.method_46426(), this.method_46427(), this.method_25368(), this.method_25364(), class_9848.method_61317(this.field_22765));
      int i = this.field_22763 ? 16777215 : 10526880;
      int stringStartX = this.method_46426() + this.field_22758 / 2;
      class_310 minecraftClient = class_310.method_1551();
      class_4587 matrixStack = context.method_51448();
      matrixStack.method_22903();
      matrixStack.method_46416(-10.0F, 0.0F, 0.0F);
      this.method_48589(context, minecraftClient.field_1772, i | class_3532.method_15386(this.field_22765 * 255.0F) << 24);
      matrixStack.method_22909();
      int imageSize = 12;
      int iconX = stringStartX + minecraftClient.field_1772.method_1727(this.method_25369().getString()) / 2 - 5;
      int iconY = this.method_46427() + (this.field_22759 - imageSize) / 2;
      RenderHelper.drawTexture(context, (float)iconX, (float)iconY, (float)imageSize, (float)imageSize, 0.0F, 0.0F, 32.0F, 32.0F, 32.0F, 32.0F, this.buttonLogo);
   }
}
