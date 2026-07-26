package vorga.phazeclient.base.util.render;

import org.joml.Vector4f;

public final class FogColorTracker {
    private static volatile float r = 0.70F;
    private static volatile float g = 0.81F;
    private static volatile float b = 0.99F;

    private FogColorTracker() {
    }

    public static void update(Vector4f color) {
        if (color == null) return;
        r = clamp01(color.x);
        g = clamp01(color.y);
        b = clamp01(color.z);
    }

    public static float red() {
        return r;
    }

    public static float green() {
        return g;
    }

    public static float blue() {
        return b;
    }

    private static float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }
}
