package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Augments the vanilla server-list entry rendering with extra info
 * blocks: tier-coloured ping number, full version string, and the
 * raw MOTD (with vanilla legacy color codes preserved). The mixin
 * paints these lines on top of vanilla's row at TAIL of
 * {@code MultiplayerServerListWidget$ServerEntry.render}.
 *
 * <h3>Why a module not always-on</h3>
 * Some users prefer the cleaner vanilla list (player-count icon
 * only). A module toggle keeps the augmentation opt-in and gives
 * users a single switch to turn it off if a server's MOTD breaks
 * the layout.
 */
public final class ServerListPlus extends Module {
    private static final ServerListPlus INSTANCE = new ServerListPlus();

    public final SectionSetting infoSection = new SectionSetting("Info");
    public final BooleanSetting showPingNumber = new BooleanSetting(
            "Show Ping Number",
            "Append the numeric ping (ms) next to the connection-strength icon, tinted by tier"
    ).setValue(true);
    public final BooleanSetting colorPing = new BooleanSetting(
            "Color Ping",
            "Tint the ping number green / yellow / red based on latency tier (<60ms / <150ms / >=150ms)"
    ).setValue(true)
            .visible(() -> showPingNumber.isValue());
    public final BooleanSetting showVersion = new BooleanSetting(
            "Show Version",
            "Display the server's reported game version (e.g. 'Paper 1.21.4') in a small line below the name"
    ).setValue(true);
    public final BooleanSetting showRawMotd = new BooleanSetting(
            "Show Raw MOTD",
            "Replace vanilla's stripped MOTD with the original raw text (preserves legacy color codes / formatting)"
    ).setValue(false);

    public final SectionSetting layoutSection = new SectionSetting("Layout");
    public final BooleanSetting compactRows = new BooleanSetting(
            "Compact Rows",
            "Tighter line spacing so the version + ping fit without overflowing into the next row"
    ).setValue(false);

    private ServerListPlus() {
        super("server_list_plus", "Server List+", ModuleCategory.OTHER, true, false);
        showPingNumber.setFullWidth(true);
        colorPing.setFullWidth(true);
        showVersion.setFullWidth(true);
        showRawMotd.setFullWidth(true);
        compactRows.setFullWidth(true);
        setup(infoSection, showPingNumber, colorPing, showVersion, showRawMotd,
                layoutSection, compactRows);
    }

    public static ServerListPlus getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Adds tier-coloured ping, version, and raw MOTD to the vanilla multiplayer server list";
    }

    @Override
    public String getIcon() {
        return "server_list_plus.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /** Tier color for a given ping (ms). Same thresholds the TPS
     *  HUD / FPS HUD use elsewhere in the client so the visual
     *  language stays consistent. */
    public int colorForPing(long pingMs) {
        if (pingMs < 60) return 0xFF55FF55;
        if (pingMs < 150) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
