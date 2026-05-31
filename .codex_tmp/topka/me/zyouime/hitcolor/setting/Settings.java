package me.zyouime.hitcolor.setting;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import me.zyouime.hitcolor.config.ModConfig;

public class Settings {
   public List<Setting<?>> settingsList = new ArrayList();
   public final Setting<Boolean> armorOverlay;
   public final Setting<Color> overlayColor;

   public Settings() {
      this.armorOverlay = this.register(new Setting("armorOverlay", Settings.Types.BOOLEAN, true));
      this.overlayColor = this.register(new Setting("overlayColor", Settings.Types.COLOR, Color.YELLOW));
   }

   public void initSettings() {
      JsonObject config = ModConfig.CONFIG_FILE.exists() ? ModConfig.loadConfig(ModConfig.CONFIG_FILE) : new JsonObject();

      for(Setting<?> setting : this.settingsList) {
         String configKey = setting.getConfigKey();
         if (!config.has(configKey)) {
            config.add(configKey, ModConfig.GSON.toJsonTree(setting.getDefaultValue()));
         }

         setting.initValue(config.get(configKey).deepCopy());
      }

      ModConfig.saveConfig(config, ModConfig.CONFIG_FILE);
   }

   private <T extends Setting<?>> T register(T t) {
      this.settingsList.add(t);
      return t;
   }

   public static class Types {
      public static final Type BOOLEAN = (new TypeToken<Boolean>() {
      }).getType();
      public static final Type COLOR = (new TypeToken<Color>() {
      }).getType();
   }
}
