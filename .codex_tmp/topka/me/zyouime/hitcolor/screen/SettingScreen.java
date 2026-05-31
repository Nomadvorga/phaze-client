package me.zyouime.hitcolor.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import me.zyouime.hitcolor.client.HitColorClient;
import me.zyouime.hitcolor.render.RenderHelper;
import me.zyouime.hitcolor.render.animation.OutBack;
import me.zyouime.hitcolor.render.animation.WidgetAnim;
import me.zyouime.hitcolor.screen.widget.api.AbstractWidget;
import me.zyouime.hitcolor.screen.widget.impl.ColorPickerWidget;
import me.zyouime.hitcolor.screen.widget.impl.ToggleWidget;
import me.zyouime.hitcolor.setting.Setting;
import me.zyouime.hitcolor.util.overlay.OverlayReloadListener;
import net.minecraft.class_1041;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_4587;

public class SettingScreen extends class_437 {
   private float scaledCenterX;
   private float scaledCenterY;
   private final List<AbstractWidget> widgets = new ArrayList();
   private final OutBack backAnim = new OutBack();
   private final class_437 parent;

   public SettingScreen(class_437 parent) {
      super(class_2561.method_30163(""));
      this.parent = parent;
   }

   protected void method_25426() {
      class_1041 window = class_310.method_1551().method_22683();
      this.scaledCenterX = (float)window.method_4486() / 2.0F;
      this.scaledCenterY = (float)window.method_4502() / 2.0F;
      ColorPickerWidget colorPicker = new ColorPickerWidget(this.scaledCenterX - 84.0F, this.scaledCenterY - 87.0F, 160.0F, 80.0F, HitColorClient.getInstance().settings.overlayColor, true);
      ToggleWidget showDamageInArmor = new ToggleWidget(this.scaledCenterX - 70.0F, this.scaledCenterY + 26.0F, 180.0F, 10.0F, HitColorClient.getInstance().settings.armorOverlay, (press) -> {
         Setting<Boolean> setting = HitColorClient.getInstance().settings.armorOverlay;
         setting.setValue(!(Boolean)setting.getValue());
      }, Color.GREEN, "Показывать урон на броне");
      this.widgets.add(colorPicker);
      this.widgets.add(showDamageInArmor);
   }

   public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
      this.method_25420(context, mouseX, mouseY, delta);
      this.backAnim.update();
      class_4587 matrixStack = context.method_51448();
      float progress = (float)this.backAnim.getAnimationd();
      float backX = this.scaledCenterX - 85.0F;
      float backY = this.scaledCenterY - 95.0F;
      float backW = 205.0F;
      float backH = 140.0F;
      WidgetAnim anim = WidgetAnim.getAnim(backX, backY, backW, backH, 8.0F, progress);
      RenderHelper.drawRoundedRect(matrixStack, anim.x(), anim.y(), anim.width(), anim.height(), anim.radius(), new Color(0, 0, 0, 128));
      if (progress == 1.0F) {
         for(AbstractWidget widget : this.widgets) {
            widget.updateAnim();
            widget.method_25394(context, mouseX, mouseY, delta);
         }
      }

   }

   protected void method_57736(class_332 context, int x, int y, int width, int height) {
   }

   public boolean method_25402(double mouseX, double mouseY, int button) {
      for(AbstractWidget widget : this.widgets) {
         if (widget.method_25402(mouseX, mouseY, button)) {
            return true;
         }
      }

      return super.method_25402(mouseX, mouseY, button);
   }

   public boolean method_25406(double mouseX, double mouseY, int button) {
      for(AbstractWidget widget : this.widgets) {
         if (widget.method_25406(mouseX, mouseY, button)) {
            return true;
         }
      }

      return super.method_25406(mouseX, mouseY, button);
   }

   public boolean method_25403(double mouseX, double mouseY, int button, double dragX, double dragY) {
      for(AbstractWidget widget : this.widgets) {
         if (widget.method_25403(mouseX, mouseY, button, dragX, dragY)) {
            return true;
         }
      }

      return super.method_25403(mouseX, mouseY, button, dragX, dragY);
   }

   public void method_25410(class_310 client, int width, int height) {
      this.widgets.clear();
      super.method_25410(client, width, height);
   }

   public void method_25419() {
      HitColorClient.getInstance().settings.settingsList.forEach(Setting::save);
      OverlayReloadListener.event();
      this.field_22787.method_1507(this.parent);
   }
}
