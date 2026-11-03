package vorga.phazeclient.api.system.hud;

/** Render-thread scope: tint only the progress pass, never the name or background. */
public final class BossBarColorContext {
    private static int tint = 0xFFFFFFFF;

    private BossBarColorContext() {
    }

    public static int setTint(int color) {
        int previous = tint;
        tint = color;
        return previous;
    }

    public static int applyTint(int color) {
        if (tint == 0xFFFFFFFF) return color;
        int a = (color >>> 24) * (tint >>> 24) / 255;
        int r = ((color >>> 16) & 255) * ((tint >>> 16) & 255) / 255;
        int g = ((color >>> 8) & 255) * ((tint >>> 8) & 255) / 255;
        int b = (color & 255) * (tint & 255) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }
}
