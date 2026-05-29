package com.misterpemodder.shulkerboxtooltip.impl.tree;

import net.minecraft.class_2487;
import net.minecraft.class_2561;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConfigNode<C> {
   @NotNull String getName();

   @NotNull class_2561 getTitle();

   @Nullable class_2561 getTooltip();

   @Nullable class_2561 getPrefix();

   void resetToDefault();

   void resetToActive(C var1);

   boolean restartRequired(C var1);

   boolean isDefaultValue(C var1);

   boolean isActiveValue(C var1);

   @Nullable class_2561 validate(C var1);

   void writeToNbt(C var1, class_2487 var2);

   void readFromNbt(C var1, class_2487 var2);

   void copy(C var1, C var2);

   void writeEditingToConfig(C var1);
}
