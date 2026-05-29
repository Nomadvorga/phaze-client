package com.misterpemodder.shulkerboxtooltip.impl.config.validators;

import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import net.minecraft.class_2561;
import org.jetbrains.annotations.Nullable;

public final class GreaterThanZero implements ValueConfigNode.ValueValidator<Object> {
   public @Nullable class_2561 validate(Object value) {
      Class<?> valueClass = value.getClass();
      return valueClass.equals(Integer.class) && (Integer)value <= 0 ? class_2561.method_43471("shulkerboxtooltip.config.validator.greater_than_zero") : null;
   }
}
