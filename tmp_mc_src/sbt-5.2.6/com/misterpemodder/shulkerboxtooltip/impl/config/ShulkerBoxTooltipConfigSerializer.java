package com.misterpemodder.shulkerboxtooltip.impl.config;

import com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.PluginManager;
import com.misterpemodder.shulkerboxtooltip.impl.color.ColorRegistryImpl;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import com.misterpemodder.shulkerboxtooltip.impl.util.Key;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.Jankson;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonElement;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonObject;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonPrimitive;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.Marshaller;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.SyntaxError;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2960;

public final class ShulkerBoxTooltipConfigSerializer {
   private final Jankson jankson = this.buildJankson();
   private static final String CONFIG_FILE_NAME = "shulkerboxtooltip.json5";

   private Jankson buildJankson() {
      Jankson.Builder builder = Jankson.builder();
      if (EnvironmentUtil.isClient()) {
         ShulkerBoxTooltipConfigSerializer.ClientOnly.buildJankson(builder);
      }

      return builder.build();
   }

   public void serialize(Configuration config) throws SerializationException {
      Path configPath = this.getConfigPath();
      ShulkerBoxTooltip.LOGGER.debug("Saving configuration to " + String.valueOf(configPath));

      try {
         Files.createDirectories(configPath.getParent());
      } catch (IOException var5) {
      }

      if (EnvironmentUtil.isClient() && !PluginManager.areColorsLoaded()) {
         ShulkerBoxTooltip.LOGGER.debug("Configuration is not fully loaded, not saving");
      } else {
         try {
            BufferedWriter writer = Files.newBufferedWriter(configPath);
            writer.write(this.jankson.toJson(config).toJson(true, true));
            writer.close();
            ShulkerBoxTooltip.LOGGER.debug("Configuration saved successfully");
         } catch (IOException e) {
            throw new SerializationException(e);
         }
      }
   }

   public Configuration deserialize() throws SerializationException {
      Path configPath = this.getConfigPath();
      if (Files.exists(configPath, new LinkOption[0])) {
         try {
            JsonObject obj = this.jankson.load(configPath.toFile());
            Configuration config = (Configuration)this.jankson.fromJson(obj, EnvironmentUtil.getInstance().getConfigurationClass());
            if (config == null) {
               throw new SerializationException("Failed to deserialize configuration");
            } else {
               return config;
            }
         } catch (SyntaxError | IOException e) {
            throw new SerializationException(e);
         }
      } else {
         ShulkerBoxTooltip.LOGGER.info("Could not find configuration file, creating default file");
         return EnvironmentUtil.getInstance().makeConfiguration();
      }
   }

   private Path getConfigPath() {
      return ShulkerBoxTooltip.getConfigDir().resolve("shulkerboxtooltip.json5");
   }

   @Environment(EnvType.CLIENT)
   private static final class ClientOnly {
      private static void buildJankson(Jankson.Builder builder) {
         builder.registerDeserializer(String.class, Key.class, (str, marshaller) -> Key.fromTranslationKey(str));
         builder.registerDeserializer(JsonObject.class, Key.class, (obj, marshaller) -> Key.fromTranslationKey((String)obj.get(String.class, "code")));
         builder.registerSerializer(Key.class, (key, marshaller) -> {
            JsonObject object = new JsonObject();
            object.put((String)"code", (JsonElement)(new JsonPrimitive(key.get().method_1441())));
            return object;
         });
         builder.registerDeserializer(JsonObject.class, ColorRegistry.class, ClientOnly::deserializeColorRegistry);
         builder.registerSerializer(ColorRegistry.class, ClientOnly::serializeColorRegistry);
      }

      private static ColorRegistry deserializeColorRegistry(JsonObject obj, Marshaller marshaller) {
         for(Map.Entry<String, JsonElement> categoryEntry : obj.entrySet()) {
            class_2960 categoryId = class_2960.method_12829((String)categoryEntry.getKey());
            if (categoryId != null) {
               Object var6 = categoryEntry.getValue();
               if (var6 instanceof JsonObject) {
                  JsonObject categoryObject = (JsonObject)var6;
                  deserializeColorCategory(categoryId, categoryObject);
               }
            }
         }

         return ColorRegistryImpl.INSTANCE;
      }

      private static JsonObject serializeColorRegistry(ColorRegistry registry, Marshaller marshaller) {
         JsonObject object = new JsonObject();

         for(Map.Entry<class_2960, ColorRegistry.Category> categoryEntry : registry.categories().entrySet()) {
            JsonObject categoryObject = new JsonObject();

            for(Map.Entry<String, ColorKey> keyEntry : ((ColorRegistry.Category)categoryEntry.getValue()).keys().entrySet()) {
               categoryObject.put((String)((String)keyEntry.getKey()), (JsonElement)(new JsonHexadecimalInt(((ColorKey)keyEntry.getValue()).rgb())));
               categoryObject.setComment((String)keyEntry.getKey(), String.format("(default value: %#x)", ((ColorKey)keyEntry.getValue()).defaultRgb()));
            }

            object.put((String)((class_2960)categoryEntry.getKey()).toString(), (JsonElement)categoryObject);
         }

         return object;
      }

      private static void deserializeColorCategory(class_2960 id, JsonObject object) {
         ColorRegistryImpl.Category category = ColorRegistryImpl.INSTANCE.category(id);

         for(Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Object var6 = entry.getValue();
            if (var6 instanceof JsonPrimitive value) {
               ColorKey key = category.key((String)entry.getKey());
               long rgbValue = value.asLong(Long.MIN_VALUE);
               boolean isValidValue = rgbValue >= -2147483648L && rgbValue <= 2147483647L;
               if (key != null) {
                  if (isValidValue) {
                     key.setRgb((int)rgbValue);
                  } else {
                     key.setRgb(key.defaultRgb());
                  }
               } else if (isValidValue) {
                  category.setRgbKeyLater((String)entry.getKey(), (int)rgbValue);
               }
            }
         }

      }
   }
}
