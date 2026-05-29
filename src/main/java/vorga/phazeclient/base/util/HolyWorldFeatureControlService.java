package vorga.phazeclient.base.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.core.Main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

/**
 * HolyWorld sends feature-control updates over the liteapi:feature-control
 * custom-payload channel. We keep a local fail-open cache of disabled
 * features so the client can react immediately without polling HTTP.
 */
public final class HolyWorldFeatureControlService {
    private static final String REQUEST_METHOD_NAME = "checkFeatures";
    private static final String HOLYWORLD_SEGMENT = "holyworld";
    private static final long REQUEST_COOLDOWN_MS = 10_000L;
    private static final String FIXED_CLIENT_ID = "phaze-client";

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Set<String> disabledFeatures = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

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
            client.execute(() -> {
                disabledFeatures.clear();
                requestServerRules("");
            });
        });
    }

    public boolean isFeatureDisabled(String featureName) {
        if (!isHolyWorldServer()) {
            return false;
        }
        return disabledFeatures.contains(normalize(featureName));
    }

    public Set<String> getDisabledFeatures() {
        return Collections.unmodifiableSet(disabledFeatures);
    }

    public void requestFeatureStatus(String featureName) {
        requestServerRules(featureName);
    }

    private void requestServerRules(String featureName) {
        if (!isHolyWorldServer()) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastRequestAt.get();
        if (now - previous < REQUEST_COOLDOWN_MS) {
            return;
        }
        if (!lastRequestAt.compareAndSet(previous, now)) {
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("method", REQUEST_METHOD_NAME);
        request.addProperty("id", UUID.randomUUID().toString());

        JsonObject payload = new JsonObject();
        payload.addProperty("client", FIXED_CLIENT_ID);
        payload.add("features", buildFeatureArray(featureName));
        request.add("payload", payload);

        ClientPlayNetworking.send(new HolyWorldPayload(request.toString()));
    }

    private void handlePayload(String json) {
        if (!isHolyWorldServer()) {
            disabledFeatures.clear();
            return;
        }

        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return;
            }

            JsonObject object = parsed.getAsJsonObject();
            if (!object.has("ok") || !object.get("ok").getAsBoolean()) {
                return;
            }
            JsonElement payloadElement = object.get("payload");
            if (payloadElement == null || !payloadElement.isJsonObject()) {
                return;
            }
            JsonElement blocklistElement = payloadElement.getAsJsonObject().get("blocklist");
            if (blocklistElement == null || !blocklistElement.isJsonArray()) {
                return;
            }
            Set<String> nextDisabled = ConcurrentHashMap.newKeySet();
            for (JsonElement entry : blocklistElement.getAsJsonArray()) {
                if (entry == null || entry.isJsonNull()) {
                    continue;
                }
                String normalized = normalize(entry.getAsString());
                if (!normalized.isEmpty()) {
                    nextDisabled.add(normalized);
                }
            }
            disabledFeatures.clear();
            disabledFeatures.addAll(nextDisabled);
        } catch (Throwable ignored) {
            // Fail-open: malformed payloads should never break the client.
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
                        (value, buf) -> PacketCodecs.STRING.encode(buf, value.json()),
                        buf -> new HolyWorldPayload(PacketCodecs.STRING.decode(buf))
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
