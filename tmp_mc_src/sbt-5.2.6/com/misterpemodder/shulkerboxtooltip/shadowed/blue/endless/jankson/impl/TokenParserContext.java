package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.Jankson;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.JsonPrimitive;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.SyntaxError;

public class TokenParserContext implements ParserContext<JsonPrimitive> {
   private String token = "";
   private boolean complete = false;

   public TokenParserContext(int firstCodePoint) {
      this.token = this.token + (char)firstCodePoint;
   }

   public boolean consume(int codePoint, Jankson loader) throws SyntaxError {
      if (this.complete) {
         return false;
      } else if (codePoint != 126 && !Character.isUnicodeIdentifierPart(codePoint)) {
         this.complete = true;
         return false;
      } else if (codePoint < 65535) {
         this.token = this.token + (char)codePoint;
         return true;
      } else {
         int temp = codePoint - 65536;
         int highSurrogate = (temp >>> 10) + '\ud800';
         int lowSurrogate = (temp & 1023) + '\udc00';
         this.token = this.token + (char)highSurrogate;
         this.token = this.token + (char)lowSurrogate;
         return true;
      }
   }

   public void eof() throws SyntaxError {
      this.complete = true;
   }

   public boolean isComplete() {
      return this.complete;
   }

   public JsonPrimitive getResult() throws SyntaxError {
      return new JsonPrimitive(this.token);
   }
}
