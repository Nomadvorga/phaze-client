package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.Comment;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonArray;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonElement;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonNull;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonObject;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonPrimitive;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.annotation.SerializedName;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.DeserializationException;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.DeserializerFunction;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.Marshaller;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.serializer.DeserializerFunctionPool;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.magic.TypeMagic;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** @deprecated */
@Deprecated
public class MarshallerImpl implements Marshaller {
   private static MarshallerImpl INSTANCE = new MarshallerImpl();
   private Map<Class<?>, Function<Object, ?>> primitiveMarshallers = new HashMap();
   Map<Class<?>, Function<JsonObject, ?>> typeAdapters = new HashMap();
   private Map<Class<?>, BiFunction<Object, Marshaller, JsonElement>> serializers = new HashMap();
   private Map<Class<?>, DeserializerFunctionPool<?>> deserializers = new HashMap();
   private Map<Class<?>, Supplier<?>> typeFactories = new HashMap();

   public static Marshaller getFallback() {
      return INSTANCE;
   }

   public <T> void register(Class<T> clazz, Function<Object, T> marshaller) {
      this.primitiveMarshallers.put(clazz, marshaller);
   }

   public <T> void registerTypeAdapter(Class<T> clazz, Function<JsonObject, T> adapter) {
      this.typeAdapters.put(clazz, adapter);
   }

   public <T> void registerSerializer(Class<T> clazz, Function<T, JsonElement> serializer) {
      this.serializers.put(clazz, (BiFunction)(it, marshaller) -> (JsonElement)serializer.apply(it));
   }

   public <T> void registerSerializer(Class<T> clazz, BiFunction<T, Marshaller, JsonElement> serializer) {
      this.serializers.put(clazz, serializer);
   }

   public <T> void registerTypeFactory(Class<T> clazz, Supplier<T> supplier) {
      this.typeFactories.put(clazz, supplier);
   }

   public <A, B> void registerDeserializer(Class<A> sourceClass, Class<B> targetClass, DeserializerFunction<A, B> function) {
      DeserializerFunctionPool<B> pool = (DeserializerFunctionPool)this.deserializers.get(targetClass);
      if (pool == null) {
         pool = new DeserializerFunctionPool<B>(targetClass);
         this.deserializers.put(targetClass, pool);
      }

      pool.registerUnsafe(sourceClass, function);
   }

   public MarshallerImpl() {
      this.register(Void.class, (it) -> null);
      this.register(String.class, (it) -> it instanceof String ? (String)it : it.toString());
      this.register(Byte.class, (it) -> it instanceof Number ? ((Number)it).byteValue() : null);
      this.register(Character.class, (it) -> it instanceof Number ? (char)((Number)it).shortValue() : it.toString().charAt(0));
      this.register(Short.class, (it) -> it instanceof Number ? ((Number)it).shortValue() : null);
      this.register(Integer.class, (it) -> it instanceof Number ? ((Number)it).intValue() : null);
      this.register(Long.class, (it) -> it instanceof Number ? ((Number)it).longValue() : null);
      this.register(Float.class, (it) -> it instanceof Number ? ((Number)it).floatValue() : null);
      this.register(Double.class, (it) -> it instanceof Number ? ((Number)it).doubleValue() : null);
      this.register(Boolean.class, (it) -> it instanceof Boolean ? (Boolean)it : null);
      this.register(Void.TYPE, (it) -> null);
      this.register(Byte.TYPE, (it) -> it instanceof Number ? ((Number)it).byteValue() : null);
      this.register(Character.TYPE, (it) -> it instanceof Number ? (char)((Number)it).shortValue() : it.toString().charAt(0));
      this.register(Short.TYPE, (it) -> it instanceof Number ? ((Number)it).shortValue() : null);
      this.register(Integer.TYPE, (it) -> it instanceof Number ? ((Number)it).intValue() : null);
      this.register(Long.TYPE, (it) -> it instanceof Number ? ((Number)it).longValue() : null);
      this.register(Float.TYPE, (it) -> it instanceof Number ? ((Number)it).floatValue() : null);
      this.register(Double.TYPE, (it) -> it instanceof Number ? ((Number)it).doubleValue() : null);
      this.register(Boolean.TYPE, (it) -> it instanceof Boolean ? (Boolean)it : null);
      this.registerSerializer(Void.class, (Function)((it) -> JsonNull.INSTANCE));
      this.registerSerializer(Character.class, (Function)((it) -> new JsonPrimitive("" + it)));
      this.registerSerializer(String.class, JsonPrimitive::new);
      this.registerSerializer(Byte.class, (Function)((it) -> new JsonPrimitive((long)it)));
      this.registerSerializer(Short.class, (Function)((it) -> new JsonPrimitive((long)it)));
      this.registerSerializer(Integer.class, (Function)((it) -> new JsonPrimitive((long)it)));
      this.registerSerializer(Long.class, JsonPrimitive::new);
      this.registerSerializer(Float.class, (Function)((it) -> new JsonPrimitive((double)it)));
      this.registerSerializer(Double.class, JsonPrimitive::new);
      this.registerSerializer(Boolean.class, JsonPrimitive::new);
      this.registerSerializer(Void.TYPE, (Function)((it) -> JsonNull.INSTANCE));
      this.registerSerializer(Character.TYPE, (Function)((it) -> new JsonPrimitive("" + it)));
      this.registerSerializer(Byte.TYPE, (Function)((it) -> new JsonPrimitive((long)it)));
      this.registerSerializer(Short.TYPE, (Function)((it) -> new JsonPrimitive((long)it)));
      this.registerSerializer(Integer.TYPE, (Function)((it) -> new JsonPrimitive((long)it)));
      this.registerSerializer(Long.TYPE, JsonPrimitive::new);
      this.registerSerializer(Float.TYPE, (Function)((it) -> new JsonPrimitive((double)it)));
      this.registerSerializer(Double.TYPE, JsonPrimitive::new);
      this.registerSerializer(Boolean.TYPE, JsonPrimitive::new);
   }

   @Nullable
   public <T> T marshall(Type type, JsonElement elem) {
      if (elem == null) {
         return null;
      } else if (elem == JsonNull.INSTANCE) {
         return null;
      } else if (type instanceof Class) {
         try {
            return (T)this.marshall((Class)type, elem);
         } catch (ClassCastException var4) {
            return null;
         }
      } else if (type instanceof ParameterizedType) {
         try {
            Class<T> clazz = TypeMagic.classForType(type);
            return (T)this.marshall(clazz, elem);
         } catch (ClassCastException var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   public <T> T marshall(Class<T> clazz, JsonElement elem) {
      try {
         return (T)this.marshall(clazz, elem, false);
      } catch (Throwable var4) {
         return null;
      }
   }

   public <T> T marshallCarefully(Class<T> clazz, JsonElement elem) throws DeserializationException {
      return (T)this.marshall(clazz, elem, true);
   }

   @Nullable
   public <T> T marshall(Class<T> clazz, JsonElement elem, boolean failFast) throws DeserializationException {
      if (elem == null) {
         return null;
      } else if (elem == JsonNull.INSTANCE) {
         return null;
      } else if (clazz.isAssignableFrom(elem.getClass())) {
         return (T)elem;
      } else {
         DeserializerFunctionPool<T> pool = (DeserializerFunctionPool)this.deserializers.get(clazz);
         if (pool != null) {
            try {
               return pool.apply(elem, this);
            } catch (DeserializerFunctionPool.FunctionMatchFailedException var15) {
            }
         }

         pool = POJODeserializer.<T>deserializersFor(clazz);

         try {
            T poolResult = pool.apply(elem, this);
            return poolResult;
         } catch (DeserializerFunctionPool.FunctionMatchFailedException var14) {
            if (Enum.class.isAssignableFrom(clazz)) {
               if (!(elem instanceof JsonPrimitive)) {
                  return null;
               }

               String name = ((JsonPrimitive)elem).getValue().toString();
               T[] constants = (T[])clazz.getEnumConstants();
               if (constants == null) {
                  return null;
               }

               for(T t : constants) {
                  if (((Enum)t).name().equals(name)) {
                     return t;
                  }
               }
            }

            if (clazz.equals(String.class)) {
               if (elem instanceof JsonObject) {
                  return (T)((JsonObject)elem).toJson(false, false);
               } else if (elem instanceof JsonArray) {
                  return (T)((JsonArray)elem).toJson(false, false);
               } else if (elem instanceof JsonPrimitive) {
                  ((JsonPrimitive)elem).getValue();
                  return (T)((JsonPrimitive)elem).asString();
               } else if (elem instanceof JsonNull) {
                  return (T)"null";
               } else if (failFast) {
                  throw new DeserializationException("Encountered unexpected JsonElement type while deserializing to string: " + elem.getClass().getCanonicalName());
               } else {
                  return null;
               }
            } else if (elem instanceof JsonPrimitive) {
               Function<Object, ?> func = (Function)this.primitiveMarshallers.get(clazz);
               if (func != null) {
                  return (T)func.apply(((JsonPrimitive)elem).getValue());
               } else if (failFast) {
                  throw new DeserializationException("Don't know how to unpack value '" + elem.toString() + "' into target type '" + clazz.getCanonicalName() + "'");
               } else {
                  return null;
               }
            } else if (elem instanceof JsonObject) {
               if (clazz.isPrimitive()) {
                  throw new DeserializationException("Can't marshall json object into primitive type " + clazz.getCanonicalName());
               } else if (JsonPrimitive.class.isAssignableFrom(clazz)) {
                  if (failFast) {
                     throw new DeserializationException("Can't marshall json object into a json primitive");
                  } else {
                     return null;
                  }
               } else {
                  JsonObject obj = (JsonObject)elem;
                  obj.setMarshaller(this);
                  if (this.typeAdapters.containsKey(clazz)) {
                     return (T)((Function)this.typeAdapters.get(clazz)).apply((JsonObject)elem);
                  } else if (this.typeFactories.containsKey(clazz)) {
                     T result = (T)((Supplier)this.typeFactories.get(clazz)).get();

                     try {
                        POJODeserializer.unpackObject(result, obj, failFast);
                        return result;
                     } catch (Throwable t) {
                        if (failFast) {
                           throw t;
                        } else {
                           return null;
                        }
                     }
                  } else {
                     try {
                        T result = (T)TypeMagic.createAndCast(clazz, failFast);
                        POJODeserializer.unpackObject(result, obj, failFast);
                        return result;
                     } catch (Throwable t) {
                        if (failFast) {
                           throw t;
                        } else {
                           return null;
                        }
                     }
                  }
               }
            } else {
               if (elem instanceof JsonArray) {
                  if (clazz.isPrimitive()) {
                     return null;
                  }

                  if (clazz.isArray()) {
                     Class<?> componentType = clazz.getComponentType();
                     JsonArray array = (JsonArray)elem;
                     T result = (T)Array.newInstance(componentType, array.size());

                     for(int i = 0; i < array.size(); ++i) {
                        Array.set(result, i, this.marshall(componentType, array.get(i)));
                     }

                     return result;
                  }
               }

               return null;
            }
         }
      }
   }

   public JsonElement serialize(Object obj) {
      if (obj == null) {
         return JsonNull.INSTANCE;
      } else {
         BiFunction<Object, Marshaller, JsonElement> serializer = (BiFunction)this.serializers.get(obj.getClass());
         if (serializer != null) {
            JsonElement result = (JsonElement)serializer.apply(obj, this);
            if (result instanceof JsonObject) {
               ((JsonObject)result).setMarshaller(this);
            }

            if (result instanceof JsonArray) {
               ((JsonArray)result).setMarshaller(this);
            }

            return result;
         } else {
            for(Map.Entry<Class<?>, BiFunction<Object, Marshaller, JsonElement>> entry : this.serializers.entrySet()) {
               if (((Class)entry.getKey()).isAssignableFrom(obj.getClass())) {
                  JsonElement result = (JsonElement)((BiFunction)entry.getValue()).apply(obj, this);
                  if (result instanceof JsonObject) {
                     ((JsonObject)result).setMarshaller(this);
                  }

                  if (result instanceof JsonArray) {
                     ((JsonArray)result).setMarshaller(this);
                  }

                  return result;
               }
            }

            if (obj instanceof Enum) {
               return new JsonPrimitive(((Enum)obj).name());
            } else if (obj.getClass().isArray()) {
               JsonArray array = new JsonArray();
               array.setMarshaller(this);

               for(int i = 0; i < Array.getLength(obj); ++i) {
                  Object elem = Array.get(obj, i);
                  JsonElement parsed = this.serialize(elem);
                  array.add(parsed);
               }

               return array;
            } else if (obj instanceof Collection) {
               JsonArray array = new JsonArray();
               array.setMarshaller(this);

               for(Object elem : (Collection)obj) {
                  JsonElement parsed = this.serialize(elem);
                  array.add(parsed);
               }

               return array;
            } else if (!(obj instanceof Map)) {
               JsonObject result = new JsonObject();

               for(Field f : obj.getClass().getFields()) {
                  if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                     f.setAccessible(true);

                     try {
                        Object child = f.get(obj);
                        String name = f.getName();
                        SerializedName nameAnnotation = (SerializedName)f.getAnnotation(SerializedName.class);
                        if (nameAnnotation != null) {
                           name = nameAnnotation.value();
                        }

                        Comment comment = (Comment)f.getAnnotation(Comment.class);
                        if (comment == null) {
                           result.put(name, this.serialize(child));
                        } else {
                           result.put(name, this.serialize(child), comment.value());
                        }
                     } catch (IllegalAccessException | IllegalArgumentException var13) {
                     }
                  }
               }

               for(Field f : obj.getClass().getDeclaredFields()) {
                  if (!Modifier.isPublic(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                     f.setAccessible(true);

                     try {
                        Object child = f.get(obj);
                        String name = f.getName();
                        SerializedName nameAnnotation = (SerializedName)f.getAnnotation(SerializedName.class);
                        if (nameAnnotation != null) {
                           name = nameAnnotation.value();
                        }

                        Comment comment = (Comment)f.getAnnotation(Comment.class);
                        if (comment == null) {
                           result.put(name, this.serialize(child));
                        } else {
                           result.put(name, this.serialize(child), comment.value());
                        }
                     } catch (IllegalAccessException | IllegalArgumentException var12) {
                     }
                  }
               }

               return result;
            } else {
               JsonObject result = new JsonObject();

               for(Map.Entry<?, ?> entry : ((Map)obj).entrySet()) {
                  String k = entry.getKey().toString();
                  Object v = entry.getValue();
                  result.put(k, this.serialize(v));
               }

               return result;
            }
         }
      }
   }
}
