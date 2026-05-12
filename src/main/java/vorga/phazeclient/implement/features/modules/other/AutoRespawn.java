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

/**
 * Auto Respawn. While enabled, any time the {@link DeathScreen} is
 * the current screen the client immediately fires {@link
 * ClientPlayerEntity#requestRespawn()} on the player's behalf, then
 * closes the death screen so the user never sees more than a single
 * frame of "You died".
 *
 * <p>Optional {@code Command} toggle: when ON, a configurable chat
 * command is sent once the player is alive again (DeathScreen gone,
 * {@code mc.player.isAlive()} reports true). The command runs at
 * most once per death so a server-side throttle on the bound command
 * (e.g. spawn /home) isn't tripped by repeated firings while the
 * post-respawn world reload is still settling.
 *
 * <p>The command text is sent VERBATIM minus a leading slash if
 * present - the chat network handler's {@code sendChatCommand}
 * expects the command WITHOUT the leading slash, while users
 * typing into the input field often add it instinctively. We strip
 * one optional leading {@code '/'} to handle both styles.
 */
public final class AutoRespawn extends Module {
    private static final AutoRespawn INSTANCE = new AutoRespawn();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting commandToggle = new BooleanSetting("Command", "Send a command after the auto-respawn completes")
            .setValue(false);
    public final TextSetting commandText = new TextSetting("Command", "Command to send after respawn (leading slash optional)")
            .setText("home")
            .visible(commandToggle::isValue);

    /**
     * Set to {@code true} the tick we invoke {@code requestRespawn()};
     * the actual command send is deferred until the next tick where
     * the player is alive and the death screen is gone, so the
     * command lands AFTER the server-side respawn world load
     * completes rather than racing it.
     */
    private boolean commandPending = false;

    /**
     * Tracks whether we've already issued requestRespawn for the
     * current death event. Without this flag the DeathScreen would
     * be re-detected on every tick of the brief gap between the
     * client-side respawn request and the server's TeleportS2C
     * actually closing the screen, and we'd spam requestRespawn()
     * packets at full client-tick rate.
     */
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
        // Reset latches so the next time the module is enabled the
        // state machine starts clean - otherwise a leftover
        // commandPending from a previous death could fire a stale
        // command the moment the user re-enables the module.
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
                // Close the death screen explicitly. requestRespawn
                // schedules the actual respawn packet exchange but
                // leaves the screen open until the server replies,
                // which on laggy connections is a visible black flash.
                mc.setScreen(null);
                respawnRequested = true;
                commandPending = commandToggle.isValue();
            }
            return;
        }

        // Once the death screen is closed AND the player is alive
        // again, drain the pending command latch. Checking isAlive()
        // (rather than just "screen != DeathScreen") avoids the
        // brief window where the screen is dismissed but the
        // server-side respawn hasn't finished healing the player
        // yet - a command sent there can race the world reload and
        // bounce off as "you are not in a world".
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
