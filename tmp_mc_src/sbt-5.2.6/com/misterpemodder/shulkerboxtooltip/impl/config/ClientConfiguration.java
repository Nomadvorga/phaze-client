package com.misterpemodder.shulkerboxtooltip.impl.config;

import com.misterpemodder.shulkerboxtooltip.api.color.ColorRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.color.ColorRegistryImpl;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.ConfigCategory;
import com.misterpemodder.shulkerboxtooltip.impl.util.Key;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.Comment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientConfiguration extends Configuration {
   @ConfigCategory(
      ordinal = 3
   )
   public ColorsCategory colors = new ColorsCategory();
   @ConfigCategory(
      ordinal = 4
   )
   public ControlsCategory controls = new ControlsCategory();

   public boolean useColors() {
      return this.colors.coloredPreview;
   }

   @Environment(EnvType.CLIENT)
   public static class ColorsCategory {
      @Comment("Controls whether the preview window should be colored.\n(default value: true)")
      public boolean coloredPreview = true;
      public ColorRegistry colors;

      public ColorsCategory() {
         this.colors = ColorRegistryImpl.INSTANCE;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class ControlsCategory {
      @Comment("Press this key when hovering a container stack to open the preview window.\n(default value: key.keyboard.left.shift)")
      public Key previewKey = Key.defaultPreviewKey();
      @Comment("Press this key when hovering a container stack to open the full preview window.\n(default value: key.keyboard.left.alt)")
      public Key fullPreviewKey = Key.defaultFullPreviewKey();
      @Comment("Hold this key when previewing a stack to lock the tooltip.\n(default value: key.keyboard.left.control)")
      public Key lockTooltipKey = Key.defaultLockTooltipKey();
   }
}
