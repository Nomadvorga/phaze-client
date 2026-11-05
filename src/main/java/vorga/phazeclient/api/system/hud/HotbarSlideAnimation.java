package vorga.phazeclient.api.system.hud;

/** Display-frame clock shared by direct and cached hotbars; no work on settled positions. */
public final class HotbarSlideAnimation {
    private float currentX;
    private int lastSelected = -1;
    private long lastFrameNanos;
    private float lastSpeed = Float.NaN;
    private float smoothness;
    private int mirrorOffset;

    public void resetTracking() {
        lastSelected = -1;
        lastFrameNanos = 0;
        mirrorOffset = 0;
    }

    public void advance(int selected, boolean rollover, float speed, long now,
                        java.util.function.DoubleUnaryOperator smoothnessForSpeed) {
        mirrorOffset = 0;
        float target = selected * 20;
        if (rollover && lastSelected >= 0) {
            int delta = selected - lastSelected;
            if (delta >= 5) currentX += 180;
            else if (delta <= -5) currentX -= 180;
        }
        lastSelected = selected;
        float dt = lastFrameNanos == 0 ? 1.0F / 60 : Math.min(0.25F, (now - lastFrameNanos) / 1_000_000_000.0F);
        lastFrameNanos = now;
        if (currentX == target) return;
        if (speed != lastSpeed) {
            smoothness = (float) smoothnessForSpeed.applyAsDouble(speed);
            lastSpeed = speed;
        }
        currentX = (currentX - target) * (float) Math.pow(smoothness, dt) + target;
        if (Math.abs(currentX - target) < 0.4F) currentX = target;
        if (rollover) {
            if (currentX < 0) mirrorOffset = 180;
            else if (currentX > 160) mirrorOffset = -180;
        }
    }

    public float currentX() { return currentX; }
    public int mirrorOffset() { return mirrorOffset; }
}
