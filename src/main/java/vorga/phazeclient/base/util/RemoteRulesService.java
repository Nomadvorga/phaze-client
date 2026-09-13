package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteRulesService {

    private static final Logger LOG = LoggerFactory.getLogger("PhazeRules");
    private static final Set<String> OFFLINE_FALLBACK_HIDDEN_MODULES = Set.of(
            "auc_helper",
            "auto_respawn",
            "auto_eat",
            "autonear",
            "autopotion",
            "autoreissue",
            "autoswap",
            "binds",
            "elytrautility",
            "fast_swap",
            "shifttap"
    );

    private static final String DEFAULT_API_BASE = "https://phazeclient.ru";

    private static final long HEARTBEAT_SECONDS = 600L;

    private static final long REFRESH_INTERVAL_SECONDS = 600L;
    private static final long LOCKDOWN_AFTER_MS = 20L * 60L * 1000L;
    private static final long STARTUP_GRACE_MS = 45_000L;

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    static {

        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    private static final class Holder {
        static final RemoteRulesService INSTANCE = new RemoteRulesService();
    }

    public static RemoteRulesService getInstance() {
        return Holder.INSTANCE;
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "phaze-rules-poll");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private final AtomicBoolean manifestUploaded = new AtomicBoolean(false);

    private final String apiBase;
    private final boolean enabled;
    private final String clientId;

    private volatile Set<String> blocked = Collections.emptySet();

    private volatile Set<String> allowed = Collections.emptySet();
    private volatile String lastHost = null;
    private volatile String rulesHost = null;
    private volatile long lastRefreshMs = 0L;
    private volatile long lastSuccessfulFetchMs = 0L;
    private final long serviceStartMs = System.currentTimeMillis();

    private volatile int onlineCount = -1;

    private RemoteRulesService() {
        String configured = System.getProperty("phaze.rules.api", DEFAULT_API_BASE);

        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        this.apiBase = configured;
        this.enabled = !configured.isEmpty();
        this.clientId = loadOrCreateClientId();
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public boolean isKnownClientUser(String username) {
        return PhazePlayerPresence.getInstance().isKnownUser(username);
    }

    public String getClientId() {
        return clientId;
    }

    public String getApiBase() {
        return apiBase;
    }

    public boolean isModuleExplicitlyAllowed(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return false;
        }

        if (isRulesApiUnavailableNow()) {
            return false;
        }
        String currentHost = ServerUtil.getCurrentServerHost();
        if (rulesHost == null || !rulesHost.equals(currentHost)) {
            return false;
        }
        return allowed.contains(moduleId.toLowerCase());
    }

    private String getCurrentUsername() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getSession() == null) return null;
            String username = client.getSession().getUsername();
            return username == null || username.isBlank() ? null : username;
        } catch (Throwable t) {
            return null;
        }
    }

    private String loadOrCreateClientId() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Path runDir = client != null
                    ? client.runDirectory.toPath()
                    : Path.of(".");
            Path file = runDir.resolve("Phaze").resolve("files").resolve("client_id");

            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();

                try {
                    UUID parsed = UUID.fromString(existing);
                    return parsed.toString();
                } catch (IllegalArgumentException badShape) {

                }
            }

            String fresh = UUID.randomUUID().toString();
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, fresh, StandardCharsets.UTF_8);
            } catch (IOException writeFailed) {

                LOG.warn("could not persist client id: {}", writeFailed.toString());
            }
            return fresh;
        } catch (Throwable t) {

            LOG.warn("client id init failed, using ephemeral id: {}", t.toString());
            return UUID.randomUUID().toString();
        }
    }

    public void start() {
        if (!enabled) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleWithFixedDelay(
                this::heartbeat,
                  0L,
                  HEARTBEAT_SECONDS,
                TimeUnit.SECONDS
        );

        PhazeEventService.getInstance().start();
    }

    public boolean isModuleBlocked(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return false;
        }
        String normalized = moduleId.toLowerCase();
        String currentHost = ServerUtil.getCurrentServerHost();

        if (rulesHost == null || !rulesHost.equals(currentHost)) {
            if (ServerUtil.hasMirroredModuleRule(normalized)) {
                return !ServerUtil.isModuleAllowedByMirroredRules(normalized);
            }
            return false;
        }
        if (shouldUseOfflineModuleFallback()) {
            if (ServerUtil.hasMirroredModuleRule(normalized)) {
                return !ServerUtil.isModuleAllowedByMirroredRules(normalized);
            }
            return OFFLINE_FALLBACK_HIDDEN_MODULES.contains(normalized);
        }
        if (!enabled) {
            return false;
        }
        return blocked.contains(moduleId.toLowerCase());
    }

    public boolean shouldUseOfflineModuleFallback() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.isInSingleplayer()) {
            return false;
        }
        return isRulesApiUnavailableNow();
    }

    public boolean shouldHideModuleWhenOffline(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return false;
        }
        if (!shouldUseOfflineModuleFallback()) {
            return false;
        }
        String normalized = moduleId.toLowerCase();
        if (ServerUtil.hasMirroredModuleRule(normalized)) {
            return false;
        }
        return OFFLINE_FALLBACK_HIDDEN_MODULES.contains(normalized);
    }

    private boolean isStrictLockdownNow() {
        String host = ServerUtil.getCurrentServerHost();
        if (host == null || host.isEmpty()) return false;

        return isRulesApiUnavailableNow();
    }

    private boolean isRulesApiUnavailableNow() {
        if (!enabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - serviceStartMs < STARTUP_GRACE_MS) return false;
        if (lastSuccessfulFetchMs <= 0L) return true;
        return (now - lastSuccessfulFetchMs) > LOCKDOWN_AFTER_MS;
    }

    public Set<String> getBlockedModuleIds() {
        return blocked;
    }

    public void requestRefresh() {
        if (!enabled || !started.get()) {
            return;
        }
        String host = ServerUtil.getCurrentServerHost();
        if (host == null) {
            host = "";
        }

        lastHost = host;
        lastRefreshMs = 0L;
        String requestedHost = host;
        scheduler.execute(() -> fetchAsync(requestedHost, true));
    }

    private static Set<String> readModuleIds(JsonObject obj, String field) {
        Set<String> out = new HashSet<>();
        if (obj.has(field) && obj.get(field).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(field);
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    out.add(el.getAsString().toLowerCase());
                }
            }
        }
        return out;
    }

    private void heartbeat() {
        try {
            String host = ServerUtil.getCurrentServerHost();

            if (host == null) host = "";

            boolean hostChanged = !host.equals(lastHost);
            long now = System.currentTimeMillis();
            boolean stale = (now - lastRefreshMs) >= REFRESH_INTERVAL_SECONDS * 1000L;

            if (hostChanged) {

                lastHost = host;

                fetchAsync(host, true);
            } else if (stale) {

                fetchAsync(host, false);
            }
        } catch (Throwable t) {
            LOG.warn("heartbeat failed", t);

        }
    }

    private void fetchAsync(String host, boolean bypassCache) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        scheduler.execute(() -> {
            try {
                fetch(host, bypassCache);
            } catch (Throwable t) {
                LOG.warn("fetch failed for host='{}': {}", host, t.toString());

                lastRefreshMs = 0L;
            } finally {
                refreshInFlight.set(false);
            }
        });
    }

    private void fetch(String host, boolean bypassCache) throws Exception {
        String encodedHost = URLEncoder.encode(host == null ? "" : host, StandardCharsets.UTF_8);
        String encodedClient = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        String encodedUsername = URLEncoder.encode(
                getCurrentUsername() == null ? "" : getCurrentUsername(),
                StandardCharsets.UTF_8);
        String encodedFresh = bypassCache ? "&fresh=1" : "";

        URI uri = URI.create(
                apiBase
                        + "/api/module-rules?host="
                        + encodedHost
                        + "&clientId="
                        + encodedClient
                        + "&username="
                        + encodedUsername
                        + encodedFresh);

        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String body;
        int status;
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (bypassCache) {
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                conn.setRequestProperty("Pragma", "no-cache");
            }

            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36");
            conn.setInstanceFollowRedirects(true);

            status = conn.getResponseCode();
            if (status != 200) {
                LOG.warn("fetch '{}' returned status {}", uri, status);
                return;
            }

            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[2048];
                int n;
                while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            }
            body = sb.toString();
        } finally {
            conn.disconnect();
        }

        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) return;
        JsonObject obj = parsed.getAsJsonObject();

        Set<String> next = readModuleIds(obj, "blocked");
        Set<String> nextAllowed = readModuleIds(obj, "allowed");

        if (obj.has("online") && obj.get("online").isJsonPrimitive()) {
            try {
                onlineCount = obj.get("online").getAsInt();
            } catch (Exception ignored) {

            }
        }

        PhazeAnnouncements.acceptAll(obj.get("announcements"));

        PhazePlayerPresence.getInstance().refreshFromRulesPayload(
                obj,
                apiBase,
                CONNECT_TIMEOUT_MS,
                READ_TIMEOUT_MS
        );

        if (host == null ? lastHost == null : host.equals(lastHost)) {
            blocked = Collections.unmodifiableSet(next);
            allowed = Collections.unmodifiableSet(nextAllowed);
            rulesHost = host;
            lastRefreshMs = System.currentTimeMillis();
            lastSuccessfulFetchMs = lastRefreshMs;
        }

        if (manifestUploaded.compareAndSet(false, true)) {
            try {
                pushManifest();
            } catch (Throwable t) {

                manifestUploaded.set(false);
                LOG.warn("manifest upload failed: {}", t.toString());
            }
        }
    }

    private void pushManifest() throws IOException {
        byte[] manifestBytes;
        String signature;
        try (InputStream manifest = RemoteRulesService.class.getResourceAsStream(
                "/phaze/module-manifest.json");
             InputStream signatureStream = RemoteRulesService.class.getResourceAsStream(
                     "/phaze/module-manifest.sig")) {
            if (manifest == null || signatureStream == null) {
                throw new IOException("signed module manifest is missing from JAR");
            }
            manifestBytes = manifest.readAllBytes();
            signature = new String(signatureStream.readAllBytes(), StandardCharsets.US_ASCII).trim();
        }
        if (manifestBytes.length == 0 || signature.isEmpty()) {
            throw new IOException("signed module manifest is empty");
        }

        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        body.addProperty("payload", Base64.getEncoder().encodeToString(manifestBytes));
        body.addProperty("signature", signature);

        URI uri = URI.create(apiBase + "/api/manifest");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();
            if (status != 204 && status != 200) {

                throw new IOException("manifest POST returned " + status);
            }
        } finally {
            conn.disconnect();
        }
    }

}
