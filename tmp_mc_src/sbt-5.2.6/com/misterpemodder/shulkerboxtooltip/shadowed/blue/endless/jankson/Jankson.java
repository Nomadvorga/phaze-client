package com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson;

import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.DeserializationException;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.DeserializerFunction;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.Marshaller;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.api.SyntaxError;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.AnnotatedElement;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.ElementParserContext;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.MarshallerImpl;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.ObjectParserContext;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.impl.ParserContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

public class Jankson {
   private Deque<ParserFrame<?>> contextStack;
   private JsonObject root;
   private int line;
   private int column;
   private int withheldCodePoint;
   private Marshaller marshaller;
   private int retries;
   private SyntaxError delayedError;
   private static final int BAD_CHARACTER = 65533;
   private AnnotatedElement rootElement;

   private Jankson(Builder builder) {
      this.contextStack = new ArrayDeque();
      this.line = 0;
      this.column = 0;
      this.withheldCodePoint = -1;
      this.marshaller = MarshallerImpl.getFallback();
      this.retries = 0;
      this.delayedError = null;
   }

   @Nonnull
   public JsonObject load(String s) throws SyntaxError {
      ByteArrayInputStream in = new ByteArrayInputStream(s.getBytes(Charset.forName("UTF-8")));

      try {
         return this.load((InputStream)in);
      } catch (IOException ex) {
         throw new RuntimeException(ex);
      }
   }

   @Nonnull
   public JsonObject load(File f) throws IOException, SyntaxError {
      InputStream in = new FileInputStream(f);
      Throwable var3 = null;

      JsonObject var4;
      try {
         var4 = this.load(in);
      } catch (Throwable var13) {
         var3 = var13;
         throw var13;
      } finally {
         if (in != null) {
            if (var3 != null) {
               try {
                  in.close();
               } catch (Throwable var12) {
                  var3.addSuppressed(var12);
               }
            } else {
               in.close();
            }
         }

      }

      return var4;
   }

   private static boolean isLowSurrogate(int i) {
      return (i & 192) == 128;
   }

   public int getCodePoint(InputStream in) throws IOException {
      int i = in.read();
      if (i == -1) {
         return -1;
      } else if ((i & 128) == 0) {
         return i;
      } else if ((i & 248) == 240) {
         int codePoint = i & 7;
         i = in.read();
         if (i == -1) {
            return -1;
         } else if (!isLowSurrogate(i)) {
            return 65533;
         } else {
            codePoint <<= 6;
            codePoint |= i & 63;
            i = in.read();
            if (i == -1) {
               return -1;
            } else if (!isLowSurrogate(i)) {
               return 65533;
            } else {
               codePoint <<= 6;
               codePoint |= i & 63;
               i = in.read();
               if (i == -1) {
                  return -1;
               } else if (!isLowSurrogate(i)) {
                  return 65533;
               } else {
                  codePoint <<= 6;
                  codePoint |= i & 63;
                  return codePoint;
               }
            }
         }
      } else if ((i & 240) == 224) {
         int codePoint = i & 15;
         i = in.read();
         if (i == -1) {
            return -1;
         } else if (!isLowSurrogate(i)) {
            return 65533;
         } else {
            codePoint <<= 6;
            codePoint |= i & 63;
            i = in.read();
            if (i == -1) {
               return -1;
            } else if (!isLowSurrogate(i)) {
               return 65533;
            } else {
               codePoint <<= 6;
               codePoint |= i & 63;
               return codePoint;
            }
         }
      } else if ((i & 224) == 192) {
         int codePoint = i & 15;
         i = in.read();
         if (i == -1) {
            return -1;
         } else if (!isLowSurrogate(i)) {
            return 65533;
         } else {
            codePoint <<= 6;
            codePoint |= i & 63;
            return codePoint;
         }
      } else {
         return 65533;
      }
   }

   @Nonnull
   public JsonObject load(InputStream in) throws IOException, SyntaxError {
      this.withheldCodePoint = -1;
      this.root = null;
      this.push(new ObjectParserContext(), (it) -> this.root = it);

      while(this.root == null) {
         if (this.delayedError != null) {
            throw this.delayedError;
         }

         if (this.withheldCodePoint != -1) {
            ++this.retries;
            if (this.retries > 25) {
               throw new IOException("Parser got stuck near line " + this.line + " column " + this.column);
            }

            this.processCodePoint(this.withheldCodePoint);
         } else {
            int inByte = this.getCodePoint(in);
            if (inByte == -1) {
               while(!this.contextStack.isEmpty()) {
                  ParserFrame<?> frame = (ParserFrame)this.contextStack.pop();

                  try {
                     frame.context.eof();
                  } catch (SyntaxError error) {
                     error.setStartParsing(frame.startLine, frame.startCol);
                     error.setEndParsing(this.line, this.column);
                     throw error;
                  }
               }

               if (this.root == null) {
                  this.root = new JsonObject();
                  this.root.marshaller = this.marshaller;
               }

               return this.root;
            }

            this.processCodePoint(inByte);
         }
      }

      return this.root;
   }

   @Nonnull
   public JsonElement loadElement(String s) throws SyntaxError {
      ByteArrayInputStream in = new ByteArrayInputStream(s.getBytes(Charset.forName("UTF-8")));

      try {
         return this.loadElement((InputStream)in);
      } catch (IOException ex) {
         throw new RuntimeException(ex);
      }
   }

   @Nonnull
   public JsonElement loadElement(File f) throws IOException, SyntaxError {
      InputStream in = new FileInputStream(f);
      Throwable var3 = null;

      JsonElement var4;
      try {
         var4 = this.loadElement(in);
      } catch (Throwable var13) {
         var3 = var13;
         throw var13;
      } finally {
         if (in != null) {
            if (var3 != null) {
               try {
                  in.close();
               } catch (Throwable var12) {
                  var3.addSuppressed(var12);
               }
            } else {
               in.close();
            }
         }

      }

      return var4;
   }

   @Nonnull
   public JsonElement loadElement(InputStream in) throws IOException, SyntaxError {
      this.withheldCodePoint = -1;
      this.push(new ElementParserContext(), (it) -> this.rootElement = it);

      while(this.rootElement == null) {
         if (this.delayedError != null) {
            throw this.delayedError;
         }

         if (this.withheldCodePoint != -1) {
            ++this.retries;
            if (this.retries > 25) {
               throw new IOException("Parser got stuck near line " + this.line + " column " + this.column);
            }

            this.processCodePoint(this.withheldCodePoint);
         } else {
            int inByte = this.getCodePoint(in);
            if (inByte == -1) {
               while(!this.contextStack.isEmpty()) {
                  ParserFrame<?> frame = (ParserFrame)this.contextStack.pop();

                  try {
                     frame.context.eof();
                  } catch (SyntaxError error) {
                     error.setStartParsing(frame.startLine, frame.startCol);
                     error.setEndParsing(this.line, this.column);
                     throw error;
                  }
               }

               if (this.rootElement == null) {
                  return JsonNull.INSTANCE;
               }
            }

            this.processCodePoint(inByte);
         }
      }

      return this.rootElement.getElement();
   }

   public <T> T fromJson(JsonObject obj, Class<T> clazz) {
      return (T)this.marshaller.marshall(clazz, obj);
   }

   public <T> T fromJson(String json, Class<T> clazz) throws SyntaxError {
      JsonObject obj = this.load(json);
      return (T)this.fromJson(obj, clazz);
   }

   public <T> T fromJsonCarefully(String json, Class<T> clazz) throws SyntaxError, DeserializationException {
      JsonObject obj = this.load(json);
      return (T)this.fromJsonCarefully(obj, clazz);
   }

   public <T> T fromJsonCarefully(JsonObject obj, Class<T> clazz) throws DeserializationException {
      return (T)this.marshaller.marshallCarefully(clazz, obj);
   }

   public <T> JsonElement toJson(T t) {
      return this.marshaller.serialize(t);
   }

   public <T> JsonElement toJson(T t, Marshaller alternateMarshaller) {
      return alternateMarshaller.serialize(t);
   }

   private void processCodePoint(int codePoint) throws SyntaxError {
      ParserFrame<?> frame = (ParserFrame)this.contextStack.peek();
      if (frame == null) {
         throw new IllegalStateException("Parser problem! The ParserContext stack underflowed! (line " + this.line + ", col " + this.column + ")");
      } else {
         try {
            if (frame.context().isComplete()) {
               this.contextStack.pop();
               frame.supply();
               frame = (ParserFrame)this.contextStack.peek();
            }
         } catch (SyntaxError error) {
            error.setStartParsing(frame.startLine, frame.startCol);
            error.setEndParsing(this.line, this.column);
            throw error;
         }

         try {
            boolean consumed = frame.context.consume(codePoint, this);
            if (frame.context.isComplete()) {
               this.contextStack.pop();
               frame.supply();
            }

            if (consumed) {
               this.withheldCodePoint = -1;
               this.retries = 0;
            } else {
               this.withheldCodePoint = codePoint;
            }
         } catch (SyntaxError error) {
            error.setStartParsing(frame.startLine, frame.startCol);
            error.setEndParsing(this.line, this.column);
            throw error;
         }

         ++this.column;
         if (codePoint == 10) {
            ++this.line;
            this.column = 0;
         }

      }
   }

   public <T> void push(ParserContext<T> t, Consumer<T> consumer) {
      ParserFrame<T> frame = new ParserFrame<T>(t, consumer);
      frame.startLine = this.line;
      frame.startCol = this.column;
      this.contextStack.push(frame);
   }

   public Marshaller getMarshaller() {
      return this.marshaller;
   }

   public static Builder builder() {
      return new Builder();
   }

   public void throwDelayed(SyntaxError syntaxError) {
      syntaxError.setEndParsing(this.line, this.column);
      this.delayedError = syntaxError;
   }

   public static class Builder {
      MarshallerImpl marshaller = new MarshallerImpl();

      /** @deprecated */
      @Deprecated
      public <T> Builder registerTypeAdapter(Class<T> clazz, Function<JsonObject, T> adapter) {
         this.marshaller.registerTypeAdapter(clazz, adapter);
         return this;
      }

      /** @deprecated */
      @Deprecated
      public <T> Builder registerPrimitiveTypeAdapter(Class<T> clazz, Function<Object, T> adapter) {
         this.marshaller.register(clazz, adapter);
         return this;
      }

      public <T> Builder registerSerializer(Class<T> clazz, BiFunction<T, Marshaller, JsonElement> serializer) {
         this.marshaller.registerSerializer(clazz, serializer);
         return this;
      }

      public <A, B> Builder registerDeserializer(Class<A> sourceClass, Class<B> targetClass, DeserializerFunction<A, B> function) {
         this.marshaller.registerDeserializer(sourceClass, targetClass, function);
         return this;
      }

      public <T> Builder registerTypeFactory(Class<T> clazz, Supplier<T> factory) {
         this.marshaller.registerTypeFactory(clazz, factory);
         return this;
      }

      public Jankson build() {
         Jankson result = new Jankson(this);
         result.marshaller = this.marshaller;
         return result;
      }
   }

   private static class ParserFrame<T> {
      private ParserContext<T> context;
      private Consumer<T> consumer;
      private int startLine = 0;
      private int startCol = 0;

      public ParserFrame(ParserContext<T> context, Consumer<T> consumer) {
         this.context = context;
         this.consumer = consumer;
      }

      public ParserContext<T> context() {
         return this.context;
      }

      public Consumer<T> consumer() {
         return this.consumer;
      }

      public void supply() throws SyntaxError {
         this.consumer.accept(this.context.getResult());
      }
   }
}
