package me.zyouime.hitcolor.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.zyouime.hitcolor.screen.SettingScreen;

public class ModMenuImpl implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return SettingScreen::new;
   }
}
