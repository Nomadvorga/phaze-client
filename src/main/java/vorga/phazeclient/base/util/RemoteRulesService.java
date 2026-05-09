package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
 * vorga.phazeclient.core.Main#onInitialize()}. From there a daemon
 * scheduler runs every {@value #HEARTBEAT_SECONDS} seconds and either:
 * <ul>
 *   <li>refreshes immediately if the player switched servers (host
 *       change), <strong>or</strong></li>
 *   <li>refreshes periodically every {@value #REFRESH_INTERVAL_SECONDS}
 *       seconds even if the host is the same, so admin edits propagate
 *       within a minute without the player having to reconnect.</li>
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

    /**
     * Default base URL — production Cloudflare Pages deployment of
     * phaze-rules-admin. Hosts both the operator dashboard (HTML) and
     * the public {@code /api/module-rules} endpoint we poll here, so
     * one URL is enough.
     *
     * <p>For local backend development, override with
     * {@code -Dphaze.rules.api=http://127.0.0.1:3001} on the JVM
     * command line.
     */
    private static final String DEFAULT_API_BASE = "https://phaze-rules-admin.pages.dev";

    /** How often the heartbeat thread runs (cheap host-equality check + maybe-refresh). */
    private static final long HEARTBEAT_SECONDS = 5L;

    /** Min interval between actual HTTP refreshes when the host hasn't changed. */
    private static final long REFRESH_INTERVAL_SECONDS = 60L;

    /** Per-request timeouts. The API is local-ish; if it's slow we just give up and fail-open. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "phaze-rules-poll");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private final String apiBase;
    private final boolean enabled;

    private volatile Set<String> blocked = Collections.emptySet();
    private volatile String lastHost = null;          // null = "never refreshed yet"
    private volatile long lastRefreshMs = 0L;

    private RemoteRulesService() {
        String configured = System.getProperty("phaze.rules.api", DEFAULT_API_BASE);
        // Trim trailing slashes so we can append /api/... cleanly.
        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        this.apiBase = configured;
        this.enabled = !configured.isEmpty();
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
        scheduler.scheduleWithFixedDelay(
                this::heartbeat,
                /* initialDelay */ 1L,
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
                fetchAsync(host);
            } else if (stale) {
                fetchAsync(host);
            }
        } catch (Throwable t) {
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
            } catch (Throwable ignored) {
                // Silent fail-open. Keeping a chatty log here would
                // spam users whose API isn't reachable.
            } finally {
                refreshInFlight.set(false);
            }
        });
    }

    private void fetch(String host) throws Exception {
        String encoded = URLEncoder.encode(host == null ? "" : host, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/api/module-rules?host=" + encoded))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            return; // fail-open: no change to current snapshot if it's still for this host
        }

        JsonElement parsed = JsonParser.parseString(res.body());
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
