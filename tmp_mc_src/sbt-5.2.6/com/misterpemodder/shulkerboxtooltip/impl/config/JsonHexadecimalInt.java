package com.misterpemodder.shulkerboxtooltip.impl.config;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonGrammar;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonPrimitive;

public class JsonHexadecimalInt extends JsonPrimitive {
   JsonHexadecimalInt(int value) {
      super(value);
   }

   public String toJson(JsonGrammar grammar, int depth) {
      return String.format("%#x", ((Number)this.getValue()).intValue());
   }
}
