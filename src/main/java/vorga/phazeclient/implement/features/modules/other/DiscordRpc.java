package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;

public final class DiscordRpc extends Module {
    private static final DiscordRpc INSTANCE = new DiscordRpc();

    public final SectionSetting linesSection = new SectionSetting("Lines");
    public final TextSetting detailsTemplate = new TextSetting(
            "Details",
            "Top line. Tokens: {server} {dimension} {player} {gamemode} {health}"
    ).setText("Playing on {server}").setMax(96);
    public final TextSetting stateTemplate = new TextSetting(
            "State",
            "Bottom line. Same tokens as Details."
    ).setText("In {dimension} as {player}").setMax(96);

    public final SectionSetting privacySection = new SectionSetting("Privacy");
    public final BooleanSetting showServerName = new BooleanSetting(
            "Show Server Name",
            "When OFF, the {server} token is replaced with 'Singleplayer' for any non-singleplayer session too. Use this if you don't want your server hostname visible in your Discord status."
    ).setValue(true);
    public final BooleanSetting hideInMenus = new BooleanSetting(
            "Hide In Menus",
            "Stop sending the rich presence when you're in the main menu / title screen so your status reads 'idle' instead of 'Playing'"
    ).setValue(false);

    public final SectionSetting timestampSection = new SectionSetting("Timestamp");
    public final SelectSetting elapsedMode = new SelectSetting(
            "Elapsed Time",
            "Session: time since the client launched. World: resets when you join a world. Off: no elapsed badge."
    ).value("Session", "World", "Off").selected("Session");

    private DiscordRpc() {
        super("discord_rpc", "Discord RPC", ModuleCategory.OTHER, true, false);
        detailsTemplate.setFullWidth(true);
        stateTemplate.setFullWidth(true);
        showServerName.setFullWidth(true);
        hideInMenus.setFullWidth(true);
        elapsedMode.setFullWidth(true);
        setup(linesSection, detailsTemplate, stateTemplate,
                privacySection, showServerName, hideInMenus,
                timestampSection, elapsedMode);
    }

    public static DiscordRpc getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Discord Rich Presence: customisable Playing-on-server card with templated lines";
    }

    @Override
    public String getIcon() {
        return "discord_rpc.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}
