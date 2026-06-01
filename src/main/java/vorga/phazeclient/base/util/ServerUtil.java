package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Utility class for server detection
 */
public class ServerUtil {

    /**
     * Get current server host address. Returns "" for singleplayer or
     * when no network handler is available. Public so the remote-rules
     * service can read it directly without re-implementing the logic.
     *
     * <p>Resolution order, falling through to the next on null:
     * <ol>
     *   <li>{@code mc.getCurrentServerEntry()} - the entry the player
     *       opened, includes the address as typed in the server list /
     *       direct-connect dialog. This survives across server-transfer
     *       packets ({@code dexland} -> {@code ru.dexland.org}) where
     *       {@code networkHandler.getServerInfo()} can go null.</li>
     *   <li>{@code networkHandler.getServerInfo()} - same data via the
     *       network handler. Older fallback, kept for safety.</li>
     *   <li>The actual TCP peer address from the live connection.
     *       Last-resort fallback so we still return *something* even if
     *       the server-info layer is broken; sometimes that's an IP
     *       literal which won't match any segment, and that's fine.</li>
     * </ol>
     */
    public static String getCurrentServerHost() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return "";

        // Singleplayer never has a remote host. Cheaper than
        // resolving network handler on each tick from the client thread.
        if (mc.isInSingleplayer()) return "";

        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry != null && entry.address != null) {
            return stripPort(entry.address);
        }

        var handler = mc.getNetworkHandler();
        if (handler == null) return "";

        ServerInfo handlerInfo = handler.getServerInfo();
        if (handlerInfo != null && handlerInfo.address != null) {
            return stripPort(handlerInfo.address);
        }

        // Live connection peer fallback - only useful when the
        // server-info object disappeared mid-session (transfer/relogin).
        SocketAddress peer = handler.getConnection().getAddress();
        if (peer instanceof InetSocketAddress isa && isa.getHostString() != null) {
            return isa.getHostString().toLowerCase().trim();
        }
        return "";
    }

    private static String stripPort(String addr) {
        String s = addr.toLowerCase().trim();
        int sep = s.indexOf(':');
        return sep >= 0 ? s.substring(0, sep) : s;
    }

    /**
     * Check if server address contains specific segment
     */
    private static boolean hasServerSegment(String expectedSegment) {
        String host = getCurrentServerHost();
        if (host.isEmpty()) {
            return false;
        }

        String[] parts = host.split("\\.");
        for (String part : parts) {
            if (part.equals(expectedSegment)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if current server is FunTime
     */
    public static boolean isFunTimeServer() {
        return hasServerSegment("funtime") || hasServerSegment("funsky");
    }

    /**
     * Check if current server is FunTrainer
     */
    public static boolean isFunTrainerServer() {
        return hasServerSegment("funtrainer");
    }

    /**
     * Check if current server is FillCube
     */
    public static boolean isFillCubeServer() {
        return hasServerSegment("fillcube");
    }

    /**
     * Check if current server is HolyWorld
     */
    public static boolean isHolyWorldServer() {
        return hasServerSegment("holyworld");
    }

    /**
     * Check if ShiftTap is supported on current server
     */
    public static boolean isShiftTapSupported() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }

        if (mc.isInSingleplayer()) {
            return true;
        }

        return isFunTimeServer() 
                || hasServerSegment("funmoon")
                || hasServerSegment("prostotrainer");
    }

    /**
     * Check if AutoSwap is supported on current server
     * Supported servers: FunTime, FunSky, HolyTime, SpookyTime, ProstoTrainer, FunTrainer, Singleplayer
     */
    public static boolean isAutoSwapSupported() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }

        // Always allow in singleplayer
        if (mc.isInSingleplayer()) {
            return true;
        }

        // Check supported servers
        return hasServerSegment("funtime")
                || hasServerSegment("funsky")
                || hasServerSegment("holytime")
                || hasServerSegment("spookytime")
                || hasServerSegment("prostotrainer")
                || hasServerSegment("funtrainer");
    }

    /**
     * Check if AutoPotion is supported on current server
     * Supported servers: FunTime, FunSky, HolyTime, Space-Times, SpookyTime, FillCube, Singleplayer
     */
    public static boolean isAutoPotionSupported() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }

        if (mc.isInSingleplayer()) {
            return true;
        }

        return hasServerSegment("funtime")
                || hasServerSegment("funsky")
                || hasServerSegment("holytime")
                || hasServerSegment("space-times")
                || hasServerSegment("spookytime")
                || hasServerSegment("fillcube");
    }

    /**
     * Check if ElytraUtility is supported on current server
     * Supported servers: FunTime, FunSky, HolyTime, SpookyTime, FunMoon, Singleplayer
     */
    public static boolean isElytraUtilitySupported() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }

        if (mc.isInSingleplayer()) {
            return true;
        }

        return hasServerSegment("funtime")
                || hasServerSegment("funsky")
                || hasServerSegment("holytime")
                || hasServerSegment("spookytime")
                || hasServerSegment("funmoon");
    }

    /**
     * Check if ItemScroller is supported on current server
     * Supported servers: FunTime, HolyWorld, HolyTime, FunSky, Space-Times, SpookyTime, FunMoon, Singleplayer
     */
    public static boolean isItemScrollerSupported() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }

        if (mc.isInSingleplayer()) {
            return true;
        }

        return hasServerSegment("funtime")
                || hasServerSegment("holyworld")
                || hasServerSegment("holytime")
                || hasServerSegment("funsky")
                || hasServerSegment("space-times")
                || hasServerSegment("spookytime")
                || hasServerSegment("stray")
                || hasServerSegment("funmoon");
    }

    /**
     * Check if MouseClicker (Tape Mouse) is supported on current server
     * Supported servers: FunTime, FunSky, HolyTime, Space-Times, SpookyTime, FunMoon, FillCube, Singleplayer
     */
    public static boolean isMouseClickerSupported() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }

        if (mc.isInSingleplayer()) {
            return true;
        }

        return hasServerSegment("funtime")
                || hasServerSegment("funsky")
                || hasServerSegment("holytime")
                || hasServerSegment("space-times")
                || hasServerSegment("spookytime")
                || hasServerSegment("funmoon")
                || hasServerSegment("fillcube");
    }

    /**
     * Get current server address
     */
    public static String getServerAddress() {
        return getCurrentServerHost();
    }
}
