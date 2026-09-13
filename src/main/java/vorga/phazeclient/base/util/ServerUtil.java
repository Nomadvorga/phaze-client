package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ServerUtil {
    private static final Set<String> FUN_TIME_SEGMENTS = Set.of("funtime", "funsky");
    private static volatile String cachedAddressInput;
    private static volatile String cachedNormalizedHost = "";

    private static final Set<String> SHIFT_TAP_SEGMENTS = Set.of(
            "funtime",
            "funmoon",
            "skytime"
    );
    private static final Set<String> AUTO_SWAP_SEGMENTS = Set.of(
            "funtime",
            "funsky",
            "holytime",
            "spookytime",
            "funtrainer",
            "skytime"
    );
    private static final Set<String> AUTO_POTION_SEGMENTS = Set.of(
            "funtime",
            "funsky",
            "holytime",
            "space-times",
            "spookytime",
            "fillcube",
            "skytime"
    );
    private static final Set<String> ELYTRA_UTILITY_SEGMENTS = Set.of(
            "funtime",
            "funsky",
            "holytime",
            "spookytime",
            "funmoon",
            "skytime",
            "funtrainer"
    );
    private static final Set<String> ITEM_SCROLLER_SEGMENTS = Set.of(
            "funtime",
            "holyworld",
            "skytime",
            "holytime",
            "funsky",
            "space-times",
            "spookytime",
            "funmoon",
            "stray"
    );
    private static final Set<String> MOUSE_CLICKER_SEGMENTS = Set.of(
            "funtime",
            "funsky",
            "holytime",
            "space-times",
            "spookytime",
            "funmoon",
            "fillcube",
            "skytime",
            "funtrainer"
    );
    private static final Set<String> AUTO_REISSUE_SEGMENTS = Set.of(
            "funtime",
            "skytime"
    );
    private static final Set<String> AUC_HELPER_SEGMENTS = Set.of(
            "funtime"
    );
    private static final Set<String> AUTO_EAT_SEGMENTS = Set.of(
            "fillcube",
            "funsky",
            "funtime",
            "holytime",
            "skytime",
            "space-times",
            "spookytime"
    );
    private static final Set<String> AUTO_RESPAWN_SEGMENTS = Set.of(
            "funsky",
            "funtime",
            "holytime",
            "skytime",
            "space-times",
            "spookytime"
    );
    private static final Set<String> FAST_SWAP_SEGMENTS = Set.of(
            "fillcube",
            "funtime",
            "space-times"
    );
    private static final Set<String> FT_HELPER_SEGMENTS = Set.of(
            "funmoon",
            "funsky",
            "funtime",
            "funtrainer",
            "holytime",
            "skytime",
            "space-times",
            "spookytime"
    );
    private static final Set<String> TRAP_TIMER_SEGMENTS = Set.of(
            "funsky",
            "funtime",
            "funtrainer",
            "holytime",
            "skytime",
            "spookytime"
    );
    private static final Map<String, Set<String>> MIRRORED_MODULE_SEGMENTS = Map.ofEntries(
            Map.entry("auc_helper", AUC_HELPER_SEGMENTS),
            Map.entry("auto_eat", AUTO_EAT_SEGMENTS),
            Map.entry("auto_respawn", AUTO_RESPAWN_SEGMENTS),
            Map.entry("autonear", Set.of("funtime")),
            Map.entry("autopotion", AUTO_POTION_SEGMENTS),
            Map.entry("autoreissue", AUTO_REISSUE_SEGMENTS),
            Map.entry("autoswap", AUTO_SWAP_SEGMENTS),
            Map.entry("elytrautility", ELYTRA_UTILITY_SEGMENTS),
            Map.entry("fast_swap", FAST_SWAP_SEGMENTS),
            Map.entry("ft_helper", FT_HELPER_SEGMENTS),
            Map.entry("item_scroller", ITEM_SCROLLER_SEGMENTS),
            Map.entry("mouseclicker", MOUSE_CLICKER_SEGMENTS),
            Map.entry("shifttap", SHIFT_TAP_SEGMENTS),
            Map.entry("trap_timer", TRAP_TIMER_SEGMENTS)
    );

    public static String getCurrentServerHost() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return "";

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

        SocketAddress peer = handler.getConnection().getAddress();
        if (peer instanceof InetSocketAddress isa && isa.getHostString() != null) {
            return stripPort(isa.getHostString());
        }
        return "";
    }

    private static String stripPort(String addr) {
        if (addr.equals(cachedAddressInput)) {
            return cachedNormalizedHost;
        }
        String s = addr.toLowerCase(Locale.ROOT).trim();
        int sep = s.indexOf(':');
        String normalized = sep >= 0 ? s.substring(0, sep) : s;
        cachedNormalizedHost = normalized;
        cachedAddressInput = addr;
        return normalized;
    }

    private static boolean hasServerSegment(String expectedSegment) {
        String host = getCurrentServerHost();
        if (host.isEmpty()) {
            return false;
        }

        return containsHostSegment(host, expectedSegment);
    }

    private static boolean hasAnyServerSegment(Set<String> expectedSegments) {
        String host = getCurrentServerHost();
        if (host.isEmpty()) {
            return false;
        }

        for (String expectedSegment : expectedSegments) {
            if (containsHostSegment(host, expectedSegment)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsHostSegment(String host, String expectedSegment) {
        int start = 0;
        while (start <= host.length()) {
            int end = host.indexOf('.', start);
            if (end < 0) {
                end = host.length();
            }
            int length = end - start;
            if (length == expectedSegment.length()
                    && host.regionMatches(start, expectedSegment, 0, length)) {
                return true;
            }
            if (end == host.length()) {
                return false;
            }
            start = end + 1;
        }
        return false;
    }

    private static boolean isSingleplayerOrHasAnyServerSegment(Set<String> expectedSegments) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }
        if (mc.isInSingleplayer()) {
            return true;
        }
        return hasAnyServerSegment(expectedSegments);
    }

    public static boolean isFunTimeServer() {
        return hasAnyServerSegment(FUN_TIME_SEGMENTS);
    }

    public static boolean isFunTrainerServer() {
        return hasServerSegment("funtrainer");
    }

    public static boolean isFillCubeServer() {
        return hasServerSegment("fillcube");
    }

    public static boolean isHolyWorldServer() {
        return hasServerSegment("holyworld");
    }

    public static boolean isShiftTapSupported() {
        return isSingleplayerOrHasAnyServerSegment(SHIFT_TAP_SEGMENTS);
    }

    public static boolean isAutoSwapSupported() {
        return isSingleplayerOrHasAnyServerSegment(AUTO_SWAP_SEGMENTS);
    }

    public static boolean isAutoPotionSupported() {
        return isSingleplayerOrHasAnyServerSegment(AUTO_POTION_SEGMENTS);
    }

    public static boolean isElytraUtilitySupported() {
        return isSingleplayerOrHasAnyServerSegment(ELYTRA_UTILITY_SEGMENTS);
    }

    public static boolean isItemScrollerSupported() {
        return isSingleplayerOrHasAnyServerSegment(ITEM_SCROLLER_SEGMENTS);
    }

    public static boolean isMouseClickerSupported() {
        return isSingleplayerOrHasAnyServerSegment(MOUSE_CLICKER_SEGMENTS);
    }

    public static boolean isAutoReissueSupported() {
        return isSingleplayerOrHasAnyServerSegment(AUTO_REISSUE_SEGMENTS);
    }

    public static boolean hasMirroredModuleRule(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return false;
        }
        return MIRRORED_MODULE_SEGMENTS.containsKey(moduleId.toLowerCase());
    }

    public static boolean isModuleAllowedByMirroredRules(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return true;
        }

        Set<String> allowedSegments = MIRRORED_MODULE_SEGMENTS.get(moduleId.toLowerCase());
        if (allowedSegments == null) {
            return true;
        }

        return isSingleplayerOrHasAnyServerSegment(allowedSegments);
    }

    public static String getServerAddress() {
        return getCurrentServerHost();
    }
}
