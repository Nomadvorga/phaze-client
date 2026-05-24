package vorga.phazeclient.api.system.discord.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JNA-bound interface to the bundled discord-rpc native library.
 *
 * <h3>Why the lazy {@link #INSTANCE} holder</h3>
 * The DLL ships inside the mod jar at
 * {@code assets/win32-x86-64/discord-rpc.dll} (and a 32-bit twin
 * for legacy JVMs). {@link Native#load(String, Class)} only walks
 * {@code java.library.path} / the OS loader's search list - it
 * does NOT look inside the calling jar's resources. The previous
 * static initialiser called {@code Native.load("discord-rpc", ...)}
 * directly which raised an {@link UnsatisfiedLinkError} the moment
 * the class was first touched (the daemon thread's
 * {@code DiscordRPC.INSTANCE} read on startup), silently aborting
 * the whole module - hence "у меня не работает discord rpc".
 *
 * <p>The {@link #loadInstance} below extracts the bundled DLL to a
 * dedicated temp directory using the JVM's working architecture,
 * then asks JNA to load it from that absolute path. The resulting
 * proxy is cached on first use; failure short-circuits to {@code null}
 * so the rest of the client keeps booting even when the OS rejects
 * the DLL (32-bit-only Discord install on a 64-bit JVM, antivirus
 * quarantine, missing VC redist, etc.) - the daemon thread null-
 * checks the instance and just stops trying without crashing.
 */
public interface DiscordRPC extends Library {
    /** Lazy holder so a load failure surfaces as a {@code null}
     *  rather than a {@link Class#forName} chain throwing
     *  {@link UnsatisfiedLinkError} during the very first reference. */
    DiscordRPC INSTANCE = loadInstance();

    /**
     * Extract the bundled discord-rpc DLL to {@code %TEMP%/phaze-discord-rpc/}
     * and load it via {@link Native#load(String, Class)} using the
     * extracted absolute path. The temp file is reused across launches
     * (no per-startup re-extraction) but the directory is JVM-specific
     * so concurrent Minecraft instances can each load their own copy
     * without stepping on each other's file handles.
     *
     * @return live JNA proxy on success, {@code null} on any I/O or
     *         linker failure.
     */
    private static DiscordRPC loadInstance() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (!osName.contains("win")) {
                // discord-rpc.dll is Windows-only. We don't ship a
                // .so / .dylib equivalent right now, so non-Windows
                // platforms get a null proxy - DiscordManager#init
                // already short-circuits on non-Windows OSes anyway.
                return null;
            }
            String arch = System.getProperty("os.arch", "").toLowerCase();
            // 64-bit JVM on Windows reports {@code amd64} (Oracle/OpenJDK)
            // or {@code x86_64} (Azul/Adoptium on certain platforms).
            // Anything else falls back to the 32-bit DLL.
            String resourcePath = arch.contains("64")
                    ? "/win32-x86-64/discord-rpc.dll"
                    : "/win32-x86/discord-rpc.dll";

            Path dir = Files.createDirectories(
                    Path.of(System.getProperty("java.io.tmpdir"), "phaze-discord-rpc"));
            Path target = dir.resolve("discord-rpc.dll");

            // Always overwrite on extract: if a previous launch
            // wrote a stale DLL (older mod version with different
            // RPC ABI) we want the bundled current version to win.
            try (InputStream in = DiscordRPC.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return null;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {
                // File still in use from a previous Minecraft launch -
                // that's actually fine, JNA can load the existing copy.
                if (!Files.exists(target)) {
                    return null;
                }
            }

            return Native.load(target.toAbsolutePath().toString(), DiscordRPC.class);
        } catch (Throwable t) {
            // Anything from "DLL signature mismatch" through "VC++
            // redist missing" lands here. Returning null lets the
            // rest of the client keep working without RPC.
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
