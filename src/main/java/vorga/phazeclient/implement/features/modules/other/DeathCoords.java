package vorga.phazeclient.implement.features.modules.other;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;

/**
 * Records the player's coordinates the moment they die so the user
 * can later return for their dropped items. By default the line is
 * printed as a client-only chat message ("Died at X Y Z in
 * dimension D"); the user can switch to copying coordinates into
 * the system clipboard, or both.
 *
 * <h3>Detection</h3>
 * Vanilla doesn't fire a clean "you just died" event on the client,
 * so we sample the player's HP each tick. A 1->0 transition with
 * the player still attached to the world is the death marker; we
 * latch a "death pending" flag and reset it once HP comes back
 * above zero (respawn).
 *
 * <h3>Output customisation</h3>
 * <ul>
 *   <li><b>Output Mode</b> - Chat / Clipboard / Both. Chat prints
 *       a client-only message (server doesn't see it). Clipboard
 *       replaces the system clipboard contents with just the raw
 *       coords for fast pasting into a notes app.</li>
 *   <li><b>Format</b> - Long ("X: 100 Y: 64 Z: -200, Overworld") or
 *       Short ("100 64 -200"). Short skips the dimension label.</li>
 *   <li><b>Color</b> - tint the chat message; off = vanilla white.</li>
 *   <li><b>Custom Prefix</b> - replace the default "[Death]" prefix
 *       with whatever the user types.</li>
 * </ul>
 */
public final class DeathCoords extends Module {
    private static final DeathCoords INSTANCE = new DeathCoords();

    public final SectionSetting outputSection = new SectionSetting("Output");
    public final SelectSetting outputMode = new SelectSetting(
            "Output Mode",
            "Where to send the recorded coordinates"
    ).value("Chat", "Clipboard", "Both").selected("Chat");
    public final SelectSetting format = new SelectSetting(
            "Format",
            "Long: full message with labels and dimension. Short: raw 'X Y Z' triplet."
    ).value("Long", "Short").selected("Long");
    public final TextSetting customPrefix = new TextSetting(
            "Prefix",
            "Tag prepended to the chat message"
    ).setText("[Death]").setMax(24);

    public final SectionSetting visualSection = new SectionSetting("Visual");
    public final BooleanSetting colorMessage = new BooleanSetting(
            "Color Message",
            "Tint the chat message red so it stands out from regular chat"
    ).setValue(true);
    public final BooleanSetting playSound = new BooleanSetting(
            "Play Sound",
            "Play the experience-orb pickup sound when the death message fires"
    ).setValue(false);

    /** Latched on the HP 1->0 transition; consumed once the message
     *  fires. Prevents repeat-printing while the death screen is up. */
    private boolean deathPending = false;

    public static DeathCoords getInstance() {
        return INSTANCE;
    }

    private DeathCoords() {
        super("death_coords", "Death Coords", ModuleCategory.UTILITIES);
        outputMode.setFullWidth(true);
        format.setFullWidth(true);
        customPrefix.setFullWidth(true);
        colorMessage.setFullWidth(true);
        playSound.setFullWidth(true);
        setup(outputSection, outputMode, format, customPrefix,
                visualSection, colorMessage, playSound);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    @Override
    public String getDescription() {
        return "Records your coords on death so you can return for your dropped items";
    }

    @Override
    public String getIcon() {
        return "death_coords.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    private void tick(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null) return;
        ClientPlayerEntity player = client.player;
        if (player.getHealth() <= 0.0F && !deathPending) {
            deathPending = true;
            recordDeath(client, player);
        } else if (player.getHealth() > 0.0F && deathPending) {
            // Player respawned - reset the latch so the next death
            // will record again.
            deathPending = false;
        }
    }

    /** Build + dispatch the death message to the configured output. */
    private void recordDeath(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        String dim = client.world != null
                ? client.world.getRegistryKey().getValue().getPath()
                : "unknown";
        boolean shortFmt = "Short".equalsIgnoreCase(format.getSelected());
        String line = shortFmt
                ? pos.getX() + " " + pos.getY() + " " + pos.getZ()
                : "X: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ() + ", " + dim;

        String mode = outputMode.getSelected();
        if (!"Clipboard".equalsIgnoreCase(mode)) {
            String prefix = customPrefix.getText() == null || customPrefix.getText().isEmpty()
                    ? "[Death]" : customPrefix.getText();
            String chatLine = prefix + " " + line;
            // Color via legacy formatting code embedded in the message.
            // ClientPlayerEntity.sendMessage(text, false) prints a
            // client-only message - the server doesn't receive it.
            String colored = colorMessage.isValue() ? "§c" + chatLine : chatLine;
            client.inGameHud.getChatHud().addMessage(Text.literal(colored));
        }
        if (!"Chat".equalsIgnoreCase(mode)) {
            // Clipboard variant gets just the raw coords (no prefix /
            // dimension label) so the user can paste into anything
            // expecting a coordinate triplet.
            String clip = shortFmt ? line
                    : pos.getX() + " " + pos.getY() + " " + pos.getZ();
            client.keyboard.setClipboard(clip);
        }
        if (playSound.isValue()) {
            client.player.playSound(
                    net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    1.0F, 1.0F);
        }
    }
}
