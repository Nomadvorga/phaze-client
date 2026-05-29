package io.github.imurx.screenshotcopy.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.imurx.screenshotcopy.ScreencopyConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.class_437;

public class ScreencopyMenuIntegration implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return (parent) -> (class_437)AutoConfig.getConfigScreen(ScreencopyConfig.class, parent).get();
   }
}
