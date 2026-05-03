package vorga.phazeclient.core;

import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import vorga.phazeclient.api.feature.module.ModuleProvider;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.CoordinatesHud;
import vorga.phazeclient.implement.features.modules.hud.CpsHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.features.modules.hud.FpsHud;
import vorga.phazeclient.implement.features.modules.hud.KeystrokesHud;
import vorga.phazeclient.implement.features.modules.hud.PingHud;
import vorga.phazeclient.implement.features.modules.hud.PotionHud;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.StatsHud;
import vorga.phazeclient.implement.features.modules.hud.SprintHud;
import vorga.phazeclient.implement.features.modules.hud.TabHud;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.hud.MemoryHud;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.hud.ServerAddressHud;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.features.modules.other.TimeChanger;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;
import vorga.phazeclient.implement.features.modules.other.Saturation;

import java.util.ArrayList;

@Getter
public class Main implements ModInitializer {
    private static Main instance;
    private final ScissorManager scissorManager = new ScissorManager();
    private final ModuleProvider moduleProvider = new ModuleProvider(new ArrayList<>());
    private final ConfigManager configManager = new ConfigManager();

    public Main() {
        instance = this;
    }

    public static Main getInstance() {
        if (instance == null) {
            instance = new Main();
        }
        return instance;
    }

    @Override
    public void onInitialize() {
        if (moduleProvider.get(Theme.class) == null) {
            moduleProvider.getModules().add(Theme.getInstance());
        }
        if (moduleProvider.get(TimeChanger.class) == null) {
            moduleProvider.getModules().add(TimeChanger.getInstance());
        }
        if (moduleProvider.get(WeatherChanger.class) == null) {
            moduleProvider.getModules().add(WeatherChanger.getInstance());
        }
        if (moduleProvider.get(AutoSprint.class) == null) {
            moduleProvider.getModules().add(AutoSprint.getInstance());
        }
        if (moduleProvider.get(FpsHud.class) == null) {
            moduleProvider.getModules().add(FpsHud.getInstance());
        }
        if (moduleProvider.get(CpsHud.class) == null) {
            moduleProvider.getModules().add(CpsHud.getInstance());
        }
        if (moduleProvider.get(ReachHud.class) == null) {
            moduleProvider.getModules().add(ReachHud.getInstance());
        }
        if (moduleProvider.get(ArmorHud.class) == null) {
            moduleProvider.getModules().add(ArmorHud.getInstance());
            ArmorHud.getInstance().setState(true);
        }
        if (moduleProvider.get(SprintHud.class) == null) {
            moduleProvider.getModules().add(SprintHud.getInstance());
        }
        if (moduleProvider.get(CoordinatesHud.class) == null) {
            moduleProvider.getModules().add(CoordinatesHud.getInstance());
        }
        if (moduleProvider.get(PingHud.class) == null) {
            moduleProvider.getModules().add(PingHud.getInstance());
        }
        if (moduleProvider.get(KeystrokesHud.class) == null) {
            moduleProvider.getModules().add(KeystrokesHud.getInstance());
        }
        if (moduleProvider.get(PotionHud.class) == null) {
            moduleProvider.getModules().add(PotionHud.getInstance());
        }
        if (moduleProvider.get(DayCounterHud.class) == null) {
            moduleProvider.getModules().add(DayCounterHud.getInstance());
        }
        if (moduleProvider.get(StatsHud.class) == null) {
            moduleProvider.getModules().add(StatsHud.getInstance());
        }
        if (moduleProvider.get(TabHud.class) == null) {
            moduleProvider.getModules().add(TabHud.getInstance());
        }
        if (moduleProvider.get(NametagHud.class) == null) {
            moduleProvider.getModules().add(NametagHud.getInstance());
        }
        if (moduleProvider.get(TimeHud.class) == null) {
            moduleProvider.getModules().add(TimeHud.getInstance());
        }
        if (moduleProvider.get(SessionTimeHud.class) == null) {
            moduleProvider.getModules().add(SessionTimeHud.getInstance());
        }
        if (moduleProvider.get(MemoryHud.class) == null) {
            moduleProvider.getModules().add(MemoryHud.getInstance());
        }
        if (moduleProvider.get(ComboCounterHud.class) == null) {
            moduleProvider.getModules().add(ComboCounterHud.getInstance());
        }
        if (moduleProvider.get(ServerAddressHud.class) == null) {
            moduleProvider.getModules().add(ServerAddressHud.getInstance());
        }
        if (moduleProvider.get(Saturation.class) == null) {
            moduleProvider.getModules().add(Saturation.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HitColor.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HitColor.getInstance());
        }
// TODO: ScoreboardHud temporarily disabled for debugging
        // if (moduleProvider.get(ScoreboardHud.class) == null) {
        //     moduleProvider.getModules().add(ScoreboardHud.getInstance());
        // }

        configManager.loadCurrentConfig();
    }
}
