package vorga.phazeclient.core;

import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import vorga.phazeclient.api.feature.module.ModuleProvider;
import vorga.phazeclient.api.system.discord.DiscordManager;
import vorga.phazeclient.base.util.HolyWorldFeatureControlService;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.implement.features.modules.hud.ArmorHud;
import vorga.phazeclient.implement.features.modules.hud.CoordinatesHud;
import vorga.phazeclient.implement.features.modules.hud.CpsHud;
import vorga.phazeclient.implement.features.modules.hud.DayCounterHud;
import vorga.phazeclient.implement.config.ConfigManager;
import vorga.phazeclient.implement.features.modules.hud.FpsHud;
import vorga.phazeclient.implement.features.modules.hud.KeystrokesHud;
import vorga.phazeclient.implement.features.modules.hud.MovementSpeedHud;
import vorga.phazeclient.implement.features.modules.hud.PingHud;
import vorga.phazeclient.implement.features.modules.hud.PotionHud;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.hud.ScoreboardHud;
import vorga.phazeclient.implement.features.modules.hud.ServerAddressHud;
import vorga.phazeclient.implement.features.modules.hud.SessionTimeHud;
import vorga.phazeclient.implement.features.modules.hud.SprintHud;
import vorga.phazeclient.implement.features.modules.hud.TabHud;
import vorga.phazeclient.implement.features.modules.hud.TimeHud;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.hud.MemoryHud;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.hud.WailaHud;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.features.modules.other.AutoSprint;
import vorga.phazeclient.implement.features.modules.other.TimeChanger;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;
import vorga.phazeclient.implement.features.modules.other.Saturation;
import vorga.phazeclient.implement.features.modules.other.Zoom;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;
import vorga.phazeclient.implement.features.modules.other.ItemPhysics;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;

import java.util.ArrayList;

@Getter
public class Main implements ModInitializer {
    private static Main instance;
    private final ScissorManager scissorManager = new ScissorManager();
    private final ModuleProvider moduleProvider = new ModuleProvider(new ArrayList<>());
    private final ConfigManager configManager = ConfigManager.getInstance();
    private final DiscordManager discordManager = new DiscordManager();

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
        HolyWorldFeatureControlService.getInstance().init();

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
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.hud.Cooldowns.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.hud.Cooldowns.getInstance());
        }
        if (moduleProvider.get(ServerAddressHud.class) == null) {
            moduleProvider.getModules().add(ServerAddressHud.getInstance());
        }
        if (moduleProvider.get(MovementSpeedHud.class) == null) {
            moduleProvider.getModules().add(MovementSpeedHud.getInstance());
        }
        if (moduleProvider.get(WailaHud.class) == null) {
            moduleProvider.getModules().add(WailaHud.getInstance());
        }
        if (moduleProvider.get(Saturation.class) == null) {
            moduleProvider.getModules().add(Saturation.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HitColor.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HitColor.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.FakeFps.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.FakeFps.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.SmoothCamera.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.SmoothCamera.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.BetterF3.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.BetterF3.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.BetterDeathScreen.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.BetterDeathScreen.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.hud.InventoryHud.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.hud.InventoryHud.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.hud.PlayerModelHud.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.hud.PlayerModelHud.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.hud.TpsHud.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.hud.TpsHud.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.BlockOverlay.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.BlockOverlay.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.Crosshair.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.Crosshair.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.DeathCoords.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.DeathCoords.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.DiscordRpc.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.DiscordRpc.getInstance());
        }
        if (moduleProvider.get(Zoom.class) == null) {
            moduleProvider.getModules().add(Zoom.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AutoSwap.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AutoSwap.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ShiftTap.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ShiftTap.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AutoNear.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AutoNear.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AutoReissue.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AutoReissue.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HudOptimizer.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HudOptimizer.getInstance());
            vorga.phazeclient.implement.features.modules.other.HudOptimizer.getInstance().setState(true);
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.FreeLook.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.FreeLook.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.PotionAuto.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.PotionAuto.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HitboxCustomizer.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HitboxCustomizer.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.LockSlot.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.LockSlot.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.MouseClicker.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.MouseClicker.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ElytraUtility.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ElytraUtility.getInstance());
        }
        if (moduleProvider.get(MotionBlur.class) == null) {
            moduleProvider.getModules().add(MotionBlur.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HealthIndicator.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HealthIndicator.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.Translator.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.Translator.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.NoFluid.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.NoFluid.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.TotemTracker.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.TotemTracker.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.hud.Consumable.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.hud.Consumable.getInstance());
        }
        if (moduleProvider.get(ItemPhysics.class) == null) {
            moduleProvider.getModules().add(ItemPhysics.getInstance());
        }
        if (moduleProvider.get(ColorCorrection.class) == null) {
            moduleProvider.getModules().add(ColorCorrection.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.FastExp.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.FastExp.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ItemPickupLogger.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ItemPickupLogger.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AutoEat.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AutoEat.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ArmorNotifier.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ArmorNotifier.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ChatHelper.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ChatHelper.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.FTHelper.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.FTHelper.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.TrapTimer.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.TrapTimer.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.PickaxeNotifier.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.PickaxeNotifier.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ItemHighlighter.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ItemHighlighter.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.Predictions.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.Predictions.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.NickHider.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.NickHider.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.StreamerMode.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.StreamerMode.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HealingHelper.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HealingHelper.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ShulkerPreview.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ShulkerPreview.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.Animations.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.Animations.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.Bright.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.Bright.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AspectRatio.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AspectRatio.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AutoGG.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AutoGG.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ItemScroller.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ItemScroller.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.NoRender.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.NoRender.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ChangeHand.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ChangeHand.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.HitRange.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.HitRange.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AucHelper.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AucHelper.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.Binds.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.Binds.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.MentionHighlight.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.MentionHighlight.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.SkyCustomizer.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.SkyCustomizer.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.CustomFog.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.CustomFog.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.AutoRespawn.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.AutoRespawn.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.MaceIndicator.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.MaceIndicator.getInstance());
        }
        if (moduleProvider.get(vorga.phazeclient.implement.features.modules.other.ChunkAnimator.class) == null) {
            moduleProvider.getModules().add(vorga.phazeclient.implement.features.modules.other.ChunkAnimator.getInstance());
        }
        if (moduleProvider.get(ScoreboardHud.class) == null) {
            moduleProvider.getModules().add(ScoreboardHud.getInstance());
        }

        configManager.loadCurrentConfig();

        // Auto-save: every Setting.notifyChange() (BindSetting.setKey,
        // BooleanSetting.setValue, sliders, color pickers, ...) flips the
        // dirty flag; flushIfDirty drains it on a tick if the debounce
        // window has elapsed. Listener is wired AFTER loadCurrentConfig so
        // load-time setValue calls don't immediately mark the config dirty.
        vorga.phazeclient.api.feature.module.setting.Setting.setGlobalChangeListener(
                setting -> configManager.markDirty()
        );
        // Module enable / disable + keybind changes go through a
        // separate state-change channel (Module.notifyStateChange);
        // wire it to the same dirty flag so toggling a module from the
        // GUI / a hotkey is persisted by the next flushIfDirty tick.
        // Without this, the `globalStateChangeListener` is null, so
        // toggling a module never marks the config dirty, and on a
        // crash within ~250ms of the toggle (or before the user makes
        // any OTHER auto-saved change), the enabled state on disk
        // remains stale - the exact "modules turn off on crash"
        // symptom users hit. Hooking it AFTER loadCurrentConfig is
        // critical: load-time setStateSilent calls would otherwise
        // immediately re-mark the config dirty and trigger a save
        // pass that overwrites the just-loaded data.
        vorga.phazeclient.api.feature.module.Module.setGlobalStateChangeListener(
                module -> configManager.flushNow()
        );
        configManager.enableAutoSave();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> configManager.flushIfDirty()
        );

        // Last-chance save on JVM shutdown. Catches Alt+F4 / window
        // close / SIGTERM that ClientLifecycleEvents.CLIENT_STOPPING
        // doesn't fire on, so any change made within the debounce
        // window before a hard exit still persists. Hard kills (kill
        // -9 / process tree termination from Task Manager) bypass
        // shutdown hooks, but those are not recoverable in any
        // user-mode code.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                configManager.saveCurrentConfig();
            } catch (Throwable ignored) {
                // Shutdown hooks must never throw - the JVM is already
                // tearing down and any exception here is unobservable.
            }
        }, "phaze-config-shutdown"));

        // Final save on game close. The tick-based debounce can miss a
        // change made within ~250ms of quitting; this catches it. We call
        // saveCurrentConfig() unconditionally rather than flushIfDirty()
        // because dirty flag may be cleared by an unrelated flush moments
        // earlier - re-saving a clean config is cheap.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> configManager.saveCurrentConfig()
        );

        // JVM shutdown hook as the final safety net. CLIENT_STOPPING
        // only fires on a clean Minecraft shutdown path; it gets
        // skipped entirely when the game crashes with an uncaught
        // exception (the more common case for users reporting "my
        // settings reset after a crash"). Shutdown hooks fire on most
        // JVM exit paths INCLUDING uncaught exception crashes and the
        // window-close X button, so adding one catches the crash case
        // the lifecycle event misses. We swallow exceptions because
        // we're already inside a shutdown sequence and have nothing
        // useful to do with an error here.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                configManager.saveCurrentConfig();
            } catch (Throwable ignored) {
            }
        }, "Phaze-ShutdownSave"));

        discordManager.init();

        // Kick off the remote-rules poller. It runs on a daemon thread,
        // is fail-open (network errors -> no extra blocks), and feeds
        // Module#isServerLocked so any rule pushed from the admin panel
        // takes effect within ~60s without the player relogging.
        // Override the API base with -Dphaze.rules.api=https://... ;
        // setting it to "" disables the service entirely (offline dev).
        vorga.phazeclient.base.util.RemoteRulesService.getInstance().start();
    }
}
