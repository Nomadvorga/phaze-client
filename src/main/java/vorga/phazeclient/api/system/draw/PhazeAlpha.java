package vorga.phazeclient.api.system.draw;

import net.minecraft.util.math.ColorHelper;

public final class PhazeAlpha {

    private static final int MAX_DEPTH = 32;
    private static final float[] STACK = new float[MAX_DEPTH];
    private static int depth;

    static {
        STACK[0] = 1.0F;
    }

    private PhazeAlpha() {
    }

    public static float get() {
        return STACK[depth];
    }

    public static void push(float factor) {
        if (depth + 1 >= MAX_DEPTH) {
            return;
        }
        STACK[depth + 1] = STACK[depth] * factor;
        depth++;
    }

    public static void pop() {
        if (depth > 0) {
            depth--;
        }
    }

    public static void reset() {
        depth = 0;
        STACK[0] = 1.0F;
    }

    public static int tint(int argb) {
        float a = STACK[depth];
        return a >= 1.0F ? argb : ColorHelper.scaleAlpha(argb, a);
    }
}
