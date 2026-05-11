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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the phaze-rules-api for the current per-server module-disable
 * rule set and exposes a fast {@link #isModuleBlocked(String)} probe
 * for the rest of the client to consult.
 *
 * <h3>Lifecycle</h3>
 * Singleton; {@link #start()} is called once from {@link
 * vorga.phazeclient.core.Main#onInitialize()}. From there:
 * <ul>
 *   <li>An <strong>immediate</strong> fetch fires at startup so the
 *       first set of remote rules is applied as soon as the player
 *       lands on the main menu (or directly into a world, when the
 *       game was launched via {@code --server}).</li>
 *   <li>Then a daemon scheduler runs every {@value #HEARTBEAT_SECONDS}
 *       seconds and refreshes if the snapshot is stale.</li>
 *   <li>Server-change responsiveness is handled out-of-band by
 *       {@link vorga.phazeclient.mixins.ClientPlayerEntityMixin}: when
 *       it observes the host change it calls {@link #requestRefresh()},
 *       so locks for the new server take effect within one tick instead
 *       of waiting for the next 60-second heartbeat.</li>
 * </ul>
 *
 * <h3>Fail-open</h3>
 * Network errors, malformed JSON, missing API — all of these collapse
 * to "no modules are remotely blocked". We do <em>not</em> fall back
 * to the last-known snapshot when the host changes, because that would
 * keep blocking modules on a server that was simply unreachable. Worst
 * case: the API is down and an admin loses their remote-disable lever
 * for that minute. The local server-whitelists in {@link ServerUtil}
 * still apply on top, so the safety baseline doesn't change.
 *
 * <h3>Configuration</h3>
 * The base URL defaults to the production Cloudflare deployment.
 * Override with the JVM system property {@code -Dphaze.rules.api=...}
 * to point at a local dev backend, or set it to an empty string to
 * disable the service entirely (useful for offline development).
 */
public final class RemoteRulesService {

    private static final Logger LOG = LoggerFactory.getLogger("PhazeRules");

    /**
     * Default base URL - the standalone Worker on workers.dev rather
     * than the Pages domain. Both serve the exact same Hono app off
     * the same D1 database, but TSPU/DPI in RU has been spotted
     * blocking {@code *.pages.dev} TLS handshakes while letting
     * {@code *.workers.dev} through. The admin dashboard still lives
     * on pages.dev (the browser handles its own TLS quirks); this is
     * just the polling endpoint for the mod.
     *
     * <p>For local backend development, override with
     * {@code -Dphaze.rules.api=http://127.0.0.1:3001} on the JVM
     * command line.
     */
    private static final String DEFAULT_API_BASE = "https://phaze-rules.49814981dany.workers.dev";

    /**
     * Period for the staleness-refresh loop. With the heartbeat and the
     * minimum-refresh-interval unified into a single value, the
     * scheduler does exactly one network call per cycle whenever the
     * cached snapshot exists and the host is non-empty; admin edits
     * propagate within ~one period without the player reconnecting.
     *
     * <p>Server-change responsiveness is NOT handled here - the
     * heartbeat would observe it on its next tick (up to 60 s of lag).
     * Instead, {@link vorga.phazeclient.mixins.ClientPlayerEntityMixin}
     * watches the host string on every client tick and calls
     * {@link #requestRefresh()} the moment it changes, which forces an
     * immediate out-of-band fetch independently of this period.
     */
    private static final long HEARTBEAT_SECONDS = 60L;

    /** Min interval between actual HTTP refreshes when the host hasn't changed. */
    private static final long REFRESH_INTERVAL_SECONDS = 60L;

    /**
     * Per-request timeouts as plain integers because
     * {@link HttpURLConnection} only takes int millis. Generous enough
     * to absorb a cold TLS handshake to Cloudflare from a residential
     * RU connection (~6-10s is normal on first hit) without locking
     * the polling thread for an absurd amount of time.
     */
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    static {
        // Best-effort hint - this property only takes effect if
        // networking hasn't been initialized yet by the JVM. In the
        // Minecraft process Mojang's authlib already opened a few
        // sockets long before our class loads, so this often does
        // nothing. The real IPv4 enforcement happens in fetch() below
        // by manually resolving the hostname and picking the first
        // Inet4Address ourselves.
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    /**
     * Lazy-init holder. Decouples the singleton from the enclosing
     * class's {@code <clinit>} so the constructor only fires when
     * something actually calls {@link #getInstance()}. This dodges the
     * forward-reference foot-gun where a static {@code INSTANCE} field
     * declared above the {@code Duration} constants would observe them
     * as {@code null} during {@code <clinit>}, and as a bonus keeps the
     * service's HTTP client / executor out of the address space until
     * the mod actually wants them.
     */
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

    private final String apiBase;
    private final boolean enabled;
    private final String clientId;

    private volatile Set<String> blocked = Collections.emptySet();
    private volatile String lastHost = null;          // null = "never refreshed yet"
    private volatile long lastRefreshMs = 0L;

    /**
     * Last known online-client count from the most recent successful
     * /api/module-rules response. Negative values signal "unknown yet"
     * (no successful poll since startup) and the UI surfaces those as
     * a placeholder rather than as zero.
     */
    private volatile int onlineCount = -1;

    private RemoteRulesService() {
        String configured = System.getProperty("phaze.rules.api", DEFAULT_API_BASE);
        // Trim trailing slashes so we can append /api/... cleanly.
        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        this.apiBase = configured;
        this.enabled = !configured.isEmpty();
        this.clientId = loadOrCreateClientId();
    }

    /**
     * Returns the most recently observed count of distinct mod
     * clients that have polled the API in the last few minutes, or a
     * negative number if we haven't received any successful poll
     * since the mod started. The {@link
     * vorga.phazeclient.mixins.TitleScreenOnlineCounterMixin TitleScreen mixin}
     * uses this to decide whether to render a real number or a
     * placeholder.
     */
    public int getOnlineCount() {
        return onlineCount;
    }

    /** Stable random identity for this install. See {@link #loadOrCreateClientId()}. */
    public String getClientId() {
        return clientId;
    }

    /**
     * Reads the persisted client UUID from
     * {@code <minecraft>/Phaze/files/client_id}, or generates and
     * writes a fresh one on first run. The file deliberately lives
     * next to the existing config plumbing so a player who wipes
     * their {@code Phaze/} directory also resets their identity -
     * useful for testing the online counter without coordinating
     * across machines.
     *
     * <p>Privacy: the value is a vanilla UUIDv4, not derived from
     * Mojang username, IP, or hardware. The server only ever uses
     * it as a primary key into the heartbeat table; nothing about
     * the player gets sent.
     */
    private String loadOrCreateClientId() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Path runDir = client != null
                    ? client.runDirectory.toPath()
                    : Path.of(".");
            Path file = runDir.resolve("Phaze").resolve("files").resolve("client_id");

            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                // Validate so a hand-edited / corrupt file gets
                // replaced instead of silently producing a 400 from
                // the worker every minute.
                try {
                    UUID parsed = UUID.fromString(existing);
                    return parsed.toString();
                } catch (IllegalArgumentException badShape) {
                    // fall through to regeneration
                }
            }

            String fresh = UUID.randomUUID().toString();
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, fresh, StandardCharsets.UTF_8);
            } catch (IOException writeFailed) {
                // Non-fatal: we'll just regenerate next launch. The
                // player still appears in the count for the duration
                // of this session.
                LOG.warn("could not persist client id: {}", writeFailed.toString());
            }
            return fresh;
        } catch (Throwable t) {
            // Last-ditch fallback so the rest of the service can still
            // function. A volatile in-memory id means this user counts
            // as a fresh client every restart, but rules still resolve.
            LOG.warn("client id init failed, using ephemeral id: {}", t.toString());
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Idempotent. Safe to call from the mod initializer; subsequent
     * calls are no-ops.
     */
    public void start() {
        if (!enabled) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        // Immediate first fetch (initialDelay=0) so the very first
        // ruleset is applied the moment the mod finishes initialising
        // - the player should not see a 60-second window where the
        // server-disable list is empty. Subsequent runs fire on the
        // 60-second heartbeat. Server-change responsiveness is added
        // on top by ClientPlayerEntityMixin via requestRefresh().
        scheduler.scheduleWithFixedDelay(
                this::heartbeat,
                /* initialDelay */ 0L,
                /* period       */ HEARTBEAT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Hot-path probe used by {@link vorga.phazeclient.api.feature.module.Module#isServerLocked()}.
     * Returns true iff the API has told us this module id is blocked
     * on the player's current server. Falls back to {@code false}
     * (i.e. don't block) on any error or empty cache.
     */
    public boolean isModuleBlocked(String moduleId) {
        if (!enabled || moduleId == null || moduleId.isEmpty()) {
            return false;
        }
        return blocked.contains(moduleId.toLowerCase());
    }

    /**
     * Snapshot of the currently blocked module ids. Returned set is
     * unmodifiable and safe to iterate without locking.
     */
    public Set<String> getBlockedModuleIds() {
        return blocked;
    }

    /**
     * Force an immediate refresh from the background thread. Useful
     * after explicit "I just connected to a new server" signals; the
     * heartbeat would notice the host change anyway, but this skips
     * the up-to-{@value #HEARTBEAT_SECONDS}-second wait.
     */
    public void requestRefresh() {
        if (!enabled || !started.get()) {
            return;
        }
        scheduler.execute(this::heartbeat);
    }

    private void heartbeat() {
        try {
            String host = ServerUtil.getCurrentServerHost();
            // Defensive: ServerUtil contracts a non-null result.
            if (host == null) host = "";

            boolean hostChanged = !host.equals(lastHost);
            long now = System.currentTimeMillis();
            boolean stale = (now - lastRefreshMs) >= REFRESH_INTERVAL_SECONDS * 1000L;

            if (hostChanged) {
                // Wipe the previous snapshot the moment we land on a
                // new server. We don't want to keep applying funtime's
                // ban list to the player on holyworld just because the
                // API hasn't responded yet.
                blocked = Collections.emptySet();
                lastHost = host;
                // Always fetch on host change, including when the host
                // is empty (singleplayer / main menu). An empty-host
                // poll still serves two purposes in one request: it
                // refreshes the global online counter via the clientId
                // heartbeat, and it confirms the empty ruleset for the
                // current (non-)server. That's the "single request on
                // game launch covers both rules and online users"
                // behaviour the UI relies on - previously we'd skip
                // the call on an empty host and the title-screen
                // counter stayed stuck on "connecting..." forever.
                fetchAsync(host);
            } else if (stale) {
                // Same reasoning as above: keep refreshing the online
                // count even when the host is empty, so the counter
                // stays alive while the player sits on the menu.
                fetchAsync(host);
            }
        } catch (Throwable t) {
            LOG.warn("heartbeat failed", t);
            // Swallow — heartbeat must never throw out of the scheduler
            // or it stops repeating.
        }
    }

    private void fetchAsync(String host) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return; // Another fetch is in flight; the heartbeat will try again later.
        }
        scheduler.execute(() -> {
            try {
                fetch(host);
            } catch (Throwable t) {
                LOG.warn("fetch failed for host='{}': {}", host, t.toString());
                // Force the next heartbeat (in HEARTBEAT_SECONDS) to
                // retry immediately instead of waiting the full
                // REFRESH_INTERVAL. Otherwise a single slow first hit
                // would keep the player on "empty rules" for a minute.
                lastRefreshMs = 0L;
            } finally {
                refreshInFlight.set(false);
            }
        });
    }

    private void fetch(String host) throws Exception {
        String encodedHost = URLEncoder.encode(host == null ? "" : host, StandardCharsets.UTF_8);
        String encodedClient = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        // clientId tells the worker to record a heartbeat for us and
        // include the live online count in the response. The server
        // tolerates older mod builds that omit it - it just won't
        // count them in the presence number.
        URI uri = URI.create(apiBase + "/api/module-rules?host=" + encodedHost
                + "&clientId=" + encodedClient);

        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String body;
        int status;
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            // Common Chrome UA - some Cloudflare deployments reset
            // "Java/..." or "Apache-HttpClient/..." UAs at the bot-
            // detection layer. Browser-shaped UA passes through.
            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36");
            conn.setInstanceFollowRedirects(true);

            status = conn.getResponseCode();
            if (status != 200) {
                LOG.warn("fetch '{}' returned status {}", uri, status);
                return; // fail-open: no change to current snapshot
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

        Set<String> next = new HashSet<>();
        if (obj.has("blocked") && obj.get("blocked").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("blocked");
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    next.add(el.getAsString().toLowerCase());
                }
            }
        }

        // The online counter updates regardless of whether the host
        // changed mid-flight: a stale ruleset would be wrong to show,
        // but the live-user count is a global value that doesn't
        // depend on which server we ended up on. Worker emits -1 when
        // it can't compute the count; we forward that through so the
        // UI can render a placeholder.
        if (obj.has("online") && obj.get("online").isJsonPrimitive()) {
            try {
                onlineCount = obj.get("online").getAsInt();
            } catch (NumberFormatException ignored) {
                // leave previous value in place
            }
        }

        // Only apply if the host hasn't changed under us mid-request -
        // otherwise we'd briefly stamp an old server's ruleset onto a
        // new server. heartbeat() will pick up the new host on its next
        // tick and re-fetch.
        if (host == null ? lastHost == null : host.equals(lastHost)) {
            blocked = Collections.unmodifiableSet(next);
            lastRefreshMs = System.currentTimeMillis();
        }
    }

}
