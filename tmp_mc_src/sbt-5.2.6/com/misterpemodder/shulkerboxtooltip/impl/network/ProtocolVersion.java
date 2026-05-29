package com.misterpemodder.shulkerboxtooltip.impl.network;

import net.minecraft.class_2540;
import org.jetbrains.annotations.Nullable;

public record ProtocolVersion(int major, int minor) {
   public static final ProtocolVersion CURRENT = new ProtocolVersion(2, 0);

   public static @Nullable ProtocolVersion readFromPacketBuf(class_2540 buf) {
      try {
         return new ProtocolVersion(buf.readInt(), buf.readInt());
      } catch (RuntimeException var2) {
         return null;
      }
   }

   public void writeToPacketBuf(class_2540 buf) {
      buf.method_53002(this.major);
      buf.method_53002(this.minor);
   }

   public String toString() {
      return this.major + "." + this.minor;
   }
}
