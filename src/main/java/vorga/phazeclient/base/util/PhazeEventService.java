package vorga.phazeclient.base.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds a long-lived connection to {@code GET /api/events} and reacts
 * to what the server pushes down it.
 *
 * <h3>Why a stream instead of polling harder</h3>
 * {@link RemoteRulesService} refreshes rules every ten minutes. That
 * is fine for a rule edit but useless for a kick, which is only worth
 * anything if it lands immediately. Shortening the poll interval would
 * multiply request volume for everyone to serve an event that fires
 * once a week; a stream costs one idle socket and a keepalive comment
 * every twenty-five seconds.
 *
 * <p>The protocol is Server-Sent Events - a response that never ends,
 * with {@code event:} / {@code data:} line pairs separated by blank
 * lines. That is a {@code readLine()} loop here rather than the frame
 * parser a WebSocket would need, and nothing is ever sent upstream.
 *
 * <h3>Events</h3>
 * <ul>
 *   <li>{@code rules} - the rule set changed; trigger a refresh so
 *       the new set applies in seconds rather than on the next tick
 *       of the ten-minute timer.</li>
 *   <li>{@code kick} - disconnect from the current server and show
 *       the operator's message.</li>
 *   <li>{@code hello} / {@code ping} - connection bookkeeping.</li>
 * </ul>
 *
 * <h3>Reconnection</h3>
 * The stream will drop: proxies recycle idle connections, laptops
 * sleep, mobile hotspots move between towers. Reconnects back off from
 * five seconds to five minutes so a server that is down does not get
 * hammered by every client at once, and the delay is jittered so they
 * do not all return in the same instant.
 */
public final class PhazeEventService {

    private static final Logger LOG = LoggerFactory.getLogger("PhazeEvents");

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    /**
     * Must exceed the server's keepalive interval (25s) or an idle but
     * healthy stream would be torn down and rebuilt every read timeout.
     */
    private static final int READ_TIMEOUT_MS = 40_000;

    private static final long RECONNECT_MIN_MS = 5_000L;
    private static final long RECONNECT_MAX_MS = 5 * 60_000L;

    private static final class Holder {
        static final PhazeEventService INSTANCE = new PhazeEventService();
    }

    public static PhazeEventService getInstance() {
        return Holder.INSTANCE;
    }

    private final ExecutorService scheduler =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "phaze-events");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean connected = false;
    private volatile long reconnectDelayMs = RECONNECT_MIN_MS;

    private PhazeEventService() {
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Idempotent. Started from {@link RemoteRulesService#start()} so
     * the two share a lifecycle and neither runs when the rules API is
     * disabled via {@code -Dphaze.rules.api=}.
     */
    public void start() {
        RemoteRulesService rules = RemoteRulesService.getInstance();
        if (rules.getApiBase() == null || rules.getApiBase().isEmpty()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.execute(this::runForever);
    }

    private void runForever() {
        while (true) {
            try {
                listen();
                // A clean end of stream is still a disconnect - the
                // server closed, so back off like any other drop.
            } catch (Throwable t) {
                LOG.debug("event stream ended: {}", t.toString());
            }
            connected = false;
            long delay = reconnectDelayMs;
            // Jitter so a server restart does not bring every client
            // back in the same second.
            long jitter = (long) (delay * 0.25 * Math.random());
            try {
                Thread.sleep(delay + jitter);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            reconnectDelayMs = Math.min(RECONNECT_MAX_MS, delay * 2);
        }
    }

    private void listen() throws Exception {
        RemoteRulesService rules = RemoteRulesService.getInstance();
        String url = rules.getApiBase()
                + "/api/events?clientId="
                + URLEncoder.encode(rules.getClientId(), StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36");
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != 200) {
                LOG.debug("event stream returned {}", status);
                return;
            }

            connected = true;
            // Only reset the backoff once a connection actually
            // succeeded, otherwise a server that accepts and instantly
            // closes would be retried every five seconds forever.
            reconnectDelayMs = RECONNECT_MIN_MS;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String event = null;
                String data = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (event != null) {
                            dispatch(event, data);
                        }
                        event = null;
                        data = null;
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue; // comment / keepalive
                    }
                    if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        String chunk = line.substring(5).trim();
                        data = data == null ? chunk : data + chunk;
                    }
                }
            }
        } finally {
            connected = false;
            conn.disconnect();
        }
    }

    private void dispatch(String event, String data) {
        try {
            switch (event) {
                case "rules" -> {
                    LOG.info("rules changed remotely, refreshing");
                    RemoteRulesService.getInstance().requestRefresh();
                }
                case "kick" -> handleKick(data);
                case "announcement" -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject() && parsed.getAsJsonObject().has("announcement")) {
                        JsonElement payload = parsed.getAsJsonObject().get("announcement");
                        if (payload.isJsonObject()) {
                            PhazeAnnouncements.accept(payload.getAsJsonObject());
                        }
                    }
                }
                case "announcement_expired" -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject() && parsed.getAsJsonObject().has("id")) {
                        PhazeAnnouncements.dismiss(parsed.getAsJsonObject().get("id").getAsInt());
                    }
                }
                case "cosmetic" -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject()) {
                        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                                .getInstance()
                                .acceptEvent(parsed.getAsJsonObject());
                    }
                }
                case "pulse_cosmetic" -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject()) {
                        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                                .getInstance()
                                .acceptPulseEvent(parsed.getAsJsonObject());
                    }
                }
                case "pulse_graffiti" -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject()) {
                        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                                .getInstance()
                                .acceptPulseGraffitiEvent(parsed.getAsJsonObject());
                    }
                }
                case "hello" -> vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                        .getInstance()
                        .requestRefresh();
                case "ping" -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject()) {
                        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                                .getInstance()
                                .acceptPushCheckpoint(parsed.getAsJsonObject());
                    }
                }
                default -> LOG.debug("unknown event '{}'", event);
            }
        } catch (Throwable t) {
            LOG.warn("failed to handle event '{}': {}", event, t.toString());
        }
    }

    private void handleKick(String data) {
        String message = Lang.t("status.kicked_by_admin");
        try {
            JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("message") && !obj.get("message").isJsonNull()) {
                    String supplied = obj.get("message").getAsString().trim();
                    if (!supplied.isEmpty()) {
                        message = supplied;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the generic message - a malformed payload
            // must not turn a kick into a no-op.
        }

        LOG.info("kick received: {}", message);
        // One event, one disconnect. Nothing blocks the player from
        // reconnecting straight away - the kick is a visible "get off
        // this server now", not a timed ban, and a cooldown that keeps
        // ejecting someone after the fact is indistinguishable from a
        // bug when you are on the receiving end of it.
        disconnectNow(message);
    }

    /**
     * Drops the connection on the client thread. Networking objects
     * are not safe to touch from the poller thread, so the actual
     * disconnect is handed to Minecraft's own executor.
     */
    private void disconnectNow(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            try {
                if (client.isInSingleplayer()) {
                    return;
                }
                if (client.getNetworkHandler() == null) {
                    return;
                }
                client.getNetworkHandler().getConnection().disconnect(Text.literal(message));
            } catch (Throwable t) {
                LOG.warn("disconnect failed: {}", t.toString());
            }
        });
    }
}
