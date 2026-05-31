package me.zyouime.hitcolor.config.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.awt.Color;
import java.lang.reflect.Type;

public class ColorAdapter implements JsonDeserializer<Color>, JsonSerializer<Color> {
   public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonArray jsonArray = json.getAsJsonArray();
      return new Color(jsonArray.get(0).getAsInt(), jsonArray.get(1).getAsInt(), jsonArray.get(2).getAsInt(), jsonArray.get(3).getAsInt());
   }

   public JsonElement serialize(Color src, Type typeOfSrc, JsonSerializationContext context) {
      JsonArray jsonArray = new JsonArray();
      jsonArray.add(src.getRed());
      jsonArray.add(src.getGreen());
      jsonArray.add(src.getBlue());
      jsonArray.add(src.getAlpha());
      return jsonArray;
   }
}
