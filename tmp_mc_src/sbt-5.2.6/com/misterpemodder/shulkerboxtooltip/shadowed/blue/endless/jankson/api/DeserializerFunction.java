package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.serializer.InternalDeserializerFunction;

@FunctionalInterface
public interface DeserializerFunction<A, B> extends InternalDeserializerFunction<B> {
   B apply(A var1, Marshaller var2) throws DeserializationException;

   default B deserialize(Object a, Marshaller m) throws DeserializationException {
      try {
         return (B)this.apply(a, m);
      } catch (ClassCastException ex) {
         throw new DeserializationException(ex);
      }
   }
}
