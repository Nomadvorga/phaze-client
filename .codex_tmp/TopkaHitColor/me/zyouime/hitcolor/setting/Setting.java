package me.zyouime.hitcolor.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Type;
import lombok.Generated;
import me.zyouime.hitcolor.config.ModConfig;

public class Setting<T> {
   private T value;
   private final T defaultValue;
   private final String configKey;
   private final Type type;

   public Setting(String configKey, Type type, T defaultValue) {
      this.configKey = configKey;
      this.defaultValue = defaultValue;
      this.type = type;
   }

   public void initValue(JsonElement value) {
      this.value = (T)ModConfig.GSON.fromJson(value, this.type);
   }

   public void save() {
      JsonObject object = ModConfig.loadConfig(ModConfig.CONFIG_FILE);
      object.add(this.configKey, ModConfig.GSON.toJsonTree(this.value, this.type));
      ModConfig.saveConfig(object, ModConfig.CONFIG_FILE);
   }

   @Generated
   public T getValue() {
      return this.value;
   }

   @Generated
   public T getDefaultValue() {
      return this.defaultValue;
   }

   @Generated
   public String getConfigKey() {
      return this.configKey;
   }

   @Generated
   public Type getType() {
      return this.type;
   }

   @Generated
   public void setValue(T value) {
      this.value = value;
   }
}
