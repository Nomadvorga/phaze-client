package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

public final class TimeChanger extends Module {
    private static final TimeChanger INSTANCE = new TimeChanger();
    private boolean originalDaylightCycle = true;
    private long originalTime = -1;
    private int cooldownTicks = 0;

    public static TimeChanger getInstance() {
        return INSTANCE;
    }

    public final ValueSetting timeValue = new ValueSetting("Time", "Set world time (0-24000 ticks)");

    private TimeChanger() {
        super("time_changer", "Time Changer", ModuleCategory.OTHER, true, false);
        timeValue.range(0, 24000).setValue(12000);
        setup(timeValue);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (cooldownTicks > 0) {
                cooldownTicks--;
            }
        });
    }

    public boolean isTimeOverrideActive() {
        return isEnabled() && cooldownTicks == 0;
    }

    @Override
    public String getDescription() {
        return "Change the time of day in the world (client-side, works on any server)";
    }

    @Override
    public String getIcon() {
        return "time_changer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean showIconInSettings() {
        return false;
    }

    @Override
    public void activate() {
        cooldownTicks = 0;
        // In singleplayer, save original time and disable daylight cycle to freeze time
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() != null && client.world != null) {
            ServerWorld world = client.getServer().getWorld(client.world.getRegistryKey());
            if (world != null) {
                originalTime = world.getTimeOfDay();
                originalDaylightCycle = world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).get();
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
            }
        }
    }

    @Override
    public void deactivate() {
        cooldownTicks = 5; // Cooldown to let server time sync
        // In singleplayer, restore original time and daylight cycle to unfreeze time
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() != null && client.world != null) {
            ServerWorld world = client.getServer().getWorld(client.world.getRegistryKey());
            if (world != null) {
                if (originalTime != -1) {
                    world.setTimeOfDay(originalTime);
                    originalTime = -1;
                }
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(originalDaylightCycle, client.getServer());
            }
        }
    }
}
