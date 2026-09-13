package vorga.phazeclient.api.system.discord;

import vorga.phazeclient.api.system.discord.utils.DiscordEventHandlers;
import vorga.phazeclient.api.system.discord.utils.DiscordRPC;
import vorga.phazeclient.api.system.discord.utils.DiscordRichPresence;
import vorga.phazeclient.api.system.discord.utils.RPCButton;
import vorga.phazeclient.core.Main;

import java.time.LocalDate;
import java.time.Month;

public class DiscordManager {
    private final DiscordDaemonThread discordDaemonThread = new DiscordDaemonThread();
    private boolean running = true;
    private DiscordInfo info = new DiscordInfo("Unknown", "", "");
    private long startTimestamp = System.currentTimeMillis() / 1000;

    private long worldJoinTimestamp = System.currentTimeMillis() / 1000;

    public void init() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("win")) {
            System.out.println("Discord RPC is disabled on non-Windows systems (detected OS: " + osName + ")");
            this.running = false;
            return;
        }

        if (DiscordRPC.INSTANCE == null) {
            System.err.println("[Phaze] Discord RPC native unavailable - module disabled for this session");
            this.running = false;
            return;
        }

        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder().ready((user) -> {
            Main.getInstance().getDiscordManager().setInfo(
                    new DiscordInfo(
                            user.username,
                            "https://cdn.discordapp.com/avatars/" + user.userId + "/" + user.avatar + ".png",
                            user.userId
                    )
            );

            updatePresence();
        }).build();

        DiscordRPC.INSTANCE.Discord_Initialize("1500846872087105639", handlers, true, "");
        discordDaemonThread.start();
    }

    public void stopRPC() {
        DiscordRPC.INSTANCE.Discord_Shutdown();
        this.running = false;
    }

    private void updatePresence() {
        String imageUrl = "https://i.imgur.com/wJLeuBG.gif";

        vorga.phazeclient.implement.features.modules.other.DiscordRpc rpc =
                resolveRpcModule();

        DiscordRichPresence.Builder builder = new DiscordRichPresence.Builder()
                .setLargeImage(imageUrl, "Phaze Client");

        if (rpc == null || !rpc.isEnabled()) {

            DiscordRPC.INSTANCE.Discord_ClearPresence();
            return;
        }

        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        boolean inWorld = mc != null && mc.world != null && mc.player != null;
        if (rpc.hideInMenus.isValue() && !inWorld) {
            DiscordRPC.INSTANCE.Discord_ClearPresence();
            return;
        }

        String server;
        if (mc != null && mc.getCurrentServerEntry() != null) {
            String addr = mc.getCurrentServerEntry().address;
            server = rpc.showServerName.isValue()
                    ? (addr != null ? addr : "Multiplayer")
                    : "Multiplayer";
        } else {
            server = "Singleplayer";
        }

        String dimension = inWorld
                ? mc.world.getRegistryKey().getValue().getPath()
                : "menu";

        String player = inWorld ? mc.player.getGameProfile().name() : "";
        String gamemode = "?";
        if (inWorld && mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() != null) {

            gamemode = mc.interactionManager.getCurrentGameMode().getId();
        }
        String health = inWorld ? String.valueOf((int) mc.player.getHealth()) : "0";

        String details = applyTokens(rpc.detailsTemplate.getText(), server, dimension, player, gamemode, health);
        String state = applyTokens(rpc.stateTemplate.getText(), server, dimension, player, gamemode, health);
        if (!details.isEmpty()) builder.setDetails(details);
        if (!state.isEmpty()) builder.setState(state);

        switch (rpc.elapsedMode.getSelected()) {
            case "World" -> builder.setStartTimestamp(worldJoinTimestamp);
            case "Off" -> {   }
            default -> builder.setStartTimestamp(startTimestamp);
        }

        builder.setButtons(new RPCButton("Telegram Channel", "https://t.me/PhazeClient"));

        DiscordRPC.INSTANCE.Discord_UpdatePresence(builder.build());
    }

    private vorga.phazeclient.implement.features.modules.other.DiscordRpc resolveRpcModule() {
        try {
            return vorga.phazeclient.implement.features.modules.other.DiscordRpc.getInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String applyTokens(String template, String server, String dimension,
                                      String player, String gamemode, String health) {
        if (template == null || template.isEmpty()) return "";
        return template
                .replace("{server}", server)
                .replace("{dimension}", dimension)
                .replace("{player}", player)
                .replace("{gamemode}", gamemode)
                .replace("{health}", health);
    }

    public void onWorldJoin() {
        worldJoinTimestamp = System.currentTimeMillis() / 1000;
    }

    private class DiscordDaemonThread extends Thread {
        @Override
        public void run() {
            this.setName("Discord-RPC");

            try {
                while (Main.getInstance().getDiscordManager().isRunning()) {
                    DiscordRPC.INSTANCE.Discord_RunCallbacks();
                    updatePresence();
                    Thread.sleep(15000);
                }
            } catch (Exception exception) {
                System.err.println("Stop Discord RPC " + exception.getMessage());
                stopRPC();
            }
            super.run();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public DiscordInfo getInfo() {
        return info;
    }

    public void setInfo(DiscordInfo info) {
        this.info = info;
    }

    public record DiscordInfo(String userName, String avatarUrl, String userId) {
    }
}
