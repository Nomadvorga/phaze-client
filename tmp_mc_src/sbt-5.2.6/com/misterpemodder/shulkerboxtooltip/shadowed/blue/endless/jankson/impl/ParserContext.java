package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.Jankson;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.SyntaxError;

public interface ParserContext<T> {
   boolean consume(int var1, Jankson var2) throws SyntaxError;

   void eof() throws SyntaxError;

   boolean isComplete();

   T getResult() throws SyntaxError;
}
