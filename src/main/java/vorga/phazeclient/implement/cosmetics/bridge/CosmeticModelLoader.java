package vorga.phazeclient.implement.cosmetics.bridge;

import com.google.gson.*;
import vorga.phazeclient.implement.cosmetics.bridge.ParsedCosmeticModel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CosmeticModelLoader {
    private static final Map<Integer, ParsedCosmeticModel> CACHE = new HashMap<>();

    public static ParsedCosmeticModel get(CosmeticEntry entry) {
        if (entry == null || entry.kind() != CosmeticKind.GEOMETRY || entry.modelResource() == null) return null;
        return CACHE.computeIfAbsent(entry.id(), id -> load(entry));
    }

    private static ParsedCosmeticModel load(CosmeticEntry entry) {
        try (InputStream in = CosmeticModelLoader.class.getClassLoader().getResourceAsStream(entry.modelResource())) {
            if (in == null) return null;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            float globalScale = f(root, "scale", 1f);
            Vec3 globalOffset = new Vec3(f(root, "x", 0f), f(root, "y", 0f), f(root, "z", 0f));
            Vec3 globalRotation = new Vec3(f(root, "pitch", 0f), f(root, "yaw", 0f), f(root, "roll", 0f));

            JsonObject modelObj = obj(root, "model");
            JsonArray geoms = modelObj == null ? null : arr(modelObj, "minecraft:geometry");
            if (geoms == null || geoms.size() == 0) return null;
            JsonObject geom = geoms.get(0).getAsJsonObject();

            JsonObject description = obj(geom, "description");
            float texW = description == null ? Math.max(1, entry.textureWidth()) : f(description, "texture_width", Math.max(1, entry.textureWidth()));
            float texH = description == null ? Math.max(1, entry.textureHeight()) : f(description, "texture_height", Math.max(1, entry.textureHeight()));

            Map<String, BoneStyle> styles = parseStyles(root);
            LinkedHashMap<String, Bone> bones = new LinkedHashMap<>();
            JsonArray boneArray = arr(geom, "bones");
            if (boneArray != null) {
                for (JsonElement element : boneArray) {
                    if (!element.isJsonObject()) continue;
                    JsonObject b = element.getAsJsonObject();
                    String name = s(b, "name", "bone_" + bones.size());
                    String parent = s(b, "parent", "");
                    Vec3 pivot = vec(b.get("pivot"), Vec3.ZERO);
                    Vec3 rotation = vec(b.get("rotation"), Vec3.ZERO);

                    List<Cube> cubes = new ArrayList<>();
                    JsonArray cubeArray = arr(b, "cubes");
                    if (cubeArray != null) {
                        for (JsonElement ce : cubeArray) {
                            if (!ce.isJsonObject()) continue;
                            JsonObject c = ce.getAsJsonObject();
                            Vec3 origin = vec(c.get("origin"), Vec3.ZERO);
                            Vec3 size = vec(c.get("size"), Vec3.ZERO);
                            Vec3 cpivot = vec(c.get("pivot"), pivot);
                            Vec3 crot = vec(c.get("rotation"), Vec3.ZERO);
                            float inflate = f(c, "inflate", 0f);
                            EnumMap<Side, Face> faces = parseFaces(c.get("uv"), size);
                            cubes.add(new Cube(origin, size, cpivot, crot, inflate, faces));
                        }
                    }

                    bones.put(name, new Bone(name, parent, pivot, rotation, cubes,
                            styles.getOrDefault(name, BoneStyle.DEFAULT)));
                }
            }

            Map<String, Animation> animations = parseAnimations(root);
            return new ParsedCosmeticModel(texW, texH, globalScale, globalOffset, globalRotation, bones, animations);
        } catch (Exception e) {
            System.err.println("[PulseCosmetics] Failed loading " + entry.name() + ": " + e);
            return null;
        }
    }

    private static Map<String, BoneStyle> parseStyles(JsonObject root) {
        Map<String, BoneStyle> out = new HashMap<>();
        JsonArray models = arr(root, "models");
        if (models == null) return out;

        for (JsonElement e : models) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String name = s(o, "name", "");
            if (name.isBlank()) continue;
            boolean visible = !o.has("visible") || o.get("visible").getAsBoolean();
            float scale = f(o, "scale", 1f);
            Vec3 offset = new Vec3(f(o, "x", 0f), f(o, "y", 0f), f(o, "z", 0f));
            Vec3 rotation = new Vec3(f(o, "pitch", 0f), f(o, "yaw", 0f), f(o, "roll", 0f));
            int color = o.has("color") ? o.get("color").getAsInt() : 0xFFFFFF;
            out.put(name, new BoneStyle(visible, scale, offset, rotation, color));
        }
        return out;
    }

    private static EnumMap<Side, Face> parseFaces(JsonElement uv, Vec3 size) {
        EnumMap<Side, Face> out = new EnumMap<>(Side.class);
        if (uv == null || uv.isJsonNull()) return out;

        if (uv.isJsonObject()) {
            JsonObject o = uv.getAsJsonObject();
            addFace(out, Side.NORTH, o.get("north"));
            addFace(out, Side.SOUTH, o.get("south"));
            addFace(out, Side.EAST, o.get("east"));
            addFace(out, Side.WEST, o.get("west"));
            addFace(out, Side.UP, o.get("up"));
            addFace(out, Side.DOWN, o.get("down"));
            return out;
        }

        // Legacy Bedrock box UV. This is rare in the supplied cosmetics.
        if (uv.isJsonArray() && uv.getAsJsonArray().size() >= 2) {
            JsonArray a = uv.getAsJsonArray();
            float u = a.get(0).getAsFloat();
            float v = a.get(1).getAsFloat();
            float sx = Math.abs(size.x());
            float sy = Math.abs(size.y());
            float sz = Math.abs(size.z());
            out.put(Side.NORTH, new Face(u + sz, v + sz, sx, sy));
            out.put(Side.SOUTH, new Face(u + sz + sx + sz, v + sz, sx, sy));
            out.put(Side.WEST, new Face(u, v + sz, sz, sy));
            out.put(Side.EAST, new Face(u + sz + sx, v + sz, sz, sy));
            out.put(Side.UP, new Face(u + sz, v, sx, sz));
            out.put(Side.DOWN, new Face(u + sz + sx, v, sx, sz));
        }
        return out;
    }

    private static void addFace(EnumMap<Side, Face> out, Side side, JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject o = element.getAsJsonObject();
        Vec3 uv = vec2(o.get("uv"));
        Vec3 sz = vec2(o.get("uv_size"));
        out.put(side, new Face(uv.x(), uv.y(), sz.x(), sz.y()));
    }

    private static Map<String, Animation> parseAnimations(JsonObject root) {
        Map<String, Animation> out = new LinkedHashMap<>();
        JsonObject animationRoot = obj(root, "animation");
        JsonObject animations = animationRoot == null ? null : obj(animationRoot, "animations");
        if (animations == null) return out;

        for (Map.Entry<String, JsonElement> ae : animations.entrySet()) {
            if (!ae.getValue().isJsonObject()) continue;
            JsonObject a = ae.getValue().getAsJsonObject();
            float length = f(a, "animation_length", 1f);
            Map<String, AnimatedBone> boneMap = new HashMap<>();
            JsonObject bones = obj(a, "bones");
            if (bones != null) {
                for (Map.Entry<String, JsonElement> be : bones.entrySet()) {
                    if (!be.getValue().isJsonObject()) continue;
                    JsonObject bo = be.getValue().getAsJsonObject();
                    boneMap.put(be.getKey(), new AnimatedBone(
                            channel(bo.get("rotation"), Vec3.ZERO),
                            channel(bo.get("position"), Vec3.ZERO),
                            channel(bo.get("scale"), Vec3.ONE)
                    ));
                }
            }
            out.put(ae.getKey(), new Animation(ae.getKey(), length, boneMap));
        }
        return out;
    }

    private static Channel channel(JsonElement element, Vec3 defaultScalarBase) {
        Channel ch = new Channel();
        if (element == null || element.isJsonNull()) return ch;

        if (element.isJsonPrimitive()) {
            float v = number(element, defaultScalarBase.x());
            ch.constant = new Vec3(v, v, v);
            return ch;
        }
        if (element.isJsonArray()) {
            ch.constant = vec(element, defaultScalarBase);
            return ch;
        }
        if (!element.isJsonObject()) return ch;

        JsonObject o = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            try {
                float t = Float.parseFloat(e.getKey());
                ch.keys.put(t, vecKey(e.getValue(), defaultScalarBase));
            } catch (NumberFormatException ignored) {
            }
        }
        return ch;
    }

    private static Vec3 vecKey(JsonElement e, Vec3 fallback) {
        if (e != null && e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("post")) return vec(o.get("post"), fallback);
            if (o.has("pre")) return vec(o.get("pre"), fallback);
        }
        return vec(e, fallback);
    }

    private static Vec3 vec(JsonElement e, Vec3 fallback) {
        if (e == null || e.isJsonNull()) return fallback;
        if (e.isJsonPrimitive()) {
            float n = number(e, fallback.x());
            return new Vec3(n, n, n);
        }
        if (!e.isJsonArray()) return fallback;
        JsonArray a = e.getAsJsonArray();
        float x = a.size() > 0 ? number(a.get(0), fallback.x()) : fallback.x();
        float y = a.size() > 1 ? number(a.get(1), fallback.y()) : fallback.y();
        float z = a.size() > 2 ? number(a.get(2), fallback.z()) : fallback.z();
        return new Vec3(x, y, z);
    }

    private static Vec3 vec2(JsonElement e) {
        if (e == null || !e.isJsonArray()) return Vec3.ZERO;
        JsonArray a = e.getAsJsonArray();
        return new Vec3(
                a.size() > 0 ? number(a.get(0), 0f) : 0f,
                a.size() > 1 ? number(a.get(1), 0f) : 0f,
                0f
        );
    }

    private static float number(JsonElement e, float fallback) {
        try {
            if (e == null || e.isJsonNull()) return fallback;
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsFloat();
                if (p.isString()) return Float.parseFloat(p.getAsString());
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : null;
    }

    private static float f(JsonObject o, String key, float fallback) {
        return o != null && o.has(key) ? number(o.get(key), fallback) : fallback;
    }

    private static String s(JsonObject o, String key, String fallback) {
        try {
            return o != null && o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private CosmeticModelLoader() {
    }
}
