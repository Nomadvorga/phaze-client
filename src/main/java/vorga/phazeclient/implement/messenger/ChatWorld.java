package vorga.phazeclient.implement.messenger;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import vorga.phazeclient.base.util.MessengerPrefs;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.base.util.ServerUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The messenger's link to the world: which server and dimension the player is
 * in, the last death point, group pings, the teammate list refresh and joining
 * a friend's server. Pings are drawn by the waypoint renderer; shared points
 * become waypoints once saved from their chat card.
 */
public final class ChatWorld {
    private static final long TEAMMATE_REFRESH_TICKS = 30L * 20L;
    private static final double PING_RANGE = 160.0D;
    public static final int PING_COLOR = 0xFFFFD166;
    public static final int ADMIN_PING_COLOR = 0xFFFF4F4F;

    /** {@code realm} is the anarchy the point is on (see {@link AnarchyDetector}), or null. */
    public record Point(String serverKey, String realm, String dimension, int x, int y, int z) {}

    private static volatile Point lastDeath;
    private static boolean wasDead;
    private static long ticks;
    private static String cachedHost;
    private static String cachedKey;
    private static String cachedRealmHost;
    private static String cachedRealm;
    private static String cachedRealmKey;

    private ChatWorld() {
    }

    /** SHA-256 of the normalized host: lets clients compare servers without the address in any message. */
    public static String serverKey() {
        String host = ServerUtil.getCurrentServerHost();
        if (host == null || host.isBlank()) return null;
        if (host.equals(cachedHost)) return cachedKey;
        String key = keyOf(host);
        if (key == null) return null;
        cachedKey = key;
        cachedHost = host;
        return key;
    }

    /** The server key of any host, for checking a name a chat card claims. */
    public static String keyOf(String host) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(("phaze-server-v1:" + host).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception unavailable) {
            return null;
        }
    }

    /** The current host when it can be named on a card (lowercase DNS name or IPv4), else null. */
    public static String nameableHost() {
        String host = ServerUtil.getCurrentServerHost();
        return host != null && host.length() <= 253 && MessengerService.HOST.matcher(host).matches() ? host : null;
    }

    /** The anarchy the player is on, when the network has several behind one address. */
    public static String realm() {
        return AnarchyDetector.current();
    }

    /**
     * Where local waypoints of this server live: the server key, or a key of
     * server and anarchy together, since anarchies sharing an address are different worlds.
     */
    public static String worldKey() {
        String server = serverKey();
        String realm = realm();
        if (server == null || realm == null) return server;
        String host = cachedHost;
        if (realm.equals(cachedRealm) && host.equals(cachedRealmHost)) return cachedRealmKey;
        String key = keyOf(host + "#" + realm);
        if (key == null) return server;
        cachedRealmKey = key;
        cachedRealm = realm;
        cachedRealmHost = host;
        return key;
    }

    /** "mc.funtime.su · Анархия-103" for the current server, or whichever part is known. */
    public static String serverLabel() {
        return label(nameableHost(), realm());
    }

    public static String label(String host, String realm) {
        if (host == null) return realm;
        return realm == null ? host : host + " · " + realm;
    }

    public static String dimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? null : client.world.getRegistryKey().getValue().toString();
    }

    /** The player's block position on a multiplayer server, or null in singleplayer and menus. */
    public static Point here() {
        MinecraftClient client = MinecraftClient.getInstance();
        String key = serverKey();
        String dimension = dimension();
        if (client.player == null || key == null || dimension == null) return null;
        BlockPos pos = client.player.getBlockPos();
        return new Point(key, realm(), dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Text colour for a group mate's name; nametags and the tab list share it. */
    public static final int TEAMMATE_COLOR = 0x7CE38B;

    /** A group mate on this server, by in-game nickname. Visual only: a nickname proves nothing. */
    public static boolean isTeammate(String nickname) {
        if (nickname == null || nickname.isEmpty() || !MessengerPrefs.get().highlightTeammates()) return false;
        var teammates = MessengerService.getInstance().getTeammates();
        return !teammates.isEmpty() && teammates.contains(nickname.toLowerCase(java.util.Locale.ROOT));
    }

    public static Point lastDeath() {
        Point death = lastDeath;
        return death != null && death.serverKey().equals(serverKey()) ? death : null;
    }

    public static boolean isHere(String serverKey, String dimension) {
        return serverKey != null && serverKey.equals(serverKey()) && dimension != null && dimension.equals(dimension());
    }

    public static JsonObject attachment(String type, Point point) {
        return attachment(type, point, -1, -1);
    }

    /** A point card naming the current server; {@code color} and {@code display} are left out when negative. */
    public static JsonObject attachment(String type, Point point, int color, int display) {
        return attachment(type, point, color, display, nameableHost());
    }

    /** {@code server} is named only when it hashes to the point's key; the backend refuses anything else. */
    public static JsonObject attachment(String type, Point point, int color, int display, String server) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("serverKey", point.serverKey());
        object.addProperty("dimension", point.dimension());
        object.addProperty("x", point.x());
        object.addProperty("y", point.y());
        object.addProperty("z", point.z());
        if (server != null && MessengerService.HOST.matcher(server).matches() && point.serverKey().equals(keyOf(server))) {
            object.addProperty("server", server);
        }
        if (AnarchyDetector.isValidLabel(point.realm())) object.addProperty("realm", point.realm());
        if (color >= 0) object.addProperty("color", color & 0xFFFFFF);
        if (display >= 0) object.addProperty("display", display & 15);
        return object;
    }

    public static void tick(MinecraftClient client, KeyBinding pingKey) {
        ticks++;
        AnarchyDetector.tick(client);
        if (client.player != null) {
            boolean dead = client.player.isDead() || client.player.getHealth() <= 0.0F;
            if (dead && !wasDead) lastDeath = here();
            wasDead = dead;
        }

        MessengerService service = MessengerService.getInstance();
        // The menu can be closed without its close path (setScreen(null)); nothing is on screen then.
        if (service.getSelectedConversation() != null
                && !(client.currentScreen instanceof vorga.phazeclient.implement.menu.MenuScreen)) {
            service.selectConversation(null);
        }
        boolean onServer = client.world != null && serverKey() != null;
        if (!onServer || !MessengerPrefs.get().highlightTeammates()) {
            if (!service.getTeammates().isEmpty()) service.clearTeammates();
        } else if (ticks % TEAMMATE_REFRESH_TICKS == 1L) {
            service.refreshTeammatesAsync();
        }

        while (pingKey.wasPressed()) {
            if (client.currentScreen == null) sendPing(client);
        }
    }

    private static void sendPing(MinecraftClient client) {
        Entity camera = client.getCameraEntity();
        String key = serverKey();
        String dimension = dimension();
        if (camera == null || key == null || dimension == null) return;
        HitResult hit = camera.raycast(PING_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = block.getBlockPos();
        MessengerService.getInstance().sendPing(key, dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Leaves the current world the way the pause menu does, then connects. */
    public static void join(String address, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (address == null || address.isBlank() || !ServerAddress.isValid(address)) return;
        client.execute(() -> {
            if (client.world != null) {
                boolean singleplayer = client.isInSingleplayer();
                client.world.disconnect();
                if (singleplayer) client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
                else client.disconnect();
            }
            ServerInfo info = new ServerInfo(name == null ? address : name, address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address), info, false, null);
        });
    }
}
