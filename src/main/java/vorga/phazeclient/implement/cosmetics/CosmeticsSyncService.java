package vorga.phazeclient.implement.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.base.util.RemoteRulesService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight cross-server cosmetic presence.
 *
 * <p>The backend stores and pushes only a UUID, catalog id and enabled bit.
 * Models, textures, physics and rendering remain client-side. Initial state is
 * fetched once for the currently loaded player entities; later selection
 * changes arrive over the existing Phaze SSE connection.
 */
public final class CosmeticsSyncService {
    private static final Logger LOG = LoggerFactory.getLogger("PhazeCosmeticsSync");
    private static final CosmeticsSyncService INSTANCE = new CosmeticsSyncService();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_QUERY_PLAYERS = 200;
    private static final long FALLBACK_REFRESH_MS = 10L * 60L * 1000L;
    private static final String SLOT_SEPARATOR = "|";

    private final ScheduledExecutorService io =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "phaze-cosmetics-sync");
                thread.setDaemon(true);
                return thread;
            });
    private final Map<UUID, RemoteCosmetic> states = new ConcurrentHashMap<>();
    private final Map<UUID, PulseRemoteCosmetic> pulseStates = new ConcurrentHashMap<>();
    private final Map<String, PulseGraffiti> pulseGraffiti = new ConcurrentHashMap<>();
    private final AtomicBoolean queryInFlight = new AtomicBoolean(false);
    private final AtomicBoolean graffitiQueryInFlight = new AtomicBoolean(false);
    private final AtomicReference<GraffitiQuery> pendingGraffitiQuery = new AtomicReference<>();

    private ScheduledFuture<?> pendingPublish;
    private UUID localPlayerUuid;
    private String rosterFingerprint = "";
    private long lastRosterCheckMs;
    private volatile long lastQueryMs;
    private volatile boolean publishOnJoin = true;
    private volatile boolean forceRefresh;
    private volatile String graffitiContext = "";
    private volatile long lastGraffitiQueryMs;
    private volatile long lastPulseCosmeticRevision;
    private volatile long lastPulseGraffitiRevision;

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
            rosterFingerprint = "";
            states.clear();
            pulseStates.clear();
            pulseGraffiti.clear();
            graffitiContext = "";
            publishOnJoin = true;
            return;
        }

        UUID currentUuid = client.player.getUuid();
        if (!currentUuid.equals(localPlayerUuid)) {
            localPlayerUuid = currentUuid;
            rosterFingerprint = "";
            publishOnJoin = true;
            forceRefresh = true;
        }
        if (publishOnJoin) {
            publishOnJoin = false;
            publishLocalState();
        }

        long now = System.currentTimeMillis();
        if (!forceRefresh && now - lastRosterCheckMs < 2_000L) return;
        lastRosterCheckMs = now;

        List<UUID> players = loadedPlayerUuids(client);
        String fingerprint = players.toString();
        boolean rosterChanged = !fingerprint.equals(rosterFingerprint);
        boolean fallbackDue = now - lastQueryMs >= FALLBACK_REFRESH_MS;
        if (forceRefresh || rosterChanged || fallbackDue) {
            forceRefresh = false;
            rosterFingerprint = fingerprint;
            query(players);
            if (rosterChanged) {
                io.schedule(() -> forceRefresh = true, 2L, TimeUnit.SECONDS);
            }
        }

        String serverKey = vorga.phazeclient.implement.cosmetics.bridge.SharedWorldKey.get(client);
        String dimension = vorga.phazeclient.implement.cosmetics.bridge.SharedWorldKey.dimension(client);
        String context = String.valueOf(serverKey) + '|' + dimension;
        if (serverKey != null && dimension != null
                && (!context.equals(graffitiContext)
                || now - lastGraffitiQueryMs >= FALLBACK_REFRESH_MS)) {
            graffitiContext = context;
            queryGraffiti(serverKey, dimension);
        }
    }

    /**
     * Coalesces setSelected + setEquipped into one write. A quick series of UI
     * clicks replaces the pending task instead of producing one request each.
     */
    public synchronized void publishLocalState() {
        if (pendingPublish != null) pendingPublish.cancel(false);
        pendingPublish = io.schedule(this::postLocalState, 120L, TimeUnit.MILLISECONDS);
    }

    /** Used after SSE reconnect/hello to repair any event missed while offline. */
    public void requestRefresh() {
        forceRefresh = true;
        lastGraffitiQueryMs = 0L;
    }

    /** Applies an immediate cosmetic SSE event. */
    public void acceptEvent(JsonObject event) {
        try {
            UUID uuid = UUID.fromString(event.get("playerUuid").getAsString());
            String cosmetic = event.get("cosmetic").getAsString();
            boolean equipped = event.get("equipped").getAsBoolean();
            long updatedAt = event.get("updatedAt").getAsLong();
            RemoteCosmetic decoded = decode(cosmetic, equipped, updatedAt);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            client.execute(() -> {
                if (!isLoadedPlayer(uuid)) return;
                putIfNewer(uuid, decoded);
            });
        } catch (Throwable error) {
            LOG.debug("invalid cosmetic event: {}", error.toString());
        }
    }

    /** Applies Pulse Cosmetics' separate SSE namespace without affecting Phaze state. */
    public void acceptPulseEvent(JsonObject event) {
        try {
            notePulseRevision(event, false);
            UUID uuid = UUID.fromString(event.get("playerUuid").getAsString());
            PulseRemoteCosmetic decoded = decodePulse(
                    event.get("cosmetic").getAsString(),
                    event.get("equipped").getAsBoolean(),
                    event.get("updatedAt").getAsLong()
            );
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;
            client.execute(() -> {
                if (!isLoadedPlayer(uuid)) return;
                putPulseIfNewer(uuid, decoded);
            });
        } catch (Throwable error) {
            LOG.debug("invalid pulse cosmetic event: {}", error.toString());
        }
    }

    public void acceptPulseGraffitiEvent(JsonObject event) {
        notePulseRevision(event, true);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            try {
                String serverKey = event.get("serverKey").getAsString();
                String dimension = event.get("dimension").getAsString();
                if (!(serverKey + '|' + dimension).equals(graffitiContext)) return;
                BlockPos pos = new BlockPos(event.get("x").getAsInt(), event.get("y").getAsInt(), event.get("z").getAsInt());
                Direction face = Direction.byId(event.get("face").getAsString());
                if (face == null) return;
                String key = graffitiKey(serverKey, dimension, pos, face);
                if ("delete".equals(event.get("action").getAsString())) {
                    pulseGraffiti.remove(key);
                } else {
                    int id = event.get("graffitiId").getAsInt();
                    vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry entry = vorga.phazeclient.implement.cosmetics.bridge.CosmeticCatalog.byId(id);
                    if (entry != null && entry.category() == vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory.GRAFFITI) {
                        pulseGraffiti.put(key, new PulseGraffiti(serverKey, dimension, pos, face, id));
                    }
                }
                if (graffitiQueryInFlight.get()) queryGraffiti(serverKey, dimension);
            } catch (Throwable error) {
                LOG.debug("invalid shared graffiti event: {}", error.toString());
            }
        });
    }

    public List<PulseGraffiti> pulseGraffiti() {
        return List.copyOf(pulseGraffiti.values());
    }

    public void acceptPushCheckpoint(JsonObject payload) {
        try {
            JsonObject revisions = payload.getAsJsonObject("pulseRevisions");
            if (revisions == null) return;
            long cosmetic = revisions.get("cosmetic").getAsLong();
            long graffiti = revisions.get("graffiti").getAsLong();
            if (cosmetic > lastPulseCosmeticRevision) {
                lastPulseCosmeticRevision = cosmetic;
                forceRefresh = true;
            }
            if (graffiti > lastPulseGraffitiRevision) {
                lastPulseGraffitiRevision = graffiti;
                lastGraffitiQueryMs = 0L;
            }
        } catch (Throwable error) {
            LOG.debug("invalid push checkpoint: {}", error.toString());
        }
    }

    private void notePulseRevision(JsonObject event, boolean graffiti) {
        if (!event.has("revision")) return;
        long revision = event.get("revision").getAsLong();
        if (graffiti) lastPulseGraffitiRevision = Math.max(lastPulseGraffitiRevision, revision);
        else lastPulseCosmeticRevision = Math.max(lastPulseCosmeticRevision, revision);
    }

    public vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry pulseEntryFor(
            UUID playerUuid,
            vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory category
    ) {
        if (playerUuid == null) return null;
        PulseRemoteCosmetic state = pulseStates.get(playerUuid);
        if (state == null || !state.equipped) return null;
        int id = state.id(category);
        vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry entry = vorga.phazeclient.implement.cosmetics.bridge.CosmeticCatalog.byId(id);
        return entry != null && entry.category() == category ? entry : null;
    }

    public String selectionFor(UUID playerUuid) {
        String wing = wingSelectionFor(playerUuid);
        return wing != null ? wing : capeSelectionFor(playerUuid);
    }

    public String wingSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.wing)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.wing)
                ? state.wing
                : null;
    }

    public String capeSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.cape)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.cape)
                ? state.cape
                : null;
    }

    public String hatSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.hat)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.hat)
                ? state.hat
                : null;
    }

    public String petSelectionFor(UUID playerUuid) {
        if (playerUuid == null) return null;
        RemoteCosmetic state = states.get(playerUuid);
        if (state == null || !state.equipped
                || CosmeticsState.NONE.equalsIgnoreCase(state.pet)) {
            return null;
        }
        return CosmeticsState.getInstance().isAvailable(state.pet)
                ? state.pet
                : null;
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
        JsonObject body = new JsonObject();
        body.addProperty("clientId", rules.getClientId());
        body.addProperty("playerUuid", client.player.getUuid().toString());
        String wing = local.isWingEquipped() ? local.getSelectedWing() : CosmeticsState.NONE;
        String cape = local.isCapeEquipped() ? local.getSelectedCape() : CosmeticsState.NONE;
        String hat = local.isHatEquipped() ? local.getSelectedHat() : CosmeticsState.NONE;
        String pet = local.isPetEquipped() ? local.getSelectedPet() : CosmeticsState.NONE;
        body.addProperty("cosmetic", String.join(SLOT_SEPARATOR, wing, cape, hat, pet));
        body.addProperty("equipped", local.isEquipped());
        try {
            request("/api/cosmetics/state", body);
        } catch (Throwable error) {
            LOG.debug("state publish failed: {}", error.toString());
            publishOnJoin = true;
        }
    }

    private void query(List<UUID> playerUuids) {
        if (playerUuids.isEmpty() || !queryInFlight.compareAndSet(false, true)) return;
        String apiBase = RemoteRulesService.getInstance().getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            queryInFlight.set(false);
            return;
        }
        List<UUID> requested = List.copyOf(playerUuids);
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray ids = new JsonArray();
                requested.forEach(uuid -> ids.add(uuid.toString()));
                body.add("playerUuids", ids);
                JsonArray sources = new JsonArray();
                sources.add("phaze");
                sources.add("pulse");
                body.add("sources", sources);
                JsonObject response = request("/api/cosmetics/query", body);
                JsonArray array = response.getAsJsonArray("states");
                Map<UUID, Boolean> returned = new ConcurrentHashMap<>();
                Map<UUID, Boolean> pulseReturned = new ConcurrentHashMap<>();
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) continue;
                        JsonObject value = element.getAsJsonObject();
                        UUID uuid = UUID.fromString(value.get("playerUuid").getAsString());
                        String source = value.has("source") ? value.get("source").getAsString() : "phaze";
                        if ("pulse".equals(source)) {
                            pulseReturned.put(uuid, Boolean.TRUE);
                            putPulseIfNewer(uuid, decodePulse(
                                    value.get("cosmetic").getAsString(),
                                    value.get("equipped").getAsBoolean(),
                                    value.get("updatedAt").getAsLong()
                            ));
                        } else {
                            returned.put(uuid, Boolean.TRUE);
                            putIfNewer(uuid, decode(
                                    value.get("cosmetic").getAsString(),
                                    value.get("equipped").getAsBoolean(),
                                    value.get("updatedAt").getAsLong()
                            ));
                        }
                    }
                }
                for (UUID uuid : requested) {
                    if (!returned.containsKey(uuid)) states.remove(uuid);
                    if (!pulseReturned.containsKey(uuid)) pulseStates.remove(uuid);
                }
                lastQueryMs = System.currentTimeMillis();
            } catch (Throwable error) {
                LOG.debug("roster query failed: {}", error.toString());
            } finally {
                queryInFlight.set(false);
            }
        });
    }

    private void queryGraffiti(String serverKey, String dimension) {
        pendingGraffitiQuery.set(new GraffitiQuery(serverKey, dimension));
        startGraffitiQueryDrain();
    }

    private void startGraffitiQueryDrain() {
        if (!graffitiQueryInFlight.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                GraffitiQuery query;
                while ((query = pendingGraffitiQuery.getAndSet(null)) != null) {
                    try {
                        GraffitiQuery currentQuery = query;
                        JsonObject body = new JsonObject();
                        body.addProperty("serverKey", currentQuery.serverKey());
                        body.addProperty("dimension", currentQuery.dimension());
                        JsonArray array = request("/api/graffiti/query", body).getAsJsonArray("graffiti");
                        Map<String, PulseGraffiti> next = new ConcurrentHashMap<>();
                        if (array != null) for (JsonElement element : array) {
                            JsonObject value = element.getAsJsonObject();
                            int id = value.get("graffitiId").getAsInt();
                            vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry entry = vorga.phazeclient.implement.cosmetics.bridge.CosmeticCatalog.byId(id);
                            Direction face = Direction.byId(value.get("face").getAsString());
                            if (entry == null || entry.category() != vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory.GRAFFITI || face == null) continue;
                            BlockPos pos = new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
                            PulseGraffiti graffiti = new PulseGraffiti(currentQuery.serverKey(), currentQuery.dimension(), pos, face, id);
                            next.put(graffitiKey(currentQuery.serverKey(), currentQuery.dimension(), pos, face), graffiti);
                        }
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) client.execute(() -> {
                            pulseGraffiti.entrySet().removeIf(entry ->
                                    entry.getValue().serverKey.equals(currentQuery.serverKey())
                                            && entry.getValue().dimension.equals(currentQuery.dimension()));
                            pulseGraffiti.putAll(next);
                        });
                        lastGraffitiQueryMs = System.currentTimeMillis();
                    } catch (Throwable error) {
                        LOG.debug("shared graffiti query failed: {}", error.toString());
                    }
                }
            } finally {
                graffitiQueryInFlight.set(false);
                if (pendingGraffitiQuery.get() != null) startGraffitiQueryDrain();
            }
        });
    }

    private static String graffitiKey(String serverKey, String dimension, BlockPos pos, Direction face) {
        return serverKey + '|' + dimension + '|' + pos.getX() + '|' + pos.getY()
                + '|' + pos.getZ() + '|' + face.asString();
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
                throw new IllegalStateException("HTTP " + status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    json.append(buffer, 0, count);
                }
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
            result.add(player.getUuid());
            if (result.size() >= MAX_QUERY_PLAYERS) break;
        }
        result.sort(Comparator.comparing(UUID::toString));
        return result;
    }

    private boolean isLoadedPlayer(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return false;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (uuid.equals(player.getUuid())) return true;
        }
        return false;
    }

    private void putIfNewer(UUID uuid, RemoteCosmetic next) {
        if (states.size() > 512) states.clear();
        states.compute(uuid, (ignored, current) ->
                current == null || next.updatedAt >= current.updatedAt ? next : current);
    }

    private void putPulseIfNewer(UUID uuid, PulseRemoteCosmetic next) {
        if (pulseStates.size() > 512) pulseStates.clear();
        pulseStates.compute(uuid, (ignored, current) ->
                current == null || next.updatedAt >= current.updatedAt ? next : current);
    }

    private static PulseRemoteCosmetic decodePulse(String value, boolean equipped, long updatedAt) {
        String[] slots = value == null ? new String[0] : value.split("\\|", -1);
        int[] ids = {-1, -1, -1, -1, -1};
        for (int i = 0; i < Math.min(ids.length, slots.length); i++) {
            try { ids[i] = Integer.parseInt(slots[i]); }
            catch (NumberFormatException ignored) { }
        }
        return new PulseRemoteCosmetic(ids, equipped, updatedAt);
    }

    private static RemoteCosmetic decode(String value, boolean equipped, long updatedAt) {
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
                    updatedAt
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
                updatedAt
        );
    }

    private record RemoteCosmetic(
            String wing,
            String cape,
            String hat,
            String pet,
            boolean equipped,
            long updatedAt
    ) {
    }

    private record PulseRemoteCosmetic(int[] ids, boolean equipped, long updatedAt) {
        int id(vorga.phazeclient.implement.cosmetics.bridge.CosmeticCategory category) {
            int index = switch (category) {
                case WINGS -> 0;
                case CAPE -> 1;
                case HAT -> 2;
                case BODYWEAR -> 3;
                case PET -> 4;
                default -> -1;
            };
            return index < 0 ? -1 : ids[index];
        }
    }

    private record GraffitiQuery(String serverKey, String dimension) { }

    public record PulseGraffiti(
            String serverKey,
            String dimension,
            BlockPos pos,
            Direction face,
            int graffitiId
    ) { }
}
