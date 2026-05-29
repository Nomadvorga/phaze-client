package com.misterpemodder.shulkerboxtooltip;

import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorKey;
import com.misterpemodder.shulkerboxtooltip.api.color.ColorRegistry;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProviderRegistry;
import com.misterpemodder.shulkerboxtooltip.fabric.ShulkerBoxTooltipImpl;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.config.ConfigurationHandler;
import com.misterpemodder.shulkerboxtooltip.impl.network.ServerNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.provider.EnderChestPreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.provider.FixedPreviewProviderRegistry;
import com.misterpemodder.shulkerboxtooltip.impl.provider.InventoryAwarePreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.provider.LecternPreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.provider.ShulkerBoxPreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.tree.RootConfigNode;
import com.misterpemodder.shulkerboxtooltip.impl.util.EnvironmentUtil;
import com.misterpemodder.shulkerboxtooltip.impl.util.NamedLogger;
import com.misterpemodder.shulkerboxtooltip.impl.util.ShulkerBoxTooltipUtil;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.injectables.annotations.ExpectPlatform.Transformed;
import java.nio.file.Path;
import javax.annotation.ParametersAreNonnullByDefault;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2589;
import net.minecraft.class_2595;
import net.minecraft.class_2601;
import net.minecraft.class_2608;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2646;
import net.minecraft.class_3719;
import net.minecraft.class_3720;
import net.minecraft.class_3722;
import net.minecraft.class_3723;
import net.minecraft.class_3866;
import net.minecraft.class_7716;
import net.minecraft.class_8172;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.ApiStatus.Internal;

@ParametersAreNonnullByDefault
@Internal
public class ShulkerBoxTooltip implements ShulkerBoxTooltipApi {
   public static final String MOD_ID = "shulkerboxtooltip";
   public static final String MOD_NAME = "ShulkerBoxTooltip";
   public static final NamedLogger LOGGER = new NamedLogger(LogManager.getFormatterLogger("ShulkerBoxTooltip"));
   public static Configuration config;
   public static Configuration savedConfig;
   public static RootConfigNode<Configuration> configTree;

   public static void init() {
      configTree = RootConfigNode.<Configuration>create(EnvironmentUtil.getInstance().makeConfiguration());
      savedConfig = ConfigurationHandler.register();
      config = EnvironmentUtil.getInstance().makeConfiguration();
      configTree.copy(savedConfig, config);
      ServerNetworking.init();
   }

   public void registerProviders(PreviewProviderRegistry registry) {
      (new FixedPreviewProviderRegistry(registry, ShulkerBoxPreviewProvider::new)).register("shulker_box", 9, class_2627::new, class_2246.field_10603).register("white_shulker_box", 9, class_2627::new, class_2246.field_10199).register("orange_shulker_box", 9, class_2627::new, class_2246.field_10407).register("magenta_shulker_box", 9, class_2627::new, class_2246.field_10063).register("light_blue_shulker_box", 9, class_2627::new, class_2246.field_10203).register("yellow_shulker_box", 9, class_2627::new, class_2246.field_10600).register("lime_shulker_box", 9, class_2627::new, class_2246.field_10275).register("pink_shulker_box", 9, class_2627::new, class_2246.field_10051).register("gray_shulker_box", 9, class_2627::new, class_2246.field_10140).register("light_gray_shulker_box", 9, class_2627::new, class_2246.field_10320).register("cyan_shulker_box", 9, class_2627::new, class_2246.field_10532).register("purple_shulker_box", 9, class_2627::new, class_2246.field_10268).register("blue_shulker_box", 9, class_2627::new, class_2246.field_10605).register("brown_shulker_box", 9, class_2627::new, class_2246.field_10373).register("green_shulker_box", 9, class_2627::new, class_2246.field_10055).register("red_shulker_box", 9, class_2627::new, class_2246.field_10068).register("black_shulker_box", 9, class_2627::new, class_2246.field_10371);
      (new FixedPreviewProviderRegistry(registry, InventoryAwarePreviewProvider::new)).register("chest", 9, class_2595::new, class_2246.field_10034).register("trapped_chest", 9, class_2646::new, class_2246.field_10380).register("barrel", 9, class_3719::new, class_2246.field_16328).register("furnace", 3, class_3866::new, class_2246.field_10181).register("blast_furnace", 3, class_3720::new, class_2246.field_16333).register("smoker", 3, class_3723::new, class_2246.field_16334).register("dropper", 3, class_2608::new, class_2246.field_10228).register("dispenser", 3, class_2601::new, class_2246.field_10200).register("hopper", 5, class_2614::new, class_2246.field_10312).register("brewing_stand", 5, class_2589::new, class_2246.field_10333).register("chiseled_bookshelf", 3, class_7716::new, class_2246.field_40276).register("decorated_pot", 1, class_8172::new, class_2246.field_42752);
      (new FixedPreviewProviderRegistry(registry, LecternPreviewProvider::new)).register("lectern", 1, (pos, state) -> (new class_3722(pos, state)).field_17386, class_2246.field_16330);
      registry.register(ShulkerBoxTooltipUtil.id("ender_chest"), new EnderChestPreviewProvider(), class_1802.field_8466);
   }

   @Environment(EnvType.CLIENT)
   public void registerColors(ColorRegistry registry) {
      registry.defaultCategory().register(ColorKey.DEFAULT, "default").register(ColorKey.ENDER_CHEST, "ender_chest", blockName("ender_chest"));
      registry.category(ShulkerBoxTooltipUtil.id("shulker_boxes")).register(ColorKey.SHULKER_BOX, "shulker_box", blockName("shulker_box")).register(ColorKey.WHITE_SHULKER_BOX, "white_shulker_box", blockName("white_shulker_box")).register(ColorKey.ORANGE_SHULKER_BOX, "orange_shulker_box", blockName("orange_shulker_box")).register(ColorKey.MAGENTA_SHULKER_BOX, "magenta_shulker_box", blockName("magenta_shulker_box")).register(ColorKey.LIGHT_BLUE_SHULKER_BOX, "light_blue_shulker_box", blockName("light_blue_shulker_box")).register(ColorKey.YELLOW_SHULKER_BOX, "yellow_shulker_box", blockName("yellow_shulker_box")).register(ColorKey.LIME_SHULKER_BOX, "lime_shulker_box", blockName("lime_shulker_box")).register(ColorKey.PINK_SHULKER_BOX, "pink_shulker_box", blockName("pink_shulker_box")).register(ColorKey.GRAY_SHULKER_BOX, "gray_shulker_box", blockName("gray_shulker_box")).register(ColorKey.LIGHT_GRAY_SHULKER_BOX, "light_gray_shulker_box", blockName("light_gray_shulker_box")).register(ColorKey.CYAN_SHULKER_BOX, "cyan_shulker_box", blockName("cyan_shulker_box")).register(ColorKey.PURPLE_SHULKER_BOX, "purple_shulker_box", blockName("purple_shulker_box")).register(ColorKey.BLUE_SHULKER_BOX, "blue_shulker_box", blockName("blue_shulker_box")).register(ColorKey.BROWN_SHULKER_BOX, "brown_shulker_box", blockName("brown_shulker_box")).register(ColorKey.GREEN_SHULKER_BOX, "green_shulker_box", blockName("green_shulker_box")).register(ColorKey.RED_SHULKER_BOX, "red_shulker_box", blockName("red_shulker_box")).register(ColorKey.BLACK_SHULKER_BOX, "black_shulker_box", blockName("black_shulker_box"));
   }

   private static String blockName(String block) {
      return "block.minecraft." + block;
   }

   @ExpectPlatform
   @Contract(
      value = "-> _",
      pure = true
   )
   @Transformed
   public static Path getConfigDir() {
      return ShulkerBoxTooltipImpl.getConfigDir();
   }
}
