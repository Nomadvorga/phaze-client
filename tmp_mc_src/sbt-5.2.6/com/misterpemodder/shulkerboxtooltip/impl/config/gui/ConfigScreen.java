package com.misterpemodder.shulkerboxtooltip.impl.config.gui;

import com.google.common.collect.UnmodifiableIterator;
import com.misterpemodder.shulkerboxtooltip.impl.PluginManager;
import com.misterpemodder.shulkerboxtooltip.impl.tree.CategoryConfigNode;
import com.misterpemodder.shulkerboxtooltip.impl.tree.RootConfigNode;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1921;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_364;
import net.minecraft.class_410;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_5244;
import net.minecraft.class_8030;
import net.minecraft.class_8087;
import net.minecraft.class_8088;
import net.minecraft.class_8089;
import net.minecraft.class_8132;
import net.minecraft.class_8209;
import net.minecraft.class_8667;

@Environment(EnvType.CLIENT)
public final class ConfigScreen<C> extends class_437 {
   private final RootConfigNode<C> root;
   private final C config;
   private final Consumer<C> onSave;
   private final class_437 previous;
   private final class_8132 layout;
   private final class_8088 tabManager;
   private class_8089 tabNavigationBar;
   private List<ConfigCategoryTab<C>> tabs;
   private class_4185 quitButton;
   private class_4185 saveAndQuitButton;
   private static final class_2561 CANCEL_LABEL;
   private static final class_2561 QUIT_UNSAVED_LABEL;
   private static final class_2561 SAVE_LABEL;
   private static final class_2561 CANNOT_SAVE_LABEL;
   private static final class_2561 QUIT_CONFIRM_LABEL;
   private static final class_2561 QUIT_CONFIRM_TITLE;
   private static final class_2561 QUIT_CONFIRM_WARNING;
   private static final class_2561 RESTART_REQUIRED_LABEL;
   private static final class_2561 RESTART_REQUIRED_TITLE;
   private static final class_2561 EXIT_MINECRAFT_LABEL;
   private static final class_2561 IGNORE_RESTART_LABEL;

   public ConfigScreen(class_437 previous, RootConfigNode<C> root, C config, Consumer<C> onSave) {
      super(root.getTitle());
      PluginManager.loadColors();
      this.root = root;
      this.config = config;
      this.onSave = onSave;
      this.previous = previous;
      this.layout = new class_8132(this, 61, 33);
      this.tabManager = new class_8088((x$0) -> {
         class_339 var10000 = (class_339)this.method_37063(x$0);
      }, (x$0) -> this.method_37066(x$0));
      this.tabs = List.of();
   }

   protected void method_25426() {
      this.root.resetToActive(this.config);
      class_8089.class_8090 tabNavigationBarBuilder = class_8089.method_48623(this.tabManager, this.field_22789);
      this.tabs = new ArrayList();
      UnmodifiableIterator var2 = this.root.getCategories().iterator();

      while(var2.hasNext()) {
         CategoryConfigNode<C> category = (CategoryConfigNode)var2.next();
         ConfigCategoryTab<C> tab = new ConfigCategoryTab<C>(this, category, this.config);
         tabNavigationBarBuilder.method_48631(new class_8087[]{tab});
         this.tabs.add(tab);
      }

      this.tabNavigationBar = tabNavigationBarBuilder.method_48627();
      this.initTabs(this.tabNavigationBar);
      this.method_37063(this.tabNavigationBar);
      class_8667 footerLayout = (class_8667)this.layout.method_48996(class_8667.method_52742().method_52735(8));
      this.quitButton = (class_4185)footerLayout.method_52736(class_4185.method_46430(this.getQuitLabel(), (b) -> this.method_25419()).method_46432(200).method_46431());
      this.saveAndQuitButton = (class_4185)footerLayout.method_52736(class_4185.method_46430(this.getSaveLabel(), (b) -> this.saveAndQuit()).method_46432(200).method_46431());
      this.saveAndQuitButton.field_22763 = !this.root.isActiveValue(this.config) && this.root.validate(this.config) == null;
      this.layout.method_48206((abstractWidget) -> {
         abstractWidget.method_48591(1);
         this.method_37063(abstractWidget);
      });
      this.tabNavigationBar.method_48987(0, false);
      this.method_48640();
   }

   private void initTabs(class_8089 bar) {
      int i = 0;

      for(class_364 child : bar.method_25396()) {
         if (child instanceof class_8209 tabButton) {
            ((ConfigCategoryTab)this.tabs.get(i)).setTabButton(tabButton);
            ++i;
         }
      }

   }

   public void method_25394(class_332 guiGraphics, int i, int j, float f) {
      super.method_25394(guiGraphics, i, j, f);
      RenderSystem.enableBlend();
      guiGraphics.method_25290(class_1921::method_62277, class_437.field_49896, 0, this.field_22790 - this.getFooterHeight() - 2, 0.0F, 0.0F, this.field_22789, 2, 32, 2);
      RenderSystem.disableBlend();
   }

   protected void method_48640() {
      this.refresh();
      if (this.tabNavigationBar != null) {
         this.tabNavigationBar.method_48618(this.field_22789);
         this.tabNavigationBar.method_49613();
         int i = this.tabNavigationBar.method_48202().method_49619();
         class_8030 screenRectangle = new class_8030(0, i, this.field_22789, this.field_22790 - this.layout.method_48994() - i);
         this.tabManager.method_48616(screenRectangle);
         this.layout.method_48995(i);
         this.layout.method_48222();
      }

   }

   public boolean method_25404(int keyCode, int scanCode, int modifiers) {
      return this.tabManager.method_48614() != null && ((ConfigCategoryTab)this.tabManager.method_48614()).keyPressed(keyCode, scanCode) || this.tabNavigationBar.method_48988(keyCode) || super.method_25404(keyCode, scanCode, modifiers);
   }

   public void method_25419() {
      if (this.root.isActiveValue(this.config)) {
         this.getMinecraft().method_1507(this.previous);
      } else {
         this.getMinecraft().method_1507(new class_410((confirmed) -> this.getMinecraft().method_1507((class_437)(confirmed ? this.previous : this)), QUIT_CONFIRM_TITLE, QUIT_CONFIRM_WARNING, QUIT_CONFIRM_LABEL, CANCEL_LABEL));
      }
   }

   public void saveAndQuit() {
      boolean restartRequired = this.root.restartRequired(this.config);
      this.root.writeEditingToConfig(this.config);
      this.onSave.accept(this.config);
      if (restartRequired) {
         this.getMinecraft().method_1507(new class_410((confirmed) -> {
            if (confirmed) {
               this.getMinecraft().method_1592();
            } else {
               this.getMinecraft().method_1507(this.previous);
            }

         }, RESTART_REQUIRED_TITLE, RESTART_REQUIRED_LABEL, EXIT_MINECRAFT_LABEL, IGNORE_RESTART_LABEL));
      } else {
         this.getMinecraft().method_1507(this.previous);
      }

   }

   public class_310 getMinecraft() {
      return (class_310)Objects.requireNonNull(this.field_22787);
   }

   public int getHeaderHeight() {
      return this.layout.method_48998();
   }

   public int getFooterHeight() {
      return this.layout.method_48994();
   }

   public void refresh() {
      this.tabs.forEach(ConfigCategoryTab::refresh);
      this.saveAndQuitButton.field_22763 = !this.root.isActiveValue(this.config) && this.root.validate(this.config) == null;
      this.quitButton.method_25355(this.getQuitLabel());
      this.saveAndQuitButton.method_25355(this.getSaveLabel());
   }

   private class_2561 getQuitLabel() {
      return this.root.isActiveValue(this.config) ? CANCEL_LABEL : QUIT_UNSAVED_LABEL;
   }

   private class_2561 getSaveLabel() {
      return this.root.validate(this.config) == null ? SAVE_LABEL : CANNOT_SAVE_LABEL;
   }

   static {
      CANCEL_LABEL = class_5244.field_24335;
      QUIT_UNSAVED_LABEL = class_2561.method_43471("shulkerboxtooltip.config.quit.unsaved");
      SAVE_LABEL = class_2561.method_43471("shulkerboxtooltip.config.save");
      CANNOT_SAVE_LABEL = class_2561.method_43471("shulkerboxtooltip.config.cannot_save");
      QUIT_CONFIRM_LABEL = class_2561.method_43471("shulkerboxtooltip.config.quit.confirm");
      QUIT_CONFIRM_TITLE = class_2561.method_43471("shulkerboxtooltip.config.quit.confirm.title");
      QUIT_CONFIRM_WARNING = class_2561.method_43471("shulkerboxtooltip.config.quit.confirm.warning");
      RESTART_REQUIRED_LABEL = class_2561.method_43471("shulkerboxtooltip.config.restart_required");
      RESTART_REQUIRED_TITLE = class_2561.method_43471("shulkerboxtooltip.config.restart_required.title");
      EXIT_MINECRAFT_LABEL = class_2561.method_43471("shulkerboxtooltip.config.exit_minecraft");
      IGNORE_RESTART_LABEL = class_2561.method_43471("shulkerboxtooltip.config.ignore_restart");
   }
}
