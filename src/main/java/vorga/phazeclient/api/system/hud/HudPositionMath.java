package vorga.phazeclient.api.system.hud;

/** Preserve the fraction of usable screen space, including right/bottom edge placement. */
public final class HudPositionMath {
    private HudPositionMath() {}

    public static float rescale(float position, int oldScreen, int newScreen, float extent) {
        if (oldScreen <= 0 || newScreen <= 0 || oldScreen == newScreen) return position;
        float size = Float.isFinite(extent) && extent > 0 ? extent : 0;
        float oldAvailable = oldScreen - size;
        if (oldAvailable <= 0) return position;
        return position * Math.max(0, newScreen - size) / oldAvailable;
    }
}
