package vorga.phazeclient.base.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Messenger choices that never leave this computer: toast switches, teammate
 * highlighting and the private notes kept about friends. Stored as
 * {@code <minecraft>/Phaze/files/messenger.json}; written on change, atomically.
 */
public final class MessengerPrefs {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeMessenger");
    public static final int NOTE_MAX = 64;
    private static final int MAX_NOTES = 500;
    private static final MessengerPrefs INSTANCE = new MessengerPrefs();

    private final Map<String, String> notes = new ConcurrentHashMap<>();
    private static final int MAX_HIDDEN_POINTS = 2_000;
    /** Shared point cards (by message id) this player chose not to see in the world. */
    private final java.util.Set<Long> hiddenPoints = ConcurrentHashMap.newKeySet();
    private volatile int hiddenVersion;
    private volatile boolean toasts = true;
    private volatile boolean friendOnlineToasts = true;
    private volatile boolean doNotDisturb;
    private volatile boolean highlightTeammates = true;
    private volatile boolean loaded;

    private MessengerPrefs() {
    }

    /** Read on every nametag and HUD frame: after the first load this is one volatile read. */
    public static MessengerPrefs get() {
        if (!INSTANCE.loaded) INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public boolean toasts() {
        return toasts;
    }

    public boolean friendOnlineToasts() {
        return friendOnlineToasts;
    }

    public boolean doNotDisturb() {
        return doNotDisturb;
    }

    public boolean highlightTeammates() {
        return highlightTeammates;
    }

    public void setToasts(boolean value) {
        toasts = value;
        save();
    }

    public void setFriendOnlineToasts(boolean value) {
        friendOnlineToasts = value;
        save();
    }

    public void setDoNotDisturb(boolean value) {
        doNotDisturb = value;
        save();
    }

    public void setHighlightTeammates(boolean value) {
        highlightTeammates = value;
        save();
    }

    public boolean isPointHidden(long messageId) {
        return hiddenPoints.contains(messageId);
    }

    /** Bumped whenever a point is hidden or shown again, so renderers can cache. */
    public int hiddenVersion() {
        return hiddenVersion;
    }

    public void setPointHidden(long messageId, boolean hidden) {
        if (messageId <= 0L) return;
        boolean changed = hidden ? hiddenPoints.add(messageId) : hiddenPoints.remove(messageId);
        if (!changed) return;
        // Message ids only grow: the smallest belongs to the oldest card, likely long gone from the chat.
        while (hiddenPoints.size() > MAX_HIDDEN_POINTS) {
            hiddenPoints.stream().min(Long::compare).ifPresent(hiddenPoints::remove);
        }
        hiddenVersion++;
        save();
    }

    public String note(String userId) {
        return userId == null ? "" : notes.getOrDefault(userId, "");
    }

    /** Blank removes the note; control characters and overflow are cut, never stored. */
    public void setNote(String userId, String note) {
        if (userId == null || !userId.matches("[0-9a-f]{32}")) return;
        String clean = note == null ? "" : note.replaceAll("\\p{Cntrl}", " ").strip();
        if (clean.codePointCount(0, clean.length()) > NOTE_MAX) {
            clean = clean.substring(0, clean.offsetByCodePoints(0, NOTE_MAX));
        }
        if (clean.isEmpty()) notes.remove(userId);
        else if (notes.size() < MAX_NOTES || notes.containsKey(userId)) notes.put(userId, clean);
        save();
    }

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
            if (!Files.exists(file)) return;
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            toasts = flag(root, "toasts", true);
            friendOnlineToasts = flag(root, "friendOnlineToasts", true);
            doNotDisturb = flag(root, "doNotDisturb", false);
            highlightTeammates = flag(root, "highlightTeammates", true);
            if (root.has("notes") && root.get("notes").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("notes").entrySet()) {
                    if (entry.getKey().matches("[0-9a-f]{32}") && entry.getValue().isJsonPrimitive()
                            && notes.size() < MAX_NOTES) {
                        String value = entry.getValue().getAsString();
                        if (!value.isBlank() && value.length() <= NOTE_MAX * 2) notes.put(entry.getKey(), value);
                    }
                }
            }
            if (root.has("hiddenPoints") && root.get("hiddenPoints").isJsonArray()) {
                for (com.google.gson.JsonElement entry : root.getAsJsonArray("hiddenPoints")) {
                    if (hiddenPoints.size() >= MAX_HIDDEN_POINTS) break;
                    if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber()) {
                        long id = entry.getAsLong();
                        if (id > 0L) hiddenPoints.add(id);
                    }
                }
            }
        } catch (Exception corrupt) {
            LOG.debug("Messenger preferences could not be read, using defaults", corrupt);
        }
    }

    private static boolean flag(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
    }

    private synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("toasts", toasts);
        root.addProperty("friendOnlineToasts", friendOnlineToasts);
        root.addProperty("doNotDisturb", doNotDisturb);
        root.addProperty("highlightTeammates", highlightTeammates);
        JsonObject noteObject = new JsonObject();
        notes.forEach(noteObject::addProperty);
        root.add("notes", noteObject);
        com.google.gson.JsonArray hidden = new com.google.gson.JsonArray();
        hiddenPoints.forEach(hidden::add);
        root.add("hiddenPoints", hidden);
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("messenger.json.tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception failed) {
            LOG.debug("Messenger preferences could not be saved", failed);
        }
    }

    private static Path file() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path runDir = client != null ? client.runDirectory.toPath() : Path.of(".");
        return runDir.resolve("Phaze").resolve("files").resolve("messenger.json");
    }
}
