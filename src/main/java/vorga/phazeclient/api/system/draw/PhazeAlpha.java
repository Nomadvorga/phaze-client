package vorga.phazeclient.api.system.draw;

import net.minecraft.util.math.ColorHelper;

/**
 * Mod-owned global alpha multiplier.
 *
 * <h3>Why this exists</h3>
 *
 * 1.21.4 kept a global shader colour on {@code RenderSystem}, and Phaze
 * leaned on it for fade animations: a component multiplied it down, drew
 * its subtree, and restored it, while leaf draws read
 * {@code RenderSystem.getShaderColor()[3]} to bake the current fade into
 * their vertex colours.
 *
 * <p>1.21.11 has no colour accessor of any kind on {@code RenderSystem} -
 * there is nothing global left to read or write, because colour now
 * travels per-draw. So the multiplier becomes Phaze's own, with the same
 * push/pop shape the old code already used.
 *
 * <h3>Contract</h3>
 *
 * Render-thread only, and every {@link #push} must be matched by a
 * {@link #pop} in a {@code finally}. A missed pop does not fail to
 * compile - it renders subsequent elements at the wrong alpha, and fades
 * pop instead of fading. {@link #reset()} is wired at the HUD and Screen
 * render entry points so a leaked push cannot outlive a frame.
 */
public final class PhazeAlpha {

    /**
     * Depth 32 mirrors the nesting the menu actually reaches
     * (screen -> window -> category -> module -> setting -> group).
     * Overflow clamps rather than throwing: a dropped fade is a cosmetic
     * bug, a crash in the middle of GUI rendering is not.
     */
    private static final int MAX_DEPTH = 32;
    private static final float[] STACK = new float[MAX_DEPTH];
    private static int depth;

    static {
        STACK[0] = 1.0F;
    }

    private PhazeAlpha() {
    }

    /** Current cumulative alpha, 0..1. */
    public static float get() {
        return STACK[depth];
    }

    /** Multiplies the current alpha by {@code factor} for the nested scope. */
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

    /** Called at each frame's render entry so a leaked push cannot persist. */
    public static void reset() {
        depth = 0;
        STACK[0] = 1.0F;
    }

    /** Applies the current alpha to a packed ARGB colour. */
    public static int tint(int argb) {
        float a = STACK[depth];
        return a >= 1.0F ? argb : ColorHelper.scaleAlpha(argb, a);
    }
}
