package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.serializer;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.DeserializationException;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.Marshaller;

@FunctionalInterface
public interface InternalDeserializerFunction<B> {
   B deserialize(Object var1, Marshaller var2) throws DeserializationException;
}
