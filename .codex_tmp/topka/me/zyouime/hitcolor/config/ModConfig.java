package me.zyouime.hitcolor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import me.zyouime.hitcolor.config.adapter.ColorAdapter;
import net.fabricmc.loader.api.FabricLoader;

public class ModConfig {
   public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "topkahitcolor.json");
   public static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().registerTypeAdapter(Color.class, new ColorAdapter()).create();

   public static JsonObject loadConfig(File configFile) {
      try {
         FileReader fileReader = new FileReader(configFile);

         JsonObject var2;
         try {
            var2 = (JsonObject)GSON.fromJson(fileReader, JsonObject.class);
         } catch (Throwable var5) {
            try {
               fileReader.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }

            throw var5;
         }

         fileReader.close();
         return var2;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static void saveConfig(JsonObject object, File configFile) {
      try {
         FileWriter fileWriter = new FileWriter(configFile);

         try {
            GSON.toJson(object, fileWriter);
         } catch (Throwable var6) {
            try {
               fileWriter.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         fileWriter.close();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}
