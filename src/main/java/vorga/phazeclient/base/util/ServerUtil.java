package vorga.phazeclient.base.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for server detection
 */
public class ServerUtil {
    private static final Set<String> FUN_TIME_SEGMENTS = Set.of("funtime", "funsky");
    private static volatile String cachedAddressInput;
    private static volatile String cachedNormalizedHost = "";

    /**
     * Mirrors the current remote-rules allow matrix. These local
     * allowlists act as a fail-safe when the rules backend is down,
     * so they must stay aligned with the D1 rules the admin panel
     * serves to the client.
     */
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

    /**
     * Check if server address contains specific segment
     */
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

    /**
     * Check if current server is FunTime
     */
    public static boolean isFunTimeServer() {
        return hasAnyServerSegment(FUN_TIME_SEGMENTS);
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
        return isSupported("shifttap", SHIFT_TAP_SEGMENTS);
    }

    /**
     * Check if AutoSwap is supported on current server
     * Mirrors remote rules: FunTime, FunSky, HolyTime, SpookyTime,
     * FunTrainer, SkyTime, Singleplayer.
     */
    public static boolean isAutoSwapSupported() {
        return isSupported("autoswap", AUTO_SWAP_SEGMENTS);
    }

    /**
     * Check if AutoPotion is supported on current server
     * Mirrors remote rules: FunTime, FunSky, HolyTime, Space-Times,
     * SpookyTime, FillCube, SkyTime, Singleplayer.
     */
    public static boolean isAutoPotionSupported() {
        return isSupported("autopotion", AUTO_POTION_SEGMENTS);
    }

    /**
     * Check if ElytraUtility is supported on current server
     * Mirrors remote rules: FunTime, FunSky, HolyTime, SpookyTime,
     * FunMoon, SkyTime, FunTrainer, Singleplayer.
     */
    public static boolean isElytraUtilitySupported() {
        return isSupported("elytrautility", ELYTRA_UTILITY_SEGMENTS);
    }

    /**
     * Check if ItemScroller is supported on current server
     * Mirrors remote rules: FunTime, HolyWorld, SkyTime, HolyTime,
     * FunSky, Space-Times, SpookyTime, FunMoon, Stray, Singleplayer.
     */
    public static boolean isItemScrollerSupported() {
        return isSupported("item_scroller", ITEM_SCROLLER_SEGMENTS);
    }

    /**
     * Check if MouseClicker (Tape Mouse) is supported on current server
     * Mirrors remote rules: FunTime, FunSky, HolyTime, Space-Times,
     * SpookyTime, FunMoon, FillCube, SkyTime, FunTrainer,
     * Singleplayer.
     */
    public static boolean isMouseClickerSupported() {
        return isSupported("mouseclicker", MOUSE_CLICKER_SEGMENTS);
    }

    public static boolean isAutoReissueSupported() {
        return isSupported("autoreissue", AUTO_REISSUE_SEGMENTS);
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

        return isSupported(moduleId, allowedSegments);
    }

    /**
     * Local whitelist check with a remote override.
     *
     * <p>The segment sets above are a snapshot of the rules as they
     * stood when the build was cut. When the admin panel explicitly
     * allows a module on the current host, that answer is newer and
     * more specific, so it wins - which is what makes "enable this
     * module on a new server" a change in the dashboard rather than a
     * client release.
     *
     * <p>It only ever loosens. A remote <em>block</em> is applied
     * separately in {@link
     * vorga.phazeclient.api.feature.module.Module#isServerLocked()};
     * this path cannot be used to lock something the local list allows.
     * And when the API is unreachable the override goes away, so a
     * stale allow cannot outlive the outage.
     */
    private static boolean isSupported(String moduleId, Set<String> allowedSegments) {
        if (RemoteRulesService.getInstance().isModuleExplicitlyAllowed(moduleId)) {
            return true;
        }
        return isSingleplayerOrHasAnyServerSegment(allowedSegments);
    }

    /**
     * Get current server address
     */
    public static String getServerAddress() {
        return getCurrentServerHost();
    }
}
