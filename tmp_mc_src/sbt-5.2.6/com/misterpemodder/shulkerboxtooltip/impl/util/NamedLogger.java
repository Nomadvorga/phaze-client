package com.misterpemodder.shulkerboxtooltip.impl.util;

import org.apache.logging.log4j.Logger;

public final class NamedLogger {
   private final Logger inner;

   public NamedLogger(Logger inner) {
      this.inner = inner;
   }

   public void error(String message) {
      Logger var10000 = this.inner;
      String var10001 = this.inner.getName();
      var10000.error("[" + var10001 + "] " + message);
   }

   public void error(String message, Exception error) {
      this.inner.error("[" + this.inner.getName() + "] " + message, error);
   }

   public void debug(String message) {
      Logger var10000 = this.inner;
      String var10001 = this.inner.getName();
      var10000.debug("[" + var10001 + "] " + message);
   }

   public void info(String message) {
      Logger var10000 = this.inner;
      String var10001 = this.inner.getName();
      var10000.info("[" + var10001 + "] " + message);
   }

   public void info(String message, Object arg1) {
      this.inner.info("[" + this.inner.getName() + "] " + message, arg1);
   }

   public void info(String message, Object arg1, Object arg2) {
      this.inner.info("[" + this.inner.getName() + "] " + message, arg1, arg2);
   }

   public void warn(String message) {
      Logger var10000 = this.inner;
      String var10001 = this.inner.getName();
      var10000.warn("[" + var10001 + "] " + message);
   }
}
