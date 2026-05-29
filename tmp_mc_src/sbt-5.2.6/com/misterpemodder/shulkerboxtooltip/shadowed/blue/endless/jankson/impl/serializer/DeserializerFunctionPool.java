package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.serializer;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonArray;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonElement;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonGrammar;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonObject;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonPrimitive;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.DeserializationException;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.Marshaller;
import java.util.HashMap;

public class DeserializerFunctionPool<B> {
   private Class<B> targetClass;
   private HashMap<Class<?>, InternalDeserializerFunction<B>> values = new HashMap();

   public DeserializerFunctionPool(Class<B> targetClass) {
      this.targetClass = targetClass;
   }

   public void registerUnsafe(Class<?> sourceClass, InternalDeserializerFunction<B> function) {
      this.values.put(sourceClass, function);
   }

   public InternalDeserializerFunction<B> getFunction(Class<?> sourceClass) {
      return (InternalDeserializerFunction)this.values.get(sourceClass);
   }

   public B apply(JsonElement elem, Marshaller marshaller) throws DeserializationException, FunctionMatchFailedException {
      InternalDeserializerFunction<B> selected = null;
      if (elem instanceof JsonPrimitive) {
         Object obj = ((JsonPrimitive)elem).getValue();
         selected = (InternalDeserializerFunction)this.values.get(obj.getClass());
         if (selected != null) {
            return selected.deserialize(obj, marshaller);
         }

         selected = (InternalDeserializerFunction)this.values.get(JsonPrimitive.class);
         if (selected != null) {
            return selected.deserialize((JsonPrimitive)elem, marshaller);
         }
      } else if (elem instanceof JsonObject) {
         selected = (InternalDeserializerFunction)this.values.get(JsonObject.class);
         if (selected != null) {
            return selected.deserialize((JsonObject)elem, marshaller);
         }
      } else if (elem instanceof JsonArray) {
         selected = (InternalDeserializerFunction)this.values.get(JsonArray.class);
         if (selected != null) {
            return selected.deserialize((JsonArray)elem, marshaller);
         }
      }

      selected = (InternalDeserializerFunction)this.values.get(JsonElement.class);
      if (selected != null) {
         return selected.deserialize(elem, marshaller);
      } else {
         throw new FunctionMatchFailedException("Couldn't find a deserializer in class '" + this.targetClass.getCanonicalName() + "' to unpack element '" + elem.toJson(JsonGrammar.JSON5) + "'.");
      }
   }

   public static class FunctionMatchFailedException extends Exception {
      private static final long serialVersionUID = -7909332778483440658L;

      public FunctionMatchFailedException(String message) {
         super(message);
      }
   }
}
