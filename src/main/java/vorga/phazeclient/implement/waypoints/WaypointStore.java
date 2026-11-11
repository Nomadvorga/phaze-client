package vorga.phazeclient.implement.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.implement.messenger.ChatWorld;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Waypoints kept on this computer only, grouped by world: the messenger's
 * hashed server key on servers (with the anarchy, where a network has several),
 * a hashed level name in singleplayer. The file
 * never contains a server address. Everything entering the store, from the
 * editor, a chat card or the file itself, goes through {@link #sanitize}.
 *
 * <p>Owned by the render thread; the file is written on a background thread
 * from an immutable JSON snapshot, coalescing bursts of edits into one write.</p>
 */
public final class WaypointStore {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeWaypoints");
    public static final int MAX_PER_WORLD = 100;
    public static final int NAME_MAX = 32;
    public static final double COORD_LIMIT = 30_000_000.0D;
    public static final double Y_LIMIT = 4_096.0D;
    private static final int MAX_WORLDS = 256;
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final Pattern WORLD_KEY = Pattern.compile("(?:sp-)?[0-9a-f]{64}");
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,48}");
    private static final WaypointStore INSTANCE = new WaypointStore();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Phaze-Waypoints-Writer");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
    private final AtomicReference<String> pendingJson = new AtomicReference<>();
    private final AtomicBoolean writeScheduled = new AtomicBoolean();
    private volatile int version;
    private long lastId;
    private boolean loaded;
    private String cachedLevel;
    private String cachedLevelKey;

    private WaypointStore() {
    }

    public static WaypointStore get() {
        if (!INSTANCE.loaded) INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** Bumped on every change; renderers rebuild their per-world caches when it moves. */
    public int version() {
        return version;
    }

    /** The key of the world the player is in (server and anarchy), or null in menus. */
    public static String currentWorldKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        String server = ChatWorld.worldKey();
        if (server != null) return server;
        return INSTANCE.singleplayerKey(client);
    }

    public static boolean isValidWorldKey(String key) {
        return key != null && WORLD_KEY.matcher(key).matches();
    }

    public static boolean isValidDimension(String dimension) {
        return dimension != null && DIMENSION.matcher(dimension).matches();
    }

    public synchronized List<Waypoint> list(String worldKey) {
        List<Waypoint> list = worldKey == null ? null : worlds.get(worldKey);
        return list == null ? List.of() : List.copyOf(list);
    }

    public synchronized Waypoint find(String worldKey, long id) {
        List<Waypoint> list = worldKey == null ? null : worlds.get(worldKey);
        if (list == null || id == 0L) return null;
        for (Waypoint waypoint : list) if (waypoint.id() == id) return waypoint;
        return null;
    }

    public synchronized Waypoint findByMessage(String worldKey, long messageId) {
        List<Waypoint> list = worldKey == null ? null : worlds.get(worldKey);
        if (list == null || messageId == 0L) return null;
        for (Waypoint waypoint : list) if (waypoint.messageId() == messageId) return waypoint;
        return null;
    }

    public synchronized boolean isFull(String worldKey) {
        List<Waypoint> list = worldKey == null ? null : worlds.get(worldKey);
        return list != null && list.size() >= MAX_PER_WORLD;
    }

    /** Adds a draft (id 0) or replaces the waypoint with the same id; null when rejected. */
    public synchronized Waypoint put(String worldKey, Waypoint candidate) {
        if (!isValidWorldKey(worldKey)) return null;
        Waypoint clean = sanitize(candidate);
        if (clean == null) return null;
        List<Waypoint> list = worlds.get(worldKey);
        if (list == null) {
            if (worlds.size() >= MAX_WORLDS) return null;
            list = new ArrayList<>();
            worlds.put(worldKey, list);
        }
        if (clean.id() != 0L) {
            for (int index = 0; index < list.size(); index++) {
                if (list.get(index).id() == clean.id()) {
                    list.set(index, clean);
                    changed();
                    return clean;
                }
            }
        }
        if (list.size() >= MAX_PER_WORLD) return null;
        Waypoint added = clean.withId(nextId());
        list.add(added);
        changed();
        return added;
    }

    public synchronized void remove(String worldKey, long id) {
        List<Waypoint> list = worldKey == null ? null : worlds.get(worldKey);
        if (list == null || !list.removeIf(waypoint -> waypoint.id() == id)) return;
        if (list.isEmpty()) worlds.remove(worldKey);
        changed();
    }

    /** Clamps every field to what the renderer and the file format accept; null when unusable. */
    public static Waypoint sanitize(Waypoint waypoint) {
        if (waypoint == null || !isValidDimension(waypoint.dimension())) return null;
        if (!Double.isFinite(waypoint.x()) || !Double.isFinite(waypoint.y()) || !Double.isFinite(waypoint.z())) return null;
        return new Waypoint(Math.max(0L, waypoint.id()), cleanName(waypoint.name()), waypoint.dimension(),
                coordinate(waypoint.x(), COORD_LIMIT), coordinate(waypoint.y(), Y_LIMIT), coordinate(waypoint.z(), COORD_LIMIT),
                0xFF000000 | (waypoint.color() & 0x00FFFFFF),
                waypoint.showText(), waypoint.highlight(), waypoint.showBeam(), waypoint.showDistance(),
                Math.max(0L, waypoint.createdAt()), Math.max(0L, waypoint.messageId()));
    }

    /** No control characters or formatting codes, single spaces, at most {@link #NAME_MAX} code points. */
    public static String cleanName(String name) {
        if (name == null) return "";
        StringBuilder out = new StringBuilder();
        int count = 0;
        boolean space = false;
        for (int index = 0; index < name.length() && count < NAME_MAX; ) {
            int codePoint = name.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '\u00A7' || Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) continue;
            if (Character.isWhitespace(codePoint)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                out.append(' ');
                count++;
                space = false;
                if (count >= NAME_MAX) break;
            }
            out.appendCodePoint(codePoint);
            count++;
        }
        return out.toString().strip();
    }

    private static double coordinate(double value, double limit) {
        double clamped = Math.max(-limit, Math.min(limit, value));
        return Math.round(clamped * 100.0D) / 100.0D;
    }

    private long nextId() {
        long now = System.currentTimeMillis();
        lastId = Math.max(lastId + 1L, now);
        return lastId;
    }

    private void changed() {
        version++;
        save();
    }

    private String singleplayerKey(MinecraftClient client) {
        try {
            var server = client.getServer();
            if (server == null) return null;
            String level = server.getSaveProperties().getLevelName();
            if (level == null) return null;
            if (level.equals(cachedLevel)) return cachedLevelKey;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            cachedLevelKey = "sp-" + HexFormat.of().formatHex(digest.digest(("phaze-world-v1:" + level).getBytes(StandardCharsets.UTF_8)));
            cachedLevel = level;
            return cachedLevelKey;
        } catch (Exception unavailable) {
            return null;
        }
    }

    /* ---- file ---------------------------------------------------------- */

    private synchronized void ensureLoaded() {
        if (loaded) return;
        try {
            load();
        } finally {
            loaded = true;
        }
    }

    private void load() {
        try {
            Path file = file();
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return;
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("worlds")
                    || !parsed.getAsJsonObject().get("worlds").isJsonObject()) return;
            for (Map.Entry<String, JsonElement> world : parsed.getAsJsonObject().getAsJsonObject("worlds").entrySet()) {
                if (worlds.size() >= MAX_WORLDS) break;
                if (!isValidWorldKey(world.getKey()) || !world.getValue().isJsonArray()) continue;
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement element : world.getValue().getAsJsonArray()) {
                    if (list.size() >= MAX_PER_WORLD) break;
                    Waypoint waypoint = element.isJsonObject() ? read(element.getAsJsonObject()) : null;
                    if (waypoint == null || waypoint.id() == 0L) continue;
                    boolean duplicate = false;
                    for (Waypoint existing : list) duplicate |= existing.id() == waypoint.id();
                    if (duplicate) continue;
                    list.add(waypoint);
                    lastId = Math.max(lastId, waypoint.id());
                }
                if (!list.isEmpty()) worlds.put(world.getKey(), list);
            }
            version++;
        } catch (Exception corrupt) {
            LOG.warn("Waypoints file could not be read, starting empty", corrupt);
        }
    }

    private static Waypoint read(JsonObject object) {
        try {
            return sanitize(new Waypoint(object.get("id").getAsLong(), object.get("name").getAsString(),
                    object.get("dimension").getAsString(), object.get("x").getAsDouble(), object.get("y").getAsDouble(),
                    object.get("z").getAsDouble(), object.get("color").getAsInt(),
                    flag(object, "text"), flag(object, "highlight"), flag(object, "beam"), flag(object, "distance"),
                    object.has("created") ? object.get("created").getAsLong() : 0L,
                    object.has("message") ? object.get("message").getAsLong() : 0L));
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static boolean flag(JsonObject object, String key) {
        return !object.has(key) || !object.get(key).isJsonPrimitive() || object.get(key).getAsBoolean();
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonObject worldsObject = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> world : worlds.entrySet()) {
            JsonArray array = new JsonArray();
            for (Waypoint waypoint : world.getValue()) array.add(write(waypoint));
            worldsObject.add(world.getKey(), array);
        }
        root.add("worlds", worldsObject);
        pendingJson.set(root.toString());
        if (writeScheduled.compareAndSet(false, true)) WRITER.execute(this::flush);
    }

    private static JsonObject write(Waypoint waypoint) {
        JsonObject object = new JsonObject();
        object.addProperty("id", waypoint.id());
        object.addProperty("name", waypoint.name());
        object.addProperty("dimension", waypoint.dimension());
        object.addProperty("x", waypoint.x());
        object.addProperty("y", waypoint.y());
        object.addProperty("z", waypoint.z());
        object.addProperty("color", waypoint.color());
        object.addProperty("text", waypoint.showText());
        object.addProperty("highlight", waypoint.highlight());
        object.addProperty("beam", waypoint.showBeam());
        object.addProperty("distance", waypoint.showDistance());
        object.addProperty("created", waypoint.createdAt());
        if (waypoint.messageId() != 0L) object.addProperty("message", waypoint.messageId());
        return object;
    }

    private void flush() {
        writeScheduled.set(false);
        String json = pendingJson.getAndSet(null);
        if (json == null) return;
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("waypoints.json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception failed) {
            LOG.warn("Waypoints could not be saved", failed);
        }
    }

    private static Path file() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path runDir = client != null ? client.runDirectory.toPath() : Path.of(".");
        return runDir.resolve("Phaze").resolve("files").resolve("waypoints.json");
    }

    /** Read-only view for callers that must not keep a reference to the live list. */
    public synchronized List<Waypoint> snapshot(String worldKey, String dimension) {
        List<Waypoint> list = worldKey == null ? null : worlds.get(worldKey);
        if (list == null || dimension == null) return Collections.emptyList();
        List<Waypoint> matching = new ArrayList<>(list.size());
        for (Waypoint waypoint : list) if (dimension.equals(waypoint.dimension())) matching.add(waypoint);
        return matching;
    }
}
