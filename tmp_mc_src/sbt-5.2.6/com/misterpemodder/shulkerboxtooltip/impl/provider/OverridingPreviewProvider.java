package com.misterpemodder.shulkerboxtooltip.impl.provider;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.renderer.PreviewRenderer;
import com.misterpemodder.shulkerboxtooltip.impl.color.ColorKeyImpl;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_9279;
import net.minecraft.class_9334;
import org.jetbrains.annotations.Nullable;

public class OverridingPreviewProvider implements PreviewProvider {
   private final PreviewProvider delegate;
   private final PreviewOverrides overrides;

   private OverridingPreviewProvider(PreviewProvider delegate, PreviewOverrides overrides) {
      this.delegate = delegate;
      this.overrides = overrides;
   }

   public static PreviewProvider maybeWrap(@Nullable PreviewProvider delegate, class_1799 stack) {
      if (delegate != null && !(delegate instanceof OverridingPreviewProvider)) {
         class_9279 custom = (class_9279)stack.method_57824(class_9334.field_49628);
         return custom == null ? delegate : (PreviewProvider)custom.method_57446(OverridingPreviewProvider.PreviewOverrides.WRAPPED_DECODER).result().map((overrides) -> new OverridingPreviewProvider(delegate, overrides)).orElse(delegate);
      } else {
         return delegate;
      }
   }

   public List<class_2561> addTooltip(PreviewContext context) {
      return this.delegate.addTooltip(context);
   }

   public boolean shouldDisplay(PreviewContext context) {
      return (Boolean)this.overrides.shouldDisplay.orElseGet(() -> this.delegate.shouldDisplay(context));
   }

   public List<class_1799> getInventory(PreviewContext context) {
      return this.delegate.getInventory(context);
   }

   public int getInventoryMaxSize(PreviewContext context) {
      return (Integer)this.overrides.inventoryMaxSize.orElseGet(() -> this.delegate.getInventoryMaxSize(context));
   }

   public int getMaxRowSize(PreviewContext context) {
      return (Integer)this.overrides.maxRowSize.orElseGet(() -> this.delegate.getMaxRowSize(context));
   }

   public int getCompactMaxRowSize(PreviewContext context) {
      return (Integer)this.overrides.compactMaxRowSize.orElseGet(() -> this.delegate.getCompactMaxRowSize(context));
   }

   public boolean isFullPreviewAvailable(PreviewContext context) {
      return (Boolean)this.overrides.fullPreviewAvailable.orElseGet(() -> this.delegate.isFullPreviewAvailable(context));
   }

   public boolean showTooltipHints(PreviewContext context) {
      return (Boolean)this.overrides.showTooltipHints.orElseGet(() -> this.delegate.showTooltipHints(context));
   }

   public String getTooltipHintLangKey(PreviewContext context) {
      return (String)this.overrides.tooltipHintLangKey.orElseGet(() -> this.delegate.getTooltipHintLangKey(context));
   }

   public String getFullTooltipHintLangKey(PreviewContext context) {
      return (String)this.overrides.fullTooltipHintLangKey.orElseGet(() -> this.delegate.getFullTooltipHintLangKey(context));
   }

   public String getLockKeyTooltipHintLangKey(PreviewContext context) {
      return (String)this.overrides.lockKeyTooltipHintLangKey.orElseGet(() -> this.delegate.getLockKeyTooltipHintLangKey(context));
   }

   @Environment(EnvType.CLIENT)
   public ColorKey getWindowColorKey(PreviewContext context) {
      return (ColorKey)this.overrides.windowColor.orElseGet(() -> this.delegate.getWindowColorKey(context));
   }

   @Environment(EnvType.CLIENT)
   public PreviewRenderer getRenderer() {
      return this.delegate.getRenderer();
   }

   @Environment(EnvType.CLIENT)
   public void onInventoryAccessStart(PreviewContext context) {
      this.delegate.onInventoryAccessStart(context);
   }

   @Environment(EnvType.CLIENT)
   public @Nullable class_2960 getTextureOverride(PreviewContext context) {
      return (class_2960)this.overrides.texture.orElseGet(() -> this.delegate.getTextureOverride(context));
   }

   public int getPriority() {
      return this.delegate.getPriority();
   }

   private static record PreviewOverrides(Optional<Boolean> shouldDisplay, Optional<Integer> inventoryMaxSize, Optional<Integer> maxRowSize, Optional<Integer> compactMaxRowSize, Optional<Boolean> fullPreviewAvailable, Optional<Boolean> showTooltipHints, Optional<String> tooltipHintLangKey, Optional<String> fullTooltipHintLangKey, Optional<String> lockKeyTooltipHintLangKey, Optional<ColorKey> windowColor, Optional<class_2960> texture, Optional<Boolean> canInsertItems, Optional<Boolean> canExtractItems) {
      public static final MapCodec<PreviewOverrides> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(Codec.BOOL.lenientOptionalFieldOf("should_display").forGetter(PreviewOverrides::shouldDisplay), Codec.INT.lenientOptionalFieldOf("inventory_max_size").forGetter(PreviewOverrides::inventoryMaxSize), Codec.INT.lenientOptionalFieldOf("max_row_size").forGetter(PreviewOverrides::maxRowSize), Codec.INT.lenientOptionalFieldOf("compact_max_row_size").forGetter(PreviewOverrides::compactMaxRowSize), Codec.BOOL.lenientOptionalFieldOf("full_preview_available").forGetter(PreviewOverrides::fullPreviewAvailable), Codec.BOOL.lenientOptionalFieldOf("show_tooltip_hints").forGetter(PreviewOverrides::showTooltipHints), Codec.STRING.lenientOptionalFieldOf("tooltip_hint_lang_key").forGetter(PreviewOverrides::tooltipHintLangKey), Codec.STRING.lenientOptionalFieldOf("full_tooltip_hint_lang_key").forGetter(PreviewOverrides::fullTooltipHintLangKey), Codec.STRING.lenientOptionalFieldOf("lock_tooltip_hint_lang_key").forGetter(PreviewOverrides::lockKeyTooltipHintLangKey), ColorKeyImpl.CODEC.lenientOptionalFieldOf("window_color").forGetter(PreviewOverrides::windowColor), class_2960.field_25139.lenientOptionalFieldOf("texture").forGetter(PreviewOverrides::texture), Codec.BOOL.lenientOptionalFieldOf("can_insert_items").forGetter(PreviewOverrides::canInsertItems), Codec.BOOL.lenientOptionalFieldOf("can_extract_items").forGetter(PreviewOverrides::canExtractItems)).apply(instance, PreviewOverrides::new));
      public static final MapDecoder<PreviewOverrides> WRAPPED_DECODER;

      static {
         WRAPPED_DECODER = class_9279.field_49303.fieldOf("shulkerboxtooltip").flatMap((c) -> c.method_57446(CODEC));
      }
   }
}
