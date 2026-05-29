package com.misterpemodder.shulkerboxtooltip.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum PreviewType {
   NO_PREVIEW,
   COMPACT,
   FULL;

   // $FF: synthetic method
   private static PreviewType[] $values() {
      return new PreviewType[]{NO_PREVIEW, COMPACT, FULL};
   }
}
