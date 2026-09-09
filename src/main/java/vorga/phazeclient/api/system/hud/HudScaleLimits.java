package vorga.phazeclient.api.system.hud;

import net.minecraft.util.math.MathHelper;

/** Shared, readable scale range for every draggable Phaze HUD. */
public final class HudScaleLimits {
    /** The value shown and persisted in settings, before the common render multiplier. */
    public static final float MIN = 0.50F;
    public static final float MAX = 3.00F;
    public static final float RENDER_MULTIPLIER = 2.00F;
    public static final float DEFAULT_SNAP_MIN = 0.92F;
    public static final float DEFAULT_SNAP_MAX = 1.08F;

    private HudScaleLimits() {}

    public static float clamp(float value) {
        return MathHelper.clamp(value, MIN, MAX);
    }

    public static boolean snapsToDefault(float value) {
        return value >= DEFAULT_SNAP_MIN && value <= DEFAULT_SNAP_MAX;
    }

    public static float normalize(float value) {
        float clamped = clamp(value);
        return snapsToDefault(clamped) ? 1.0F : clamped;
    }
}
