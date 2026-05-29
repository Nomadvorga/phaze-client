package com.misterpemodder.shulkerboxtooltip.impl.color;

import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2960;

@Environment(EnvType.CLIENT)
public record ColorKeyImpl(float[] rgbComponents, float[] defaultRgbComponents) implements ColorKey {
   private static final Codec<Pair<class_2960, String>> CATEGORY_AND_ID_CODEC = RecordCodecBuilder.create((instance) -> instance.group(class_2960.field_25139.fieldOf("category").forGetter(Pair::getFirst), Codec.STRING.fieldOf("id").forGetter(Pair::getSecond)).apply(instance, Pair::of));
   private static final Codec<ColorKey> FULL_CODEC;
   private static final Codec<ColorKey> INT_CODEC;
   public static final Codec<ColorKey> CODEC;

   public int rgb() {
      return ShulkerBoxTooltipUtil.componentsToRgb(this.rgbComponents());
   }

   public int defaultRgb() {
      return ShulkerBoxTooltipUtil.componentsToRgb(this.defaultRgbComponents());
   }

   public void setRgb(int rgb) {
      this.setRgb(ShulkerBoxTooltipUtil.rgbToComponents(rgb));
   }

   public void setRgb(float[] rgb) {
      this.rgbComponents[0] = rgb[0];
      this.rgbComponents[1] = rgb[1];
      this.rgbComponents[2] = rgb[2];
   }

   public String toString() {
      return String.format("ColorKey(rgb=#%x, defaultRgb=#%x)", this.rgb(), this.defaultRgb());
   }

   static {
      FULL_CODEC = CATEGORY_AND_ID_CODEC.flatXmap((categoryAndId) -> {
         class_2960 category = (class_2960)categoryAndId.getFirst();
         String id = (String)categoryAndId.getSecond();
         ColorKey key = ColorRegistryImpl.INSTANCE.category(category).key(id);
         return key == null ? DataResult.error(() -> "Unknown color key " + id + " in category " + String.valueOf(category)) : DataResult.success(key);
      }, (key) -> DataResult.error(() -> "Cannot encode color key " + String.valueOf(key) + " as a string"));
      INT_CODEC = Codec.INT.xmap(ColorKey::ofRgb, ColorKey::rgb);
      CODEC = Codec.withAlternative(FULL_CODEC, INT_CODEC);
   }
}
