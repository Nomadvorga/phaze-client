package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;

/**
 * Utility class for server detection
 */
public class ServerUtil {
    
    /**
     * Get current server host address
     */
    private static String getCurrentServerHost() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getNetworkHandler() == null) {
            return "";
        }

        var serverInfo = mc.getNetworkHandler().getServerInfo();
        if (serverInfo == null || serverInfo.address == null) {
            return "";
        }

        String serverAddress = serverInfo.address.toLowerCase().trim();
        int portSeparator = serverAddress.indexOf(':');
        if (portSeparator >= 0) {
            serverAddress = serverAddress.substring(0, portSeparator);
        }

        return serverAddress;
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
