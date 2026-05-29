package com.misterpemodder.shulkerboxtooltip.impl.config;

import com.misterpemodder.shulkerboxtooltip.api.config.ItemStackMergingStrategy;
import com.misterpemodder.shulkerboxtooltip.api.config.PreviewConfiguration;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.ConfigCategory;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.RequiresRestart;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.Synchronize;
import com.misterpemodder.shulkerboxtooltip.impl.config.annotation.Validator;
import com.misterpemodder.shulkerboxtooltip.impl.config.validators.GreaterThanZero;
import com.misterpemodder.shulkerboxtooltip.shadowed.blue.endless.jankson.Comment;

public class Configuration implements PreviewConfiguration {
   @ConfigCategory(
      ordinal = 1
   )
   public PreviewCategory preview = new PreviewCategory();
   @ConfigCategory(
      ordinal = 2
   )
   public TooltipCategory tooltip = new TooltipCategory();
   @ConfigCategory(
      ordinal = 5
   )
   public ServerCategory server = new ServerCategory();

   public ItemStackMergingStrategy itemStackMergingStrategy() {
      return this.preview.compactPreviewNbtBehavior;
   }

   public int defaultMaxRowSize() {
      return this.preview.defaultMaxRowSize;
   }

   public boolean shortItemCounts() {
      return this.preview.shortItemCounts;
   }

   public boolean useColors() {
      return false;
   }

   public static class PreviewCategory {
      @Comment("Toggles the shulker box preview.\n(default value: true)")
      public boolean enable = true;
      @Comment("Swaps the preview modes.\nIf true, pressing the preview key will show the full preview instead.\n(default value: false)")
      public boolean swapModes = false;
      @Comment("If on, the preview is always displayed, regardless of the preview key being pressed.\n(default value: false)")
      public boolean alwaysOn = false;
      @Comment("In compact mode, how should items with the same ID but different component data be compacted?\nIGNORE: Ignores component data\nFIRST_ITEM: Items are displayed as all having the same component as the first item\nSEPARATE: Separates items with different component data\n(default value: SEPARATE)")
      public ItemStackMergingStrategy compactPreviewNbtBehavior;
      @Validator(GreaterThanZero.class)
      @Comment("The max number of items in a row.\nMay not affect modded containers.\n(default value: 9)")
      public int defaultMaxRowSize;
      @RequiresRestart
      @Comment("If on, the client will try to send packets to servers to allow extra preview information such as ender chest previews.\n(default value: true)\n")
      public boolean serverIntegration;
      @Comment("The theme to use for preview windows.\nSHULKERBOXTOOLTIP: ShulkerBoxTooltip's default look and feel.\nVANILLA: Mimics the style of vanilla bundle previews.\n(default value: SHULKERBOXTOOLTIP)")
      public Theme theme;
      @Comment("The position of the preview window.\nINSIDE: Inside the item's tooltip.\nOUTSIDE: Outside the item's tooltip, moves depending on the screen borders.\nOUTSIDE_TOP: Always at the top of the item's tooltip.\nOUTSIDE_BOTTOM: Always at the bottom of the item's tooltip.\n(default value: INSIDE)")
      public PreviewPosition position;
      @Comment("If on, large item counts in compact previews will be shortened.\n(default value: true)")
      public boolean shortItemCounts;

      public PreviewCategory() {
         this.compactPreviewNbtBehavior = ItemStackMergingStrategy.SEPARATE;
         this.defaultMaxRowSize = 9;
         this.serverIntegration = true;
         this.theme = Configuration.Theme.SHULKERBOXTOOLTIP;
         this.position = Configuration.PreviewPosition.INSIDE;
         this.shortItemCounts = true;
      }
   }

   public static enum Theme {
      SHULKERBOXTOOLTIP,
      VANILLA;

      public String toString() {
         return "shulkerboxtooltip.config.theme." + this.name().toLowerCase();
      }

      // $FF: synthetic method
      private static Theme[] $values() {
         return new Theme[]{SHULKERBOXTOOLTIP, VANILLA};
      }
   }

   public static enum PreviewPosition {
      INSIDE,
      OUTSIDE,
      OUTSIDE_TOP,
      OUTSIDE_BOTTOM;

      public String toString() {
         return "shulkerboxtooltip.config.preview_position." + this.name().toLowerCase();
      }

      // $FF: synthetic method
      private static PreviewPosition[] $values() {
         return new PreviewPosition[]{INSIDE, OUTSIDE, OUTSIDE_TOP, OUTSIDE_BOTTOM};
      }
   }

   public static class TooltipCategory {
      @Comment("Controls whether the key hints in the container's tooltip should be displayed.\n(default value: true)")
      public boolean showKeyHints = true;
      @Comment("The tooltip to use.\nVANILLA: The vanilla tooltip (shows the first 5 items)\nMOD: The mod's tooltip\nNONE: No tooltip\n(default value: MOD)")
      public ShulkerBoxTooltipType type;
      @Comment("Shows info about the current loot table of the item if present.\nVisible only when Tooltip Type is set to Modded.\nHIDE: No loot table info, default.\nSIMPLE: Displays whether the stack uses a loot table.\nADVANCED: Shows the loot table used by the item.\n(default value: HIDE)")
      public LootTableInfoType lootTableInfoType;
      @Comment("If on, the mod hides the custom text on shulker box tooltips.\nUse this option when a server-side preview data pack clashes with the mod.\n(default value: false)")
      public boolean hideShulkerBoxLore;

      public TooltipCategory() {
         this.type = Configuration.ShulkerBoxTooltipType.MOD;
         this.lootTableInfoType = Configuration.LootTableInfoType.HIDE;
         this.hideShulkerBoxLore = false;
      }
   }

   public static enum ShulkerBoxTooltipType {
      VANILLA,
      MOD,
      NONE;

      public String toString() {
         return "shulkerboxtooltip.config.tooltip_type." + this.name().toLowerCase();
      }

      // $FF: synthetic method
      private static ShulkerBoxTooltipType[] $values() {
         return new ShulkerBoxTooltipType[]{VANILLA, MOD, NONE};
      }
   }

   public static enum LootTableInfoType {
      HIDE,
      SIMPLE,
      ADVANCED;

      public String toString() {
         return "shulkerboxtooltip.config.loot_table_info_type." + this.name().toLowerCase();
      }

      // $FF: synthetic method
      private static LootTableInfoType[] $values() {
         return new LootTableInfoType[]{HIDE, SIMPLE, ADVANCED};
      }
   }

   public static class ServerCategory {
      @Synchronize
      @RequiresRestart
      @Comment("If on, the server will be able to provide extra information about containers to the clients with the mod installed.\nDisabling this option will disable all the options below.\n(default value: true)\n")
      public boolean clientIntegration = true;
      @Synchronize
      @RequiresRestart
      @Comment("Changes the way the ender chest content preview is synchronized.\nNONE: No synchronization, prevents clients from seeing a preview of their ender chest.\nACTIVE: Ender chest contents are synchronized when changed.\nPASSIVE: Ender chest contents are synchronized when the client opens a preview.\n(default value: ACTIVE)")
      public EnderChestSyncType enderChestSyncType;

      public ServerCategory() {
         this.enderChestSyncType = Configuration.EnderChestSyncType.ACTIVE;
      }
   }

   public static enum EnderChestSyncType {
      NONE,
      ACTIVE,
      PASSIVE;

      public String toString() {
         return "shulkerboxtooltip.config.ender_chest_sync_type." + this.name().toLowerCase();
      }

      // $FF: synthetic method
      private static EnderChestSyncType[] $values() {
         return new EnderChestSyncType[]{NONE, ACTIVE, PASSIVE};
      }
   }
}
