package vorga.phazeclient.implement.cosmetics.bridge.pulserender;

import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.GeoBone;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.GeoModel;

import java.util.*;

final class PulseSourceModel {
    final int id;
    final String name;
    final int position;
    final float scale;
    final float x;
    final float y;
    final float z;
    final float yaw;
    final float pitch;
    final float roll;
    final GeoModel model;
    final Map<String, AnimationData> animations;
    final Map<String, float[]> initialBoneTransforms = new HashMap<>();

    PulseSourceModel(int id,
                     String name,
                     int position,
                     float scale,
                     float x,
                     float y,
                     float z,
                     float yaw,
                     float pitch,
                     float roll,
                     GeoModel model,
                     Map<String, AnimationData> animations) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.scale = scale;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.model = model;
        this.animations = animations;
        for (GeoBone bone : model.topLevelBones) {
            saveBoneRecursive(bone);
        }
    }

    private void saveBoneRecursive(GeoBone bone) {
        initialBoneTransforms.put(bone.name, new float[]{
                bone.getRotationX(), bone.getRotationY(), bone.getRotationZ(),
                bone.getPositionX(), bone.getPositionY(), bone.getPositionZ(),
                bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()
        });
        for (GeoBone child : bone.childBones) saveBoneRecursive(child);
    }

    void resetBones() {
        for (GeoBone bone : model.topLevelBones) resetBoneRecursive(bone);
    }

    private void resetBoneRecursive(GeoBone bone) {
        float[] base = initialBoneTransforms.get(bone.name);
        if (base != null) {
            bone.setRotationX(base[0]);
            bone.setRotationY(base[1]);
            bone.setRotationZ(base[2]);
            bone.setPositionX(base[3]);
            bone.setPositionY(base[4]);
            bone.setPositionZ(base[5]);
            bone.setScaleX(base[6]);
            bone.setScaleY(base[7]);
            bone.setScaleZ(base[8]);
        }
        for (GeoBone child : bone.childBones) resetBoneRecursive(child);
    }

    GeoBone findBone(String name) {
        return model.getBone(name).orElse(null);
    }

    String chooseAnimation(boolean gliding, boolean swimming, boolean sneaking, boolean moving) {
        if (animations.isEmpty()) return null;

        // Pulse contains several naming generations. Prefer a state-specific
        // variant first, then the generic animation for that state.
        if (gliding) {
            String s = find("elytra", "flying", "fly");
            if (s != null) return s;
        }

        if (swimming) {
            if (moving) {
                String s = findAll("moving", "water");
                if (s != null) return s;
            }
            String s = find("swimming", "swim");
            if (s != null) return s;
            s = findAll("idle", "water");
            if (s != null) return s;
            s = findAll("main", "water");
            if (s != null) return s;
        }

        if (sneaking) {
            if (moving) {
                String s = findAll("moving", "sneak");
                if (s != null) return s;
            }
            String s = findAll("idle", "sneak");
            if (s != null) return s;
            s = findAll("main", "sneak");
            if (s != null) return s;
            s = find("sneak", "crouch");
            if (s != null) return s;
        }

        if (moving) {
            String s = find("walking", "walk", "moving", "move", "run");
            if (s != null && !lower(s).contains("water") && !lower(s).contains("sneak")) return s;
        }

        String idle = findExactOrContains("idle");
        if (idle != null && !lower(idle).contains("water") && !lower(idle).contains("sneak")) return idle;
        String main = findExactOrContains("main");
        if (main != null && !lower(main).contains("water") && !lower(main).contains("sneak")) return main;

        for (String name : animations.keySet()) {
            String l = lower(name);
            if (!l.contains("gui") && !l.contains("preview")) return name;
        }
        return animations.keySet().iterator().next();
    }

    private String find(String... needles) {
        for (String name : animations.keySet()) {
            String l = lower(name);
            for (String needle : needles) {
                if (l.contains(needle)) return name;
            }
        }
        return null;
    }

    private String findAll(String... needles) {
        for (String name : animations.keySet()) {
            String l = lower(name);
            boolean ok = true;
            for (String needle : needles) ok &= l.contains(needle);
            if (ok) return name;
        }
        return null;
    }

    private String findExactOrContains(String needle) {
        for (String name : animations.keySet()) {
            if (lower(name).equals(needle)) return name;
        }
        return find(needle);
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    static final class AnimationData {
        final String name;
        final boolean loop;
        final float length;
        final Map<String, BoneAnimationData> bones;

        AnimationData(String name, boolean loop, float length, Map<String, BoneAnimationData> bones) {
            this.name = name;
            this.loop = loop;
            this.length = Math.max(0.001f, length);
            this.bones = bones;
        }
    }

    static final class BoneAnimationData {
        final Channel rotation;
        final Channel position;
        final Channel scale;

        BoneAnimationData(Channel rotation, Channel position, Channel scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }
    }

    static final class Channel {
        final NavigableMap<Float, float[]> keys = new TreeMap<>();
        float[] constant;

        boolean isEmpty() {
            return constant == null && keys.isEmpty();
        }

        float[] sample(float time, float[] fallback) {
            if (constant != null) return constant;
            if (keys.isEmpty()) return fallback;
            if (keys.size() == 1) return keys.firstEntry().getValue();

            Map.Entry<Float, float[]> first = keys.firstEntry();
            Map.Entry<Float, float[]> last = keys.lastEntry();
            if (time <= first.getKey()) return first.getValue();
            if (time >= last.getKey()) return last.getValue();

            Map.Entry<Float, float[]> a = keys.floorEntry(time);
            Map.Entry<Float, float[]> b = keys.ceilingEntry(time);
            if (a == null) return first.getValue();
            if (b == null) return last.getValue();
            if (Objects.equals(a.getKey(), b.getKey())) return a.getValue();

            float q = (time - a.getKey()) / (b.getKey() - a.getKey());
            q = Math.max(0f, Math.min(1f, q));
            float[] av = a.getValue();
            float[] bv = b.getValue();
            return new float[]{
                    av[0] + q * (bv[0] - av[0]),
                    av[1] + q * (bv[1] - av[1]),
                    av[2] + q * (bv[2] - av[2])
            };
        }
    }
}
