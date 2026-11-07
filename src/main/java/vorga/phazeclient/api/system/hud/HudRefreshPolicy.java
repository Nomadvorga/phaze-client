package vorga.phazeclient.api.system.hud;

/** Screen/mouse state is deliberately absent: editor input must not override either refresh clock. */
public final class HudRefreshPolicy {
    private HudRefreshPolicy() {}

    public static int batch(int configured, boolean liveBackground, boolean blur, int blurFps) {
        return Math.max(clamp(configured), liveBackground ? Math.max(120, blur ? clamp(blurFps) : 1) : 1);
    }

    public static int edited(int configured, boolean gradient, boolean blur, int blurFps) {
        return Math.max(clamp(configured), Math.max(gradient ? 120 : 1, blur ? clamp(blurFps) : 1));
    }

    private static int clamp(int fps) { return Math.max(1, Math.min(360, fps)); }
}
