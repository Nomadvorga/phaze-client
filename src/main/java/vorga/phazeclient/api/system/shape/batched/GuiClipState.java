package vorga.phazeclient.api.system.shape.batched;

/**
 * Mirror of the GL scissor state as set through {@link com.mojang.blaze3d.platform.GlStateManager}.
 * Batched GUI vertices copy the clip that is active when they are submitted, so scissor changes
 * no longer have to drain the batch.
 */
public final class GuiClipState {
    private static boolean enabled;
    private static int x, y, width, height;

    private GuiClipState() {
    }

    public static void onEnable() {
        enabled = true;
    }

    public static void onDisable() {
        enabled = false;
    }

    public static void onBox(int boxX, int boxY, int boxWidth, int boxHeight) {
        x = boxX;
        y = boxY;
        width = boxWidth;
        height = boxHeight;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int boxX() { return x; }
    public static int boxY() { return y; }
    public static int boxWidth() { return width; }
    public static int boxHeight() { return height; }

    /** Clip as {minX, minY, maxX, maxY} in framebuffer pixels, GL (y-up) orientation. */
    static short minX() { return enabled ? clamp(x) : Short.MIN_VALUE; }
    static short minY() { return enabled ? clamp(y) : Short.MIN_VALUE; }
    static short maxX() { return enabled ? clamp((long) x + Math.max(0, width)) : Short.MAX_VALUE; }
    static short maxY() { return enabled ? clamp((long) y + Math.max(0, height)) : Short.MAX_VALUE; }

    private static short clamp(long value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
