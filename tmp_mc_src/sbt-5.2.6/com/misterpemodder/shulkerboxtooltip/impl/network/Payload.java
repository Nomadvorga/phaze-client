package com.misterpemodder.shulkerboxtooltip.impl.network;

import net.minecraft.class_8710;

public record Payload<T>(class_8710.class_9154<?> id, T value) implements class_8710 {
   public class_8710.class_9154<? extends class_8710> method_56479() {
      return this.id;
   }
}
