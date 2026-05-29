package com.misterpemodder.shulkerboxtooltip;

import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.impl.config.ClientConfiguration;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration;
import com.misterpemodder.shulkerboxtooltip.impl.network.ClientNetworking;
import com.misterpemodder.shulkerboxtooltip.impl.util.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_310;
import net.minecraft.class_3675;
import net.minecraft.class_5250;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
@Environment(EnvType.CLIENT)
public class ShulkerBoxTooltipClient {
   private static class_1799 previousStack = null;
   public static class_310 client;
   private static boolean wasPreviewAccessed = false;
   private static boolean previewKeyPressed = false;
   private static boolean fullPreviewKeyPressed = false;
   private static boolean lockPreviewKeyPressed = false;
   private static boolean lockKeyHintsEnabled = false;

   public static void init() {
      client = class_310.method_1551();
      ClientNetworking.init();
   }

   public static ClientConfiguration getConfig() {
      return (ClientConfiguration)ShulkerBoxTooltip.config;
   }

   private static boolean isPreviewRequested() {
      return getConfig().preview.alwaysOn || isPreviewKeyPressed();
   }

   private static List<class_2561> getTooltipHints(PreviewContext context, PreviewProvider provider) {
      if (getConfig().preview.enable && provider.shouldDisplay(context)) {
         boolean previewRequested = isPreviewRequested();
         List<class_2561> hints = new ArrayList();
         class_2561 previewKeyHint = getPreviewKeyTooltipHint(context, provider, previewRequested);
         class_2561 lockKeyHint = getLockKeyTooltipHint(context, provider, previewRequested);
         if (previewKeyHint != null) {
            hints.add(previewKeyHint);
         }

         if (lockKeyHint != null) {
            hints.add(lockKeyHint);
         }

         return hints;
      } else {
         return Collections.emptyList();
      }
   }

   @Nullable
   private static class_2561 getPreviewKeyTooltipHint(PreviewContext context, PreviewProvider provider, boolean previewRequested) {
      if (previewRequested && isFullPreviewKeyPressed()) {
         return null;
      } else {
         boolean fullPreviewAvailable = provider.isFullPreviewAvailable(context);
         if (!fullPreviewAvailable && previewRequested) {
            return null;
         } else {
            class_5250 previewKeyHint = class_2561.method_43470("");
            class_2561 previewKeyText = getConfig().controls.previewKey.get().method_27445();
            if (previewRequested) {
               previewKeyHint.method_10852(getConfig().controls.fullPreviewKey.get().method_27445());
               if (!getConfig().preview.alwaysOn) {
                  previewKeyHint.method_27693("+").method_10852(previewKeyText);
               }
            } else {
               previewKeyHint.method_10852(previewKeyText);
            }

            previewKeyHint.method_27693(": ");
            previewKeyHint.method_27696(class_2583.field_24360.method_10977(class_124.field_1065));
            String contentHint;
            if (ShulkerBoxTooltipApi.getCurrentPreviewType(fullPreviewAvailable) == PreviewType.NO_PREVIEW) {
               contentHint = getConfig().preview.swapModes ? provider.getFullTooltipHintLangKey(context) : provider.getTooltipHintLangKey(context);
            } else {
               contentHint = getConfig().preview.swapModes ? provider.getTooltipHintLangKey(context) : provider.getFullTooltipHintLangKey(context);
            }

            return previewKeyHint.method_10852(class_2561.method_43471(contentHint).method_10862(class_2583.field_24360.method_10977(class_124.field_1068)));
         }
      }
   }

   @Nullable
   private static class_2561 getLockKeyTooltipHint(PreviewContext context, PreviewProvider provider, boolean previewRequested) {
      if (previewRequested && !isLockPreviewKeyPressed() && lockKeyHintsEnabled) {
         class_5250 lockKeyHint = class_2561.method_43470("");
         String lockKeyHintLangKey = provider.getLockKeyTooltipHintLangKey(context);
         lockKeyHint.method_10852(getConfig().controls.lockTooltipKey.get().method_27445());
         lockKeyHint.method_27693(": ");
         lockKeyHint.method_27696(class_2583.field_24360.method_10977(class_124.field_1065));
         lockKeyHint.method_10852(class_2561.method_43471(lockKeyHintLangKey).method_10862(class_2583.field_24360.method_10977(class_124.field_1068)));
         return lockKeyHint;
      } else {
         return null;
      }
   }

   public static void modifyStackTooltip(class_1799 stack, Consumer<Collection<class_2561>> tooltip) {
      if (client != null) {
         PreviewContext context = PreviewContext.builder(stack).withOwner(client.field_1724).build();
         PreviewProvider provider = ShulkerBoxTooltipApi.getPreviewProviderForStackWithOverrides(stack);
         if (provider != null) {
            if (previousStack == null || !class_1799.method_7973(stack, previousStack)) {
               wasPreviewAccessed = false;
            }

            previousStack = stack;
            if (!wasPreviewAccessed) {
               provider.onInventoryAccessStart(context);
            }

            wasPreviewAccessed = true;
            if (provider.showTooltipHints(context)) {
               if (getConfig().tooltip.type == Configuration.ShulkerBoxTooltipType.MOD) {
                  tooltip.accept(provider.addTooltip(context));
               }

               if (getConfig().tooltip.showKeyHints) {
                  tooltip.accept(getTooltipHints(context, provider));
               }
            }

         }
      }
   }

   public static boolean isPreviewAvailable(PreviewContext context) {
      if (!getConfig().preview.enable) {
         return false;
      } else {
         PreviewProvider provider = ShulkerBoxTooltipApi.getPreviewProviderForStackWithOverrides(context.stack());
         return provider != null && provider.shouldDisplay(context) && ShulkerBoxTooltipApi.getCurrentPreviewType(provider.isFullPreviewAvailable(context)) != PreviewType.NO_PREVIEW;
      }
   }

   public static PreviewType getCurrentPreviewType(boolean hasFullPreviewMode) {
      boolean previewRequested = isPreviewRequested();
      if (previewRequested && !hasFullPreviewMode) {
         return PreviewType.COMPACT;
      } else {
         if (getConfig().preview.swapModes) {
            if (previewRequested) {
               return isFullPreviewKeyPressed() ? PreviewType.COMPACT : PreviewType.FULL;
            }
         } else if (previewRequested) {
            return isFullPreviewKeyPressed() ? PreviewType.FULL : PreviewType.COMPACT;
         }

         return PreviewType.NO_PREVIEW;
      }
   }

   public static boolean isPreviewKeyPressed() {
      return previewKeyPressed;
   }

   public static boolean isFullPreviewKeyPressed() {
      return fullPreviewKeyPressed;
   }

   public static boolean isLockPreviewKeyPressed() {
      return lockPreviewKeyPressed;
   }

   public static void setLockKeyHintsEnabled(boolean value) {
      lockKeyHintsEnabled = value;
   }

   private static boolean isKeyPressed(@Nullable Key key) {
      return key != null && !key.equals(Key.UNKNOWN_KEY) && !key.isUnbound() ? class_3675.method_15987(class_310.method_1551().method_22683().method_4490(), key.get().method_1444()) : false;
   }

   public static void updatePreviewKeys() {
      if (getConfig() == null) {
         previewKeyPressed = false;
         fullPreviewKeyPressed = false;
         lockPreviewKeyPressed = false;
      } else {
         previewKeyPressed = isKeyPressed(getConfig().controls.previewKey);
         fullPreviewKeyPressed = isKeyPressed(getConfig().controls.fullPreviewKey);
         lockPreviewKeyPressed = isKeyPressed(getConfig().controls.lockTooltipKey);
      }

   }
}
