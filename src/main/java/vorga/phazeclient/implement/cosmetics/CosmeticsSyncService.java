package vorga.phazeclient.implement.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.base.util.PhazeEventService;
import vorga.phazeclient.base.util.RemoteRulesService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight cross-server cosmetic presence.
 *
 * <p>The backend stores and pushes only a UUID, catalog id and enabled bit.
 * Models, textures, physics and rendering remain client-side.
 *
 * <h3>Traffic model</h3>
 * Cosmetic state is global per player UUID, and every change is pushed over
 * the Phaze SSE stream. So a UUID only has to be queried once: after that the
 * stream keeps it current for as long as it stays connected. Players walking
 * in and out of render distance therefore cost nothing; only never-seen UUIDs
 * are queried, batched and rate-limited. A stream reconnect ({@code hello})
 * invalidates the cache because events may have been missed while offline,
 * and while the stream is down entries expire after {@link #OFFLINE_STALE_MS}.
 */
public final class CosmeticsSyncService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeCosmeticsSync");
    private static final CosmeticsSyncService INSTANCE = new CosmeticsSyncService();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    /** Server-side cap for {@code /api/cosmetics/query}. */
    private static final int MAX_QUERY_PLAYERS = 200;
    private static final int MAX_CACHED_PLAYERS = 4096;
    private static final long ROSTER_CHECK_MS = 500L;
    /** Lets players that load in the same moment share one request. */
    private static final long BATCH_WINDOW_MS = 400L;
    /**
     * The server allows 30 queries per minute per IP, and players behind one
     * NAT share that budget, so a single client stays well below it.
     */
    private static final long MIN_QUERY_INTERVAL_MS = 5_000L;
    private static final long OFFLINE_STALE_MS = 10L * 60L * 1000L;
    private static final long FAILURE_BACKOFF_MIN_MS = 5_000L;
    private static final long FAILURE_BACKOFF_MAX_MS = 2L * 60L * 1000L;
    private static final long RATE_LIMITED_BACKOFF_MS = 65_000L;
    private static final String SLOT_SEPARATOR = "|";

    private final ScheduledExecutorService io =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "phaze-cosmetics-sync");
                thread.setDaemon(true);
                return thread;
            });
    private final Map<UUID, RemoteCosmetic> states = new ConcurrentHashMap<>();
    /** Local time a UUID's state was last confirmed, present or absent. */
    private final Map<UUID, Long> confirmedAt = new ConcurrentHashMap<>();
    /** Last payload the server accepted per local UUID during this launch. */
    private final Map<UUID, String> publishedPayloads = new ConcurrentHashMap<>();
    private final AtomicBoolean queryInFlight = new AtomicBoolean(false);
    private final AtomicInteger cacheEpoch = new AtomicInteger();

    private ScheduledFuture<?> pendingPublish;
    private UUID localPlayerUuid;
    private long lastRosterCheckMs;
    private long pendingSinceMs;
    private volatile long nextQueryAllowedMs;
    private volatile long failureBackoffMs = FAILURE_BACKOFF_MIN_MS;
    private volatile boolean publishOnJoin = true;

    private CosmeticsSyncService() {
    }

    public static CosmeticsSyncService getInstance() {
        return INSTANCE;
    }

    /** Called from the regular client tick; it never performs network I/O. */
    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null
                || client.getNetworkHandler() == null) {
            localPlayerUuid = null;
            pendingSinceMs = 0L;
            publishOnJoin = true;
            return;
        }

        UUID currentUuid = client.player.getUuid();
        if (!currentUuid.equals(localPlayerUuid)) {
            localPlayerUuid = currentUuid;
            publishOnJoin = true;
        }
        if (publishOnJoin) {
            publishOnJoin = false;
            publishLocalState();
        }

        long now = System.currentTimeMillis();
        if (now - lastRosterCheckMs < ROSTER_CHECK_MS) return;
        lastRosterCheckMs = now;

        List<UUID> loaded = loadedPlayerUuids(client);
        trimCache(loaded);
        List<UUID> unknown = unconfirmed(loaded, now);
        if (unknown.isEmpty()) {
            pendingSinceMs = 0L;
            return;
        }
        if (pendingSinceMs == 0L) pendingSinceMs = now;
        if (now - pendingSinceMs < BATCH_WINDOW_MS || now < nextQueryAllowedMs) return;
        if (query(unknown)) pendingSinceMs = 0L;
    }

    /**
     * Coalesces setSelected + setEquipped into one write. A quick series of UI
     * clicks replaces the pending task instead of producing one request each.
     */
    public synchronized void publishLocalState() {
        if (pendingPublish != null) pendingPublish.cancel(false);
        pendingPublish = io.schedule(this::postLocalState, 120L, TimeUnit.MILLISECONDS);
    }

    /** Called on SSE {@code hello}: anything may have changed while the stream was down. */
    public void requestRefresh() {
        cacheEpoch.incrementAndGet();
        confirmedAt.clear();
        nextQueryAllowedMs = 0L;
    }

    /** Applies an immediate cosmetic SSE event; each one carries the player's full state. */
    public void acceptEvent(JsonObject event) {
        try {
            UUID uuid = UUID.fromString(event.get("playerUuid").getAsString());
            RemoteCosmetic decoded = decode(
                    event.get("cosmetic").getAsString(),
                    event.get("equipped").getAsBoolean(),
                    event.get("updatedAt").getAsLong()
            );
            putIfNewer(uuid, decoded);
            confirmedAt.put(uuid, System.currentTimeMillis());
        } catch (Throwable error) {
            LOG.debug("invalid cosmetic event: {}", error.toString());
        }
    }

    public String selectionFor(UUID playerUuid) {
        String wing = wingSelectionFor(playerUuid);
        return wing != null ? wing : capeSelectionFor(playerUuid);
    }

    public String wingSelectionFor(UUID playerUuid) {
        RemoteCosmetic state = equippedState(playerUuid);
        return state == null ? null : available(state.wing);
    }

    public String capeSelectionFor(UUID playerUuid) {
        RemoteCosmetic state = equippedState(playerUuid);
        return state == null ? null : available(state.cape);
    }

    public String hatSelectionFor(UUID playerUuid) {
        RemoteCosmetic state = equippedState(playerUuid);
        return state == null ? null : available(state.hat);
    }

    public String petSelectionFor(UUID playerUuid) {
        RemoteCosmetic state = equippedState(playerUuid);
        return state == null ? null : available(state.pet);
    }

    private RemoteCosmetic equippedState(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        return state != null && state.equipped ? state : null;
    }

    private static String available(String id) {
        if (id == null || CosmeticsState.NONE.equalsIgnoreCase(id)) return null;
        return CosmeticsState.getInstance().isAvailable(id) ? id : null;
    }

    private void postLocalState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            publishOnJoin = true;
            return;
        }
        RemoteRulesService rules = RemoteRulesService.getInstance();
        if (rules.getApiBase() == null || rules.getApiBase().isBlank()) return;

        CosmeticsState local = CosmeticsState.getInstance();
        UUID playerUuid = client.player.getUuid();
        String wing = local.isWingEquipped() ? local.getSelectedWing() : CosmeticsState.NONE;
        String cape = local.isCapeEquipped() ? local.getSelectedCape() : CosmeticsState.NONE;
        String hat = local.isHatEquipped() ? local.getSelectedHat() : CosmeticsState.NONE;
        String pet = local.isPetEquipped() ? local.getSelectedPet() : CosmeticsState.NONE;
        String cosmetic = String.join(SLOT_SEPARATOR, wing, cape, hat, pet);
        boolean equipped = local.isEquipped();
        String payloadKey = cosmetic + "#" + equipped;
        if (payloadKey.equals(publishedPayloads.get(playerUuid))) return;

        JsonObject body = new JsonObject();
        body.addProperty("clientId", rules.getClientId());
        body.addProperty("playerUuid", playerUuid.toString());
        body.addProperty("cosmetic", cosmetic);
        body.addProperty("equipped", equipped);
        try {
            request("/api/cosmetics/state", body);
            publishedPayloads.put(playerUuid, payloadKey);
        } catch (HttpStatusException error) {
            LOG.debug("state publish rejected: HTTP {}", error.status);
            if (error.status == 429 || error.status >= 500) publishOnJoin = true;
        } catch (Throwable error) {
            LOG.debug("state publish failed: {}", error.toString());
            publishOnJoin = true;
        }
    }

    /** Returns false when nothing was sent, so the caller keeps the batch pending. */
    private boolean query(List<UUID> playerUuids) {
        if (playerUuids.isEmpty() || !queryInFlight.compareAndSet(false, true)) return false;
        String apiBase = RemoteRulesService.getInstance().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            queryInFlight.set(false);
            return false;
        }
        List<UUID> requested = List.copyOf(playerUuids.size() > MAX_QUERY_PLAYERS
                ? playerUuids.subList(0, MAX_QUERY_PLAYERS)
                : playerUuids);
        long startedAt = System.currentTimeMillis();
        int epoch = cacheEpoch.get();
        nextQueryAllowedMs = startedAt + MIN_QUERY_INTERVAL_MS;
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray ids = new JsonArray();
                requested.forEach(uuid -> ids.add(uuid.toString()));
                body.add("playerUuids", ids);
                JsonArray sources = new JsonArray();
                sources.add("phaze");
                body.add("sources", sources);
                JsonObject response = request("/api/cosmetics/query", body);
                JsonArray array = response.getAsJsonArray("states");
                Set<UUID> returned = new HashSet<>();
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) continue;
                        JsonObject value = element.getAsJsonObject();
                        String source = value.has("source") ? value.get("source").getAsString() : "phaze";
                        if (!"phaze".equals(source)) continue;
                        UUID uuid = UUID.fromString(value.get("playerUuid").getAsString());
                        returned.add(uuid);
                        putIfNewer(uuid, decode(
                                value.get("cosmetic").getAsString(),
                                value.get("equipped").getAsBoolean(),
                                value.get("updatedAt").getAsLong()
                        ));
                    }
                }
                for (UUID uuid : requested) {
                    if (!returned.contains(uuid)) {
                        states.computeIfPresent(uuid, (ignored, current) ->
                                current.receivedAt < startedAt ? null : current);
                    }
                }
                if (cacheEpoch.get() == epoch) {
                    long confirmed = System.currentTimeMillis();
                    requested.forEach(uuid -> confirmedAt.put(uuid, confirmed));
                }
                failureBackoffMs = FAILURE_BACKOFF_MIN_MS;
            } catch (HttpStatusException error) {
                LOG.debug("roster query rejected: HTTP {}", error.status);
                if (error.status == 429) {
                    nextQueryAllowedMs = System.currentTimeMillis() + RATE_LIMITED_BACKOFF_MS;
                } else if (error.status >= 400 && error.status < 500) {
                    // The same body would be rejected again; don't ask about these players.
                    long confirmed = System.currentTimeMillis();
                    requested.forEach(uuid -> confirmedAt.put(uuid, confirmed));
                } else {
                    backOff();
                }
            } catch (Throwable error) {
                LOG.debug("roster query failed: {}", error.toString());
                backOff();
            } finally {
                queryInFlight.set(false);
            }
        });
        return true;
    }

    private void backOff() {
        long delay = failureBackoffMs;
        failureBackoffMs = Math.min(FAILURE_BACKOFF_MAX_MS, delay * 2L);
        nextQueryAllowedMs = System.currentTimeMillis() + delay;
    }

    private JsonObject request(String path, JsonObject body) throws Exception {
        String base = RemoteRulesService.getInstance().getApiBase();
        HttpURLConnection connection = (HttpURLConnection)
                URI.create(base + path).toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/126.0.0.0 Safari/537.36");
            connection.setDoOutput(true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    json.append(buffer, 0, count);
                }
                if (json.isEmpty()) return new JsonObject();
                JsonElement parsed = JsonParser.parseString(json.toString());
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            }
        } finally {
            connection.disconnect();
        }
    }

    private List<UUID> loadedPlayerUuids(MinecraftClient client) {
        List<UUID> result = new ArrayList<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) continue;
            UUID uuid = player.getUuid();
            // Server-side NPC plugins mint version-2 UUIDs; real players are v3 (offline) or v4.
            if (uuid.version() == 2) continue;
            result.add(uuid);
        }
        return result;
    }

    private List<UUID> unconfirmed(List<UUID> loaded, long now) {
        boolean live = PhazeEventService.getInstance().isConnected();
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : loaded) {
            Long confirmed = confirmedAt.get(uuid);
            if (confirmed == null || (!live && now - confirmed >= OFFLINE_STALE_MS)) {
                result.add(uuid);
            }
        }
        return result;
    }

    private void trimCache(List<UUID> loaded) {
        if (confirmedAt.size() <= MAX_CACHED_PLAYERS && states.size() <= MAX_CACHED_PLAYERS) return;
        Set<UUID> keep = new HashSet<>(loaded);
        confirmedAt.keySet().retainAll(keep);
        states.keySet().retainAll(keep);
    }

    private void putIfNewer(UUID uuid, RemoteCosmetic next) {
        states.compute(uuid, (ignored, current) ->
                current == null || next.updatedAt >= current.updatedAt ? next : current);
    }

    private static RemoteCosmetic decode(String value, boolean equipped, long updatedAt) {
        long receivedAt = System.currentTimeMillis();
        String raw = value == null ? CosmeticsState.NONE : value;
        String[] slots = raw.split("\\Q" + SLOT_SEPARATOR + "\\E", -1);
        if (slots.length >= 2) {
            String wing = slots[0];
            String cape = slots[1];
            String hat = slots.length >= 3 ? slots[2] : CosmeticsState.NONE;
            String pet = slots.length >= 4 ? slots[3] : CosmeticsState.NONE;
            return new RemoteCosmetic(
                    wing.isBlank() ? CosmeticsState.NONE : wing,
                    cape.isBlank() ? CosmeticsState.NONE : cape,
                    hat.isBlank() ? CosmeticsState.NONE : hat,
                    pet.isBlank() ? CosmeticsState.NONE : pet,
                    equipped,
                    updatedAt,
                    receivedAt
            );
        }
        // Backward compatibility with states written by 1.0 clients.
        boolean cape = CosmeticsState.isCape(raw);
        return new RemoteCosmetic(
                cape ? CosmeticsState.NONE : raw,
                cape ? raw : CosmeticsState.NONE,
                CosmeticsState.NONE,
                CosmeticsState.NONE,
                equipped,
                updatedAt,
                receivedAt
        );
    }

    private record RemoteCosmetic(
            String wing,
            String cape,
            String hat,
            String pet,
            boolean equipped,
            long updatedAt,
            long receivedAt
    ) {
    }

    private static final class HttpStatusException extends Exception {
        final int status;

        HttpStatusException(int status) {
            super("HTTP " + status, null, false, false);
            this.status = status;
        }
    }
}
