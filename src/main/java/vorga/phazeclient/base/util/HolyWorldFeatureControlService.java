package vorga.phazeclient.base.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.implement.other.ModuleDetailComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HolyWorldFeatureControlService {
    private static final String REQUEST_METHOD_NAME = "checkFeatures";
    private static final String HOLYWORLD_SEGMENT = "holyworld";
    private static final long REQUEST_COOLDOWN_MS = 10_000L;
    private static final String FIXED_CLIENT_ID = "phaze-client";
    private static final long RETRY_INTERVAL_MS = 10_000L;
    private static final int MAX_RETRIES = 5;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Set<String> disabledFeatures = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

    private final AtomicBoolean receivedInitialResponse = new AtomicBoolean(false);
    private final AtomicReference<String> pendingRequestId = new AtomicReference<>(null);
    private final AtomicInteger retryCount = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "HolyWorld-FeatureControl");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> retryFuture = null;

    private HolyWorldFeatureControlService() {
    }

    private static final class Holder {
        private static final HolyWorldFeatureControlService INSTANCE = new HolyWorldFeatureControlService();
    }

    public static HolyWorldFeatureControlService getInstance() {
        return Holder.INSTANCE;
    }

    public void init() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        PayloadTypeRegistry.playC2S().register(HolyWorldPayload.ID, HolyWorldPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HolyWorldPayload.ID, HolyWorldPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HolyWorldPayload.ID, (payload, context) -> {
            String json = payload.json();
            context.client().execute(() -> handlePayload(json));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            resetCache();
            client.execute(() -> {
                if (isHolyWorldServer()) {
                    requestServerRules("");
                    startRetryTask();
                    enforceServerLocks();
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetCache();
        });
    }

    public synchronized void resetCache() {
        stopRetryTask();
        disabledFeatures.clear();
        receivedInitialResponse.set(false);
        pendingRequestId.set(null);
        retryCount.set(0);
    }

    public void onHostChange() {
        if (isHolyWorldServer()) {
            if (!receivedInitialResponse.get() && pendingRequestId.get() == null) {
                requestServerRules("");
                startRetryTask();
                enforceServerLocks();
            }
        } else {
            resetCache();
        }
    }

    private synchronized void startRetryTask() {
        stopRetryTask();
        retryCount.set(0);

        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestAt.get();
        long initialDelay;
        if (elapsed < REQUEST_COOLDOWN_MS) {
            initialDelay = (REQUEST_COOLDOWN_MS - elapsed) + 100L;
        } else {
            initialDelay = RETRY_INTERVAL_MS;
        }

        retryFuture = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isHolyWorldServer() || receivedInitialResponse.get()) {
                    stopRetryTask();
                    return;
                }
                if (retryCount.incrementAndGet() > MAX_RETRIES) {
                    stopRetryTask();
                    return;
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        if (isHolyWorldServer() && !receivedInitialResponse.get()) {
                            requestServerRules("");
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        }, initialDelay, RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopRetryTask() {
        if (retryFuture != null) {
            retryFuture.cancel(false);
            retryFuture = null;
        }
    }

    public boolean isFeatureDisabled(String featureName) {
        if (!isHolyWorldServer()) {
            return false;
        }
        if (!receivedInitialResponse.get()) {
            return true;
        }
        return disabledFeatures.contains(normalize(featureName));
    }

    public Set<String> getDisabledFeatures() {
        return Collections.unmodifiableSet(disabledFeatures);
    }

    public void requestFeatureStatus(String featureName) {
        requestServerRules(featureName);
    }

    private boolean requestServerRules(String featureName) {
        if (!isHolyWorldServer()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long previous = lastRequestAt.get();
        if (now - previous < REQUEST_COOLDOWN_MS) {
            return false;
        }
        if (!lastRequestAt.compareAndSet(previous, now)) {
            return false;
        }

        String requestId = UUID.randomUUID().toString();
        pendingRequestId.set(requestId);

        JsonObject request = new JsonObject();
        request.addProperty("method", REQUEST_METHOD_NAME);
        request.addProperty("id", requestId);

        JsonObject payload = new JsonObject();
        payload.addProperty("client", FIXED_CLIENT_ID);
        payload.add("features", buildFeatureArray(featureName));
        request.add("payload", payload);

        try {
            ClientPlayNetworking.send(new HolyWorldPayload(request.toString()));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void handlePayload(String json) {
        if (!isHolyWorldServer()) {
            resetCache();
            return;
        }

        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return;
            }

            JsonObject object = parsed.getAsJsonObject();
            boolean isRpcResponse = object.has("ok");
            boolean isPushUpdate = object.has("event") || object.has("type")
                    || object.has("action") || (!isRpcResponse && object.has("payload"));

            Set<String> nextDisabled = null;

            if (isRpcResponse) {
                if (!object.get("ok").getAsBoolean()) {
                    return;
                }
                String respId = getString(object, "id");
                String expectedId = pendingRequestId.get();
                if (expectedId == null || !expectedId.equals(respId)) {
                    return;
                }
                pendingRequestId.set(null);
                nextDisabled = parseBlocklist(object.get("payload"));
            } else if (isPushUpdate) {
                nextDisabled = parseBlocklist(object.get("payload"));
            }

            if (nextDisabled != null) {
                disabledFeatures.clear();
                disabledFeatures.addAll(nextDisabled);
                receivedInitialResponse.set(true);
                stopRetryTask();
                enforceServerLocks();
            }
        } catch (Throwable ignored) {
        }
    }

    private Set<String> parseBlocklist(JsonElement payloadElement) {
        if (payloadElement == null || payloadElement.isJsonNull()) {
            return null;
        }
        Set<String> nextDisabled = ConcurrentHashMap.newKeySet();
        JsonArray array = null;

        if (payloadElement.isJsonArray()) {
            array = payloadElement.getAsJsonArray();
        } else if (payloadElement.isJsonObject()) {
            JsonObject obj = payloadElement.getAsJsonObject();
            if (obj.has("blocklist") && obj.get("blocklist").isJsonArray()) {
                array = obj.getAsJsonArray("blocklist");
            } else if (obj.has("features") && obj.get("features").isJsonArray()) {
                array = obj.getAsJsonArray("features");
            } else if (obj.has("disabled") && obj.get("disabled").isJsonArray()) {
                array = obj.getAsJsonArray("disabled");
            }
        }

        if (array != null) {
            for (JsonElement entry : array) {
                if (entry != null && !entry.isJsonNull() && entry.isJsonPrimitive()) {
                    String normalized = normalize(entry.getAsString());
                    if (!normalized.isEmpty()) {
                        nextDisabled.add(normalized);
                    }
                }
            }
            return nextDisabled;
        }
        return null;
    }

    public static void enforceServerLocks() {
        Main main = Main.getInstance();
        if (main == null || main.getModuleProvider() == null) {
            return;
        }

        for (Module module : main.getModuleProvider().getModules()) {
            if (module != null && module.isServerLocked()) {
                if (module.state) {
                    module.setState(false);
                }
                if (module.isEnabled()) {
                    module.deactivate();
                }
            }
        }

        try {
            MenuScreen menuScreen = MenuScreen.INSTANCE;
            if (menuScreen != null && menuScreen.getModuleDetailComponent() != null) {
                ModuleDetailComponent detail = menuScreen.getModuleDetailComponent();
                if (detail.isOpen() && detail.getModule() != null && detail.getModule().isServerLocked()) {
                    menuScreen.closeModuleDetail();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private JsonArray buildFeatureArray(String featureName) {
        List<String> features = new ArrayList<>();
        Main main = Main.getInstance();
        if (main != null && main.getModuleProvider() != null) {
            for (Module module : main.getModuleProvider().getModules()) {
                if (module == null) {
                    continue;
                }
                String id = normalize(module.getIdentifier());
                if (!id.isEmpty() && !features.contains(id)) {
                    features.add(id);
                }
            }
        }

        String requestedFeature = normalize(featureName);
        if (!requestedFeature.isEmpty() && !features.contains(requestedFeature)) {
            features.add(requestedFeature);
        }

        JsonArray array = new JsonArray();
        for (String feature : features) {
            array.add(feature);
        }
        return array;
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isHolyWorldServer() {
        String host = ServerUtil.getCurrentServerHost();
        if (host.isEmpty()) {
            return false;
        }

        String[] parts = host.split("\\.");
        for (String part : parts) {
            if (HOLYWORLD_SEGMENT.equals(part)) {
                return true;
            }
        }
        return false;
    }

    public record HolyWorldPayload(String json) implements CustomPayload {
        public static final Id<HolyWorldPayload> ID =
                new Id<>(Identifier.of("liteapi", "feature-control"));
        public static final PacketCodec<RegistryByteBuf, HolyWorldPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeBytes(value.json().getBytes(StandardCharsets.UTF_8)),
                        buf -> {
                            byte[] bytes = new byte[buf.readableBytes()];
                            buf.readBytes(bytes);
                            return new HolyWorldPayload(new String(bytes, StandardCharsets.UTF_8));
                        }
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
