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

    public void init() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("win")) {
            System.out.println("Discord RPC is disabled on non-Windows systems (detected OS: " + osName + ")");
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
        String imageUrl = "https://i.imgur.com/xbQBccj.jpg";

        DiscordRichPresence.Builder builder = new DiscordRichPresence.Builder()
                .setStartTimestamp(startTimestamp)
                .setLargeImage(imageUrl, "Phaze Client")
                .setState("Phaze Client");

        DiscordRPC.INSTANCE.Discord_UpdatePresence(builder.build());
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
