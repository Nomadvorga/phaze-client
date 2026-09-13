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

public final class PhazeEventService {

    private static final Logger LOG = LoggerFactory.getLogger("PhazeEvents");

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private static final int READ_TIMEOUT_MS = 40_000;

    private static final long RECONNECT_MIN_MS = 5_000L;
    private static final long RECONNECT_MAX_MS = 5 * 60_000L;

    private static final String LEGACY_COSMETIC_EVENT = "pu" + "lse_cosmetic";
    private static final String LEGACY_GRAFFITI_EVENT = "pu" + "lse_graffiti";

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

            } catch (Throwable t) {
                LOG.debug("event stream ended: {}", t.toString());
            }
            connected = false;
            long delay = reconnectDelayMs;

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
                        continue;
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
                case LEGACY_COSMETIC_EVENT -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject()) {
                        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                                .getInstance()
                                .acceptPhazeEvent(parsed.getAsJsonObject());
                    }
                }
                case LEGACY_GRAFFITI_EVENT -> {
                    JsonElement parsed = JsonParser.parseString(data == null ? "{}" : data);
                    if (parsed.isJsonObject()) {
                        vorga.phazeclient.implement.cosmetics.CosmeticsSyncService
                                .getInstance()
                                .acceptPhazeGraffitiEvent(parsed.getAsJsonObject());
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

        }

        LOG.info("kick received: {}", message);

        disconnectNow(message);
    }

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
