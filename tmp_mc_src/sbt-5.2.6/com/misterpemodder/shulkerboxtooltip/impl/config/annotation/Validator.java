package com.misterpemodder.shulkerboxtooltip.impl.config.annotation;

import com.misterpemodder.shulkerboxtooltip.impl.tree.ValueConfigNode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Validator {
   Class<? extends ValueConfigNode.ValueValidator<?>> value();
}
