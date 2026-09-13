package vorga.phazeclient.api.system.discord.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public interface DiscordRPC extends Library {

    DiscordRPC INSTANCE = loadInstance();

    private static DiscordRPC loadInstance() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (!osName.contains("win")) {

                return null;
            }
            String arch = System.getProperty("os.arch", "").toLowerCase();

            String resourcePath = arch.contains("64")
                    ? "/win32-x86-64/discord-rpc.dll"
                    : "/win32-x86/discord-rpc.dll";

            Path dir = Files.createDirectories(
                    Path.of(System.getProperty("java.io.tmpdir"), "phaze-discord-rpc"));
            Path target = dir.resolve("discord-rpc.dll");

            try (InputStream in = DiscordRPC.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return null;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {

                if (!Files.exists(target)) {
                    return null;
                }
            }

            return Native.load(target.toAbsolutePath().toString(), DiscordRPC.class);
        } catch (Throwable t) {

            System.err.println("[Phaze] Discord RPC native load failed: " + t.getMessage());
            return null;
        }
    }

    void Discord_UpdateHandlers(DiscordEventHandlers var1);

    void Discord_UpdatePresence(DiscordRichPresence var1);

    void Discord_Respond(String var1, int var2);

    void Discord_Register(String var1, String var2);

    void Discord_Shutdown();

    void Discord_UpdateConnection();

    void Discord_RegisterSteamGame(String var1, String var2);

    void Discord_RunCallbacks();

    void Discord_Initialize(String var1, DiscordEventHandlers var2, boolean var3, String var4);

    void Discord_ClearPresence();

    enum DiscordReply {
        NO(0),
        IGNORE(2),
        YES(1);

        public final int reply;

        DiscordReply(int reply) {
            this.reply = reply;
        }

        private static DiscordReply[] getReplies() {
            return new DiscordReply[]{NO, YES, IGNORE};
        }
    }
}
