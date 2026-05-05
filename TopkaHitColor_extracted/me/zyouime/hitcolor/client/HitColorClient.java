package me.zyouime.hitcolor.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.Generated;
import me.zyouime.hitcolor.render.shader.AbstractShader;
import me.zyouime.hitcolor.render.shader.MyShaders;
import me.zyouime.hitcolor.screen.SettingScreen;
import me.zyouime.hitcolor.setting.Settings;
import me.zyouime.hitcolor.util.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.class_2960;
import net.minecraft.class_3264;
import net.minecraft.class_3300;

public class HitColorClient implements ClientModInitializer {
   private static HitColorClient instance;
   public Settings settings;

   public void onInitializeClient() {
      instance = this;
      this.settings = new Settings();
      this.settings.initSettings();
      KeyBindings.register();
      this.registerEvents();
   }

   private void registerEvents() {
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         if (KeyBindings.openGui.method_1436()) {
            client.method_1507(new SettingScreen(client.field_1755));
         }

      });
      ResourceManagerHelper.get(class_3264.field_14188).registerReloadListener(new SimpleResourceReloadListener<Void>() {
         public class_2960 getFabricId() {
            return class_2960.method_60655("hitcolor", "reload");
         }

         public CompletableFuture<Void> load(class_3300 manager, Executor executor) {
            return CompletableFuture.completedFuture((Object)null);
         }

         public CompletableFuture<Void> apply(Void data, class_3300 manager, Executor executor) {
            return CompletableFuture.runAsync(() -> MyShaders.shaders.forEach(AbstractShader::reload), executor);
         }
      });
   }

   @Generated
   public static HitColorClient getInstance() {
      return instance;
   }
}
