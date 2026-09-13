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
import vorga.phazeclient.base.util.PhazeAnnouncements;

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

    private boolean deathPending = false;

    public static DeathCoords getInstance() {
        return INSTANCE;
    }

    private DeathCoords() {
        super("death_coords", "Death Coords", ModuleCategory.OTHER);
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

            deathPending = false;
        }
    }

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

            String colored = colorMessage.isValue() ? "§c" + chatLine : chatLine;
            client.inGameHud.getChatHud().addMessage(
                    PhazeAnnouncements.systemMessage(Text.literal(colored))
            );
        }
        if (!"Chat".equalsIgnoreCase(mode)) {

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
