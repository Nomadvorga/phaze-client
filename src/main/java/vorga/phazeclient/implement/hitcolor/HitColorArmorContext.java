package vorga.phazeclient.implement.hitcolor;

import net.minecraft.client.render.OverlayTexture;

/** Per-entity hurt overlay shared with equipment rendering on the render thread. */
public final class HitColorArmorContext {
    private static final ThreadLocal<Integer> OVERLAY = ThreadLocal.withInitial(() -> OverlayTexture.DEFAULT_UV);
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private HitColorArmorContext() {
    }

    public static void begin(int overlay) {
        OVERLAY.set(overlay);
        ACTIVE.set(true);
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static int getOverlay() {
        return OVERLAY.get();
    }

    public static void end() {
        ACTIVE.set(false);
        OVERLAY.set(OverlayTexture.DEFAULT_UV);
    }
}
