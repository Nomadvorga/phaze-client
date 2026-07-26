package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Best-effort cache of online Phaze usernames.
 *
 * <p>The public rules endpoint currently guarantees the aggregate online
 * count and may optionally include usernames in future payloads. For
 * owners / testers the service can also use the admin online-players
 * endpoint when a token is supplied via JVM property, env var, or
 * {@code <minecraft>/Phaze/files/admin_token}.</p>
 */
public final class PhazePlayerPresence {
    private static final String[] PUBLIC_PLAYER_ENDPOINTS = {
            "/api/online-players",
            "/api/players",
            "/api/online-users",
            "/api/users",
            "/api/online-clients",
            "/api/clients",
            "/api/presence",
            "/api/online"
    };

    private static final class Holder {
        static final PhazePlayerPresence INSTANCE = new PhazePlayerPresence();
    }

    public static PhazePlayerPresence getInstance() {
        return Holder.INSTANCE;
    }

    private volatile Set<String> knownUsernames = Collections.emptySet();
    private volatile String adminToken;

    private PhazePlayerPresence() {
        this.adminToken = loadAdminToken();
        refreshSelfOnly();
    }

    public boolean isKnownUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return knownUsernames.contains(username.toLowerCase(Locale.ROOT));
    }

    /**
     * Best-effort extraction of a known Phaze username from a richer
     * nametag string, e.g. ArmorStand-based labels with prefixes,
     * suffixes, health text, or decorative separators around the real
     * Minecraft username.
     */
    public String findKnownUserInText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Set<String> users = knownUsernames;
        if (users.isEmpty()) {
            return null;
        }

        int start = -1;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '\0';
            if (i < text.length() && isUsernameChar(c)) {
                if (start < 0) {
                    start = i;
                }
                continue;
            }
            if (start < 0) {
                continue;
            }

            String token = text.substring(start, i);
            String normalized = token.toLowerCase(Locale.ROOT);
            if (users.contains(normalized)) {
                return normalized;
            }
            start = -1;
        }

        String lowered = text.toLowerCase(Locale.ROOT);
        String best = null;
        for (String username : users) {
            if (username == null || username.isBlank()) {
                continue;
            }
            int from = 0;
            while (from < lowered.length()) {
                int idx = lowered.indexOf(username, from);
                if (idx < 0) {
                    break;
                }
                int end = idx + username.length();
                boolean leftBoundary = idx == 0 || !isUsernameChar(lowered.charAt(idx - 1));
                boolean rightBoundary = end >= lowered.length() || !isUsernameChar(lowered.charAt(end));
                boolean accept = (leftBoundary && rightBoundary)
                        || (username.length() >= 4 && (leftBoundary || rightBoundary));
                if (accept && (best == null || username.length() > best.length())) {
                    best = username;
                }
                from = idx + 1;
            }
        }

        return best;
    }

    public void refreshFromRulesPayload(JsonObject payload, String apiBase, int connectTimeoutMs, int readTimeoutMs) {
        Set<String> next = new LinkedHashSet<>();
        addCurrentUsername(next);
        collectUsernames(payload, next);
        if (next.size() <= 1) {
            try {
                fetchPublicPlayers(apiBase, connectTimeoutMs, readTimeoutMs, next);
            } catch (Throwable ignored) {
                // Public player-list probes are optional. The common
                // path today is still "self only + aggregate online
                // count" when the backend doesn't expose usernames.
            }
        }

        String token = adminToken;
        if (token == null || token.isBlank()) {
            token = loadAdminToken();
            adminToken = token;
        }
        if (token != null && !token.isBlank()) {
            try {
                fetchAdminPlayers(apiBase, token, connectTimeoutMs, readTimeoutMs, next);
            } catch (Throwable ignored) {
                // Best-effort only. Lack of token / endpoint access
                // must never break the main rules heartbeat.
            }
        }

        knownUsernames = Collections.unmodifiableSet(next);
    }

    public void refreshSelfOnly() {
        Set<String> next = new LinkedHashSet<>();
        addCurrentUsername(next);
        knownUsernames = Collections.unmodifiableSet(next);
    }

    private void collectUsernames(JsonObject payload, Set<String> sink) {
        if (payload == null) {
            return;
        }
        collectArray(payload.get("usernames"), sink);
        collectArray(payload.get("players"), sink);
        collectArray(payload.get("onlinePlayers"), sink);
    }

    private void collectArray(JsonElement element, Set<String> sink) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                addUsername(sink, entry.getAsString());
                continue;
            }
            if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                if (object.has("username")) {
                    addUsername(sink, object.get("username").getAsString());
                } else if (object.has("name")) {
                    addUsername(sink, object.get("name").getAsString());
                }
            }
        }
    }

    private void fetchAdminPlayers(String apiBase, String token, int connectTimeoutMs, int readTimeoutMs, Set<String> sink) throws IOException {
        URI uri = URI.create(apiBase + "/api/admin/online-players");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            applyCommonRequestHeaders(conn, connectTimeoutMs, readTimeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            int status = conn.getResponseCode();
            if (status != 200) {
                return;
            }
            String body = readBody(conn.getInputStream());
            collectUsernames(com.google.gson.JsonParser.parseString(body), sink);
        } finally {
            conn.disconnect();
        }
    }

    private void fetchPublicPlayers(String apiBase, int connectTimeoutMs, int readTimeoutMs, Set<String> sink) throws IOException {
        for (String endpoint : PUBLIC_PLAYER_ENDPOINTS) {
            URI uri = URI.create(apiBase + endpoint);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            try {
                applyCommonRequestHeaders(conn, connectTimeoutMs, readTimeoutMs);
                conn.setRequestMethod("GET");
                int status = conn.getResponseCode();
                if (status != 200) {
                    continue;
                }
                String body = readBody(conn.getInputStream());
                int before = sink.size();
                collectUsernames(com.google.gson.JsonParser.parseString(body), sink);
                if (sink.size() > before) {
                    return;
                }
            } finally {
                conn.disconnect();
            }
        }
    }

    private void collectUsernames(JsonElement payload, Set<String> sink) {
        if (payload == null || payload.isJsonNull()) {
            return;
        }
        if (payload.isJsonArray()) {
            collectArray(payload, sink);
            return;
        }
        if (!payload.isJsonObject()) {
            return;
        }

        JsonObject object = payload.getAsJsonObject();
        collectArray(object.get("usernames"), sink);
        collectArray(object.get("players"), sink);
        collectArray(object.get("onlinePlayers"), sink);
        collectArray(object.get("users"), sink);
        collectArray(object.get("onlineUsers"), sink);
        collectArray(object.get("clients"), sink);
    }

    private void applyCommonRequestHeaders(HttpURLConnection conn, int connectTimeoutMs, int readTimeoutMs) {
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0.0.0 Safari/537.36");
    }

    private String loadAdminToken() {
        String fromProperty = System.getProperty("phaze.rules.adminToken", "").trim();
        if (!fromProperty.isEmpty()) {
            return fromProperty;
        }

        String fromEnv = System.getenv("PHAZE_ADMIN_TOKEN");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Path runDir = client != null ? client.runDirectory.toPath() : Path.of(".");
            Path file = runDir.resolve("Phaze").resolve("files").resolve("admin_token");
            if (Files.exists(file)) {
                String token = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private void addCurrentUsername(Set<String> sink) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getSession() == null) {
                return;
            }
            addUsername(sink, client.getSession().getUsername());
        } catch (Throwable ignored) {
        }
    }

    private void addUsername(Set<String> sink, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        sink.add(username.toLowerCase(Locale.ROOT));
    }

    private static boolean isUsernameChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    private static String readBody(InputStream stream) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[2048];
            int read;
            while ((read = reader.read(buf)) != -1) {
                out.append(buf, 0, read);
            }
        }
        return out.toString();
    }
}
