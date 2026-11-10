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
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;
import vorga.phazeclient.base.util.MessengerPrefs;
import vorga.phazeclient.base.util.MessengerService;
import vorga.phazeclient.base.util.ServerUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The messenger's link to the world: which server and dimension the player is
 * in, the last death point, the shared points a player chose to show, group
 * pings, the teammate list refresh and joining a friend's server.
 *
 * <p>Markers are deliberately plain: a block outline and a distance label,
 * drawn through the shared world overlay batch, nothing per-frame allocated
 * beyond the label text.</p>
 */
public final class ChatWorld {
    private static final long TEAMMATE_REFRESH_TICKS = 30L * 20L;
    private static final int MAX_MARKERS = 8;
    private static final double PING_RANGE = 160.0D;
    private static final int MARKER_COLOR = 0xFF7FD3FF;
    private static final int DEATH_COLOR = 0xFFFF6B6B;
    private static final int PING_COLOR = 0xFFFFD166;

    public record Point(String serverKey, String dimension, int x, int y, int z) {}

    private record Marker(String label, Point point, int color) {}

    private static final Map<Long, Marker> MARKERS = new LinkedHashMap<>();
    private static volatile Point lastDeath;
    private static boolean wasDead;
    private static long ticks;
    private static String cachedHost;
    private static String cachedKey;

    private ChatWorld() {
    }

    /** SHA-256 of the normalized host: lets clients compare servers without the address in any message. */
    public static String serverKey() {
        String host = ServerUtil.getCurrentServerHost();
        if (host == null || host.isBlank()) return null;
        if (host.equals(cachedHost)) return cachedKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            cachedKey = HexFormat.of().formatHex(digest.digest(("phaze-server-v1:" + host).getBytes(StandardCharsets.UTF_8)));
            cachedHost = host;
            return cachedKey;
        } catch (Exception unavailable) {
            return null;
        }
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
        return new Point(key, dimension, pos.getX(), pos.getY(), pos.getZ());
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
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("serverKey", point.serverKey());
        object.addProperty("dimension", point.dimension());
        object.addProperty("x", point.x());
        object.addProperty("y", point.y());
        object.addProperty("z", point.z());
        return object;
    }

    public static boolean isMarked(long messageId) {
        synchronized (MARKERS) {
            return MARKERS.containsKey(messageId);
        }
    }

    /** Shows or hides a shared point; the oldest one goes when the limit is reached. */
    public static void toggleMarker(long messageId, String label, MessengerService.Attachment attachment) {
        if (attachment == null || !attachment.isPoint()) return;
        synchronized (MARKERS) {
            if (MARKERS.remove(messageId) != null) return;
            if (MARKERS.size() >= MAX_MARKERS) MARKERS.remove(MARKERS.keySet().iterator().next());
            MARKERS.put(messageId, new Marker(label, new Point(attachment.serverKey(), attachment.dimension(),
                    attachment.x(), attachment.y(), attachment.z()), "death".equals(attachment.type()) ? DEATH_COLOR : MARKER_COLOR));
        }
    }

    public static void tick(MinecraftClient client, KeyBinding pingKey) {
        ticks++;
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

    public static void render(MatrixStack matrices, Vec3d camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        String key = serverKey();
        String dimension = dimension();
        if (key == null || dimension == null) return;

        List<Marker> visible = new ArrayList<>();
        synchronized (MARKERS) {
            for (Marker marker : MARKERS.values()) {
                if (marker.point().serverKey().equals(key) && marker.point().dimension().equals(dimension)) visible.add(marker);
            }
        }
        for (MessengerService.Ping ping : MessengerService.getInstance().getActivePings()) {
            if (ping.serverKey().equals(key) && ping.dimension().equals(dimension)) {
                visible.add(new Marker(ping.username(), new Point(key, dimension, ping.x(), ping.y(), ping.z()), PING_COLOR));
            }
        }
        for (Marker marker : visible) drawMarker(client, matrices, camera, marker);
    }

    private static void drawMarker(MinecraftClient client, MatrixStack matrices, Vec3d camera, Marker marker) {
        Point point = marker.point();
        double rx = point.x() - camera.x;
        double ry = point.y() - camera.y;
        double rz = point.z() - camera.z;
        double distance = Math.sqrt((point.x() + 0.5D - client.player.getX()) * (point.x() + 0.5D - client.player.getX())
                + (point.y() + 0.5D - client.player.getY()) * (point.y() + 0.5D - client.player.getY())
                + (point.z() + 0.5D - client.player.getZ()) * (point.z() + 0.5D - client.player.getZ()));
        int color = marker.color();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        var lines = WorldOverlayRenderer.buffer(VertexFormat.DrawMode.LINES, VertexFormats.LINES, 2, false);
        VertexRendering.drawBox(matrices, lines, new Box(rx, ry, rz, rx + 1.0D, ry + 1.0D, rz + 1.0D), r, g, b, 0.9F);

        VertexConsumerProvider consumers = WorldOverlayRenderer.CONSUMERS;
        TextRenderer textRenderer = client.textRenderer;
        Text text = Text.literal(marker.label() + " · " + (int) distance + "m");
        float width = textRenderer.getWidth(text);
        matrices.push();
        matrices.translate(rx + 0.5D, ry + 1.6D, rz + 0.5D);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        float scale = 0.025F * Math.max(1.0F, (float) (distance / 10.0D));
        matrices.scale(scale, -scale, scale);
        textRenderer.draw(text, -width / 2.0F, 0.0F, color, false, matrices.peek().getPositionMatrix(),
                consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x80000000, 0xF000F0);
        matrices.pop();
    }
}
