package vorga.phazeclient.implement.cosmetics.bridge;

import java.util.*;

final class ParsedCosmeticModel {
    final float textureWidth;
    final float textureHeight;
    final float globalScale;
    final Vec3 globalOffset;
    final Vec3 globalRotation;
    final LinkedHashMap<String, Bone> bones;
    final Map<String, Animation> animations;
    final List<String> roots;
    final Vec3 minBounds;
    final Vec3 maxBounds;

    ParsedCosmeticModel(float textureWidth, float textureHeight,
                        float globalScale, Vec3 globalOffset, Vec3 globalRotation,
                        LinkedHashMap<String, Bone> bones,
                        Map<String, Animation> animations) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.globalScale = globalScale;
        this.globalOffset = globalOffset;
        this.globalRotation = globalRotation;
        this.bones = bones;
        this.animations = animations;

        List<String> r = new ArrayList<>();
        for (Bone bone : bones.values()) {
            if (bone.parent == null || bone.parent.isBlank() || !bones.containsKey(bone.parent)) {
                r.add(bone.name);
            }
        }
        this.roots = List.copyOf(r);

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Bone bone : bones.values()) {
            for (Cube cube : bone.cubes) {
                float x0 = Math.min(cube.origin.x(), cube.origin.x() + cube.size.x()) - cube.inflate;
                float y0 = Math.min(cube.origin.y(), cube.origin.y() + cube.size.y()) - cube.inflate;
                float z0 = Math.min(cube.origin.z(), cube.origin.z() + cube.size.z()) - cube.inflate;
                float x1 = Math.max(cube.origin.x(), cube.origin.x() + cube.size.x()) + cube.inflate;
                float y1 = Math.max(cube.origin.y(), cube.origin.y() + cube.size.y()) + cube.inflate;
                float z1 = Math.max(cube.origin.z(), cube.origin.z() + cube.size.z()) + cube.inflate;
                minX = Math.min(minX, x0); minY = Math.min(minY, y0); minZ = Math.min(minZ, z0);
                maxX = Math.max(maxX, x1); maxY = Math.max(maxY, y1); maxZ = Math.max(maxZ, z1);
            }
        }
        if (!Float.isFinite(minX)) {
            minX = minY = minZ = maxX = maxY = maxZ = 0f;
        }
        this.minBounds = new Vec3(minX, minY, minZ);
        this.maxBounds = new Vec3(maxX, maxY, maxZ);
    }

    Vec3 firstRootPivot() {
        if (roots.isEmpty()) return Vec3.ZERO;
        Bone root = bones.get(roots.get(0));
        return root == null ? Vec3.ZERO : root.pivot;
    }

    record Vec3(float x, float y, float z) {
        static final Vec3 ZERO = new Vec3(0, 0, 0);
        static final Vec3 ONE = new Vec3(1, 1, 1);

        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        Vec3 mul(Vec3 other) {
            return new Vec3(x * other.x, y * other.y, z * other.z);
        }
    }

    static final class Bone {
        final String name;
        final String parent;
        final Vec3 pivot;
        final Vec3 rotation;
        final List<Cube> cubes;
        final BoneStyle style;

        Bone(String name, String parent, Vec3 pivot, Vec3 rotation, List<Cube> cubes, BoneStyle style) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.cubes = cubes;
            this.style = style;
        }
    }

    record BoneStyle(boolean visible, float scale, Vec3 offset, Vec3 rotation, int color) {
        static final BoneStyle DEFAULT = new BoneStyle(true, 1f, Vec3.ZERO, Vec3.ZERO, 0xFFFFFF);
    }

    record Face(float u, float v, float width, float height) {}

    static final class Cube {
        final Vec3 origin;
        final Vec3 size;
        final Vec3 pivot;
        final Vec3 rotation;
        final float inflate;
        final EnumMap<Side, Face> faces;

        Cube(Vec3 origin, Vec3 size, Vec3 pivot, Vec3 rotation, float inflate, EnumMap<Side, Face> faces) {
            this.origin = origin;
            this.size = size;
            this.pivot = pivot;
            this.rotation = rotation;
            this.inflate = inflate;
            this.faces = faces;
        }
    }

    enum Side {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
    }

    static final class Animation {
        final String name;
        final float length;
        final Map<String, AnimatedBone> bones;

        Animation(String name, float length, Map<String, AnimatedBone> bones) {
            this.name = name;
            this.length = Math.max(length, 0.001f);
            this.bones = bones;
        }
    }

    record AnimatedBone(Channel rotation, Channel position, Channel scale) {}

    static final class Channel {
        final NavigableMap<Float, Vec3> keys = new TreeMap<>();
        Vec3 constant;

        boolean isEmpty() {
            return constant == null && keys.isEmpty();
        }

        Vec3 sample(float t, Vec3 fallback) {
            if (constant != null) return constant;
            if (keys.isEmpty()) return fallback;
            if (keys.size() == 1) return keys.firstEntry().getValue();

            Map.Entry<Float, Vec3> first = keys.firstEntry();
            Map.Entry<Float, Vec3> last = keys.lastEntry();
            if (t <= first.getKey()) return first.getValue();
            if (t >= last.getKey()) return last.getValue();

            Map.Entry<Float, Vec3> a = keys.floorEntry(t);
            Map.Entry<Float, Vec3> b = keys.ceilingEntry(t);
            if (a == null) return first.getValue();
            if (b == null) return last.getValue();
            if (Objects.equals(a.getKey(), b.getKey())) return a.getValue();

            float q = (t - a.getKey()) / (b.getKey() - a.getKey());
            q = Math.max(0f, Math.min(1f, q));
            // Smoothstep gives much smoother cosmetics than stepping at 20 TPS.
            q = q * q * (3f - 2f * q);
            Vec3 av = a.getValue();
            Vec3 bv = b.getValue();
            return new Vec3(
                    av.x() + (bv.x() - av.x()) * q,
                    av.y() + (bv.y() - av.y()) * q,
                    av.z() + (bv.z() - av.z()) * q
            );
        }
    }

    String chooseAnimation(boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
        if (animations.isEmpty()) return null;

        if (gliding) {
            String s = findAnimation("elytra", "flying", "fly");
            if (s != null) return s;
        }
        if (swimming) {
            String s = findAnimation("swimming", "swim", "water");
            if (s != null) return s;
        }
        if (sneaking) {
            String s = findAnimation("sneak", "crouch");
            if (s != null) return s;
        }
        if (moving) {
            String s = findAnimation("walking", "walk", "moving", "move", "run");
            if (s != null) return s;
        }
        String idle = findAnimation("idle", "afk");
        if (idle != null) return idle;
        return animations.keySet().iterator().next();
    }

    private String findAnimation(String... needles) {
        for (String name : animations.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (lower.contains(needle)) return name;
            }
        }
        return null;
    }
}
