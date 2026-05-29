package io.github.imurx.screenshotcopy;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

@Config(
   name = "screencopy"
)
public class ScreencopyConfig implements ConfigData {
   @Tooltip
   public boolean saveScreenshot = true;
}
