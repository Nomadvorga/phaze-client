package me.zyouime.hitcolor.mixin.accessor;

import net.minecraft.class_1011;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_1011.class})
public interface INativeImageMixin {
   @Accessor("pointer")
   long getPointer();
}
