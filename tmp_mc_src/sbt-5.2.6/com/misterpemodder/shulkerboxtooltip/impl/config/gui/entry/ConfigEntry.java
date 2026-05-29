package com.misterpemodder.shulkerboxtooltip.impl.config.gui.entry;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_4265;
import net.minecraft.class_5481;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ConfigEntry extends class_4265.class_4266<ConfigEntry> {
   public @Nullable List<class_5481> getTooltip() {
      return null;
   }

   public void refresh() {
   }
}
