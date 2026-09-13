package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;

public final class AutoRespawn extends Module {
    private static final AutoRespawn INSTANCE = new AutoRespawn();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting commandToggle = new BooleanSetting("Command", "Send a command after the auto-respawn completes")
            .setValue(false);
    public final TextSetting commandText = new TextSetting("Command", "Command to send after respawn (leading slash optional)")
            .setText("home")
            .visible(commandToggle::isValue);

    private boolean commandPending = false;

    private boolean respawnRequested = false;

    private AutoRespawn() {
        super("auto_respawn", "Auto Respawn", ModuleCategory.UTILITIES);
        commandToggle.setFullWidth(true);
        commandText.setFullWidth(true);
        setup(generalSection, commandToggle, commandText);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public static AutoRespawn getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically respawns and optionally runs a command after death";
    }

    @Override
    public String getIcon() {
        return "auto_respawn.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public void deactivate() {

        commandPending = false;
        respawnRequested = false;
    }

    private void tick(MinecraftClient mc) {
        if (!isEnabled()) {
            return;
        }
        if (mc == null || mc.player == null) {
            return;
        }

        boolean onDeathScreen = mc.currentScreen instanceof DeathScreen;

        if (onDeathScreen) {
            if (!respawnRequested) {
                mc.player.requestRespawn();

                mc.setScreen(null);
                respawnRequested = true;
                commandPending = commandToggle.isValue();
            }
            return;
        }

        respawnRequested = false;
        if (commandPending && mc.player.isAlive() && mc.getNetworkHandler() != null) {
            String raw = commandText.getText();
            if (raw == null) {
                commandPending = false;
                return;
            }
            String cleaned = raw.trim();
            if (cleaned.startsWith("/")) {
                cleaned = cleaned.substring(1).trim();
            }
            if (!cleaned.isEmpty()) {
                mc.getNetworkHandler().sendChatCommand(cleaned);
            }
            commandPending = false;
        }
    }
}
