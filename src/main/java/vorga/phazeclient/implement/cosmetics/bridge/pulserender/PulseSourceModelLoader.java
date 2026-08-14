package vorga.phazeclient.implement.cosmetics.bridge.pulserender;

import com.google.gson.*;
import vorga.phazeclient.implement.cosmetics.bridge.CosmeticEntry;
import vorga.phazeclient.implement.cosmetics.bridge.CosmeticKind;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseSourceModel.AnimationData;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseSourceModel.BoneAnimationData;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseSourceModel.Channel;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.GeoModel;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.GeoModelParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PulseSourceModelLoader {
    private static final Map<Integer, PulseSourceModel> CACHE = new ConcurrentHashMap<>();

    static PulseSourceModel get(CosmeticEntry entry) {
        if (entry == null || entry.kind() != CosmeticKind.GEOMETRY || entry.modelResource() == null) return null;
        return CACHE.computeIfAbsent(entry.id(), ignored -> load(entry));
    }

    private static PulseSourceModel load(CosmeticEntry entry) {
        try (InputStream in = PulseSourceModelLoader.class.getClassLoader().getResourceAsStream(entry.modelResource())) {
            if (in == null) return null;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject modelObject = object(root, "model");
            if (modelObject == null) return null;

            GeoModel geo = GeoModelParser.parse(modelObject.toString());
            if (geo == null) return null;

            return new PulseSourceModel(
                    entry.id(),
                    string(root, "name", entry.name()),
                    integer(root, "pos", 1),
                    number(root, "scale", 1f),
                    number(root, "x", 0f),
                    number(root, "y", 0f),
                    number(root, "z", 0f),
                    number(root, "yaw", 0f),
                    number(root, "pitch", 0f),
                    number(root, "roll", 0f),
                    geo,
                    parseAnimations(root)
            );
        } catch (Exception e) {
            System.err.println("[PulseCosmetics] Pulse renderer failed to load " + entry.name() + ": " + e);
            return null;
        }
    }

    private static Map<String, AnimationData> parseAnimations(JsonObject root) {
        Map<String, AnimationData> result = new LinkedHashMap<>();
        JsonObject animationRoot = object(root, "animation");
        JsonObject animations = animationRoot == null ? null : object(animationRoot, "animations");
        if (animations == null) return result;

        for (Map.Entry<String, JsonElement> animEntry : animations.entrySet()) {
            if (!animEntry.getValue().isJsonObject()) continue;
            JsonObject animation = animEntry.getValue().getAsJsonObject();
            boolean loop = bool(animation, "loop", true);
            float length = number(animation, "animation_length", 1f);
            Map<String, BoneAnimationData> bones = new LinkedHashMap<>();

            JsonObject boneObject = object(animation, "bones");
            if (boneObject != null) {
                for (Map.Entry<String, JsonElement> boneEntry : boneObject.entrySet()) {
                    if (!boneEntry.getValue().isJsonObject()) continue;
                    JsonObject b = boneEntry.getValue().getAsJsonObject();
                    bones.put(boneEntry.getKey(), new BoneAnimationData(
                            parseChannel(b.get("rotation"), new float[]{0f, 0f, 0f}),
                            parseChannel(b.get("position"), new float[]{0f, 0f, 0f}),
                            parseChannel(b.get("scale"), new float[]{1f, 1f, 1f})
                    ));
                }
            }
            result.put(animEntry.getKey(), new AnimationData(animEntry.getKey(), loop, length, bones));
        }
        return result;
    }

    private static Channel parseChannel(JsonElement element, float[] fallback) {
        Channel channel = new Channel();
        if (element == null || element.isJsonNull()) return channel;

        if (element.isJsonArray() || element.isJsonPrimitive()) {
            channel.constant = vector(element, fallback);
            return channel;
        }
        if (!element.isJsonObject()) return channel;

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                float t = Float.parseFloat(entry.getKey());
                channel.keys.put(t, keyVector(entry.getValue(), fallback));
            } catch (NumberFormatException ignored) {
            }
        }
        return channel;
    }

    private static float[] keyVector(JsonElement value, float[] fallback) {
        if (value != null && value.isJsonObject()) {
            JsonObject o = value.getAsJsonObject();
            if (o.has("post")) return vector(o.get("post"), fallback);
            if (o.has("pre")) return vector(o.get("pre"), fallback);
            if (o.has("vector")) return vector(o.get("vector"), fallback);
        }
        return vector(value, fallback);
    }

    private static float[] vector(JsonElement element, float[] fallback) {
        if (element == null || element.isJsonNull()) return fallback.clone();
        if (element.isJsonPrimitive()) {
            float n;
            try { n = element.getAsFloat(); } catch (Exception e) { return fallback.clone(); }
            return new float[]{n, n, n};
        }
        if (!element.isJsonArray()) return fallback.clone();
        JsonArray a = element.getAsJsonArray();
        float[] result = fallback.clone();
        for (int i = 0; i < Math.min(3, a.size()); i++) {
            try { result[i] = a.get(i).getAsFloat(); } catch (Exception ignored) {}
        }
        return result;
    }

    private static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private static float number(JsonObject root, String key, float fallback) {
        try { return root.has(key) ? root.get(key).getAsFloat() : fallback; } catch (Exception e) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try { return root.has(key) ? root.get(key).getAsInt() : fallback; } catch (Exception e) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root.has(key) ? root.get(key).getAsBoolean() : fallback; } catch (Exception e) { return fallback; }
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root.has(key) ? root.get(key).getAsString() : fallback; } catch (Exception e) { return fallback; }
    }

    private PulseSourceModelLoader() {}
}
