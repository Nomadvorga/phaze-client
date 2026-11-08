package vorga.phazeclient.implement.menu.components.implement.settings;

/** GL-free, per-component label geometry and one-shot marquee. Times are monotonic nanoseconds. */
public final class SettingLabelState {
    public static final int WIDTH_TRANSITION_MS = 200;
    public static final long WIDTH_TRANSITION_NANOS = WIDTH_TRANSITION_MS * 1_000_000L;
    public static final long TRAVEL_NANOS = 900_000_000L;
    public static final long HOLD_NANOS = 650_000_000L;
    public static final long RETURN_NANOS = 900_000_000L;
    public static final float FADE_WIDTH = 10.0F;
    /** Hidden text that already gives a full-strength edge fade, so a few clipped pixels still read as faded. */
    public static final float FULL_FADE_DISTANCE = 2.0F;

    private String text = "";
    private float width, height, overflow;
    private float widthFrom, widthTarget;
    private long widthStarted;
    private boolean widthInitialized;
    private long started;
    private boolean running;
    private boolean returning;
    private boolean hovered;
    private float startFraction;

    public void layout(String text, float textWidth, float width, float height) {
        layout(text, textWidth, width, height, System.nanoTime());
    }

    public void layout(String text, float textWidth, float width, float height, long now) {
        layout(text, textWidth, width, height, now, 0.0F);
    }

    void layout(String text, float textWidth, float width, float height, long now, float animatedInset) {
        // The reset icon already animates the left inset. Smooth only the remaining
        // geometry so we don't animate that inset twice or lag behind the icon.
        float target = Math.max(0.0F, width + animatedInset);
        if (!widthInitialized) {
            // A newly opened page starts at its real geometry, without an appear animation.
            widthFrom = widthTarget = target;
            widthStarted = now;
            widthInitialized = true;
        }
        float currentWidth = sampleWidth(now);
        if (Float.compare(target, widthTarget) != 0) {
            // Retarget from the visible width, even when the user changes direction
            // during a drag. Repeated frames at the same target never restart it.
            widthFrom = currentWidth;
            widthTarget = target;
            widthStarted = now;
        }
        // A new translation returns to idle without fabricating another hover
        // entry. Width changes preserve the phase, including reset-icon motion.
        if (!this.text.equals(text)) {
            running = false;
            returning = false;
        }
        this.text = text;
        this.width = Math.max(0.0F, currentWidth - animatedInset);
        this.height = Math.max(0.0F, height);
        this.overflow = Math.max(0.0F, textWidth - this.width);
        if (!isOverflowing()) {
            running = false;
            returning = false;
        }
    }

    private float sampleWidth(long now) {
        float t = clamp((float) Math.max(0L, now - widthStarted) / WIDTH_TRANSITION_NANOS);
        float progress = 1.0F - (1.0F - t) * (1.0F - t);
        return widthFrom + (widthTarget - widthFrom) * progress;
    }

    public float getWidth() {
        return width;
    }

    public boolean isOverflowing() {
        return overflow > 0.0F && width > 0.0F && height > 0.0F;
    }

    public boolean isHovered() {
        return hovered;
    }

    /**
     * Starts one cycle on a card-entry edge, never on subsequent hovered frames.
     * Leaving scrolls back to the start from the current position. A fresh entry
     * during motion starts a new cycle from its current position, so neither
     * leaving nor re-entry jumps. Fitting/changed labels require a new entry
     * before they can animate.
     */
    public void updateHover(boolean hovered, long now) {
        float currentOffset = offset(now);
        boolean entered = hovered && !this.hovered;
        boolean left = !hovered && this.hovered;
        this.hovered = hovered;
        if (entered && isOverflowing()) {
            startFraction = currentOffset / overflow;
            started = now;
            running = true;
            returning = false;
        } else if (left && running) {
            // Reuses startFraction as the fraction the return starts from.
            startFraction = currentOffset / overflow;
            started = now;
            returning = true;
            running = startFraction > 0.0F;
        }
    }

    public float offset(long now) {
        if (!running || !isOverflowing()) return 0.0F;
        long elapsed = Math.max(0L, now - started);
        if (returning) {
            // Shorter remaining distance returns proportionally faster.
            long duration = (long) (RETURN_NANOS * startFraction);
            if (elapsed < duration) {
                return overflow * startFraction * (1.0F - ease((float) elapsed / duration));
            }
            running = false;
            returning = false;
            return 0.0F;
        }
        if (elapsed < TRAVEL_NANOS) {
            return overflow * (startFraction + (1.0F - startFraction) * ease((float) elapsed / TRAVEL_NANOS));
        }
        elapsed -= TRAVEL_NANOS;
        if (elapsed < HOLD_NANOS) return overflow;
        elapsed -= HOLD_NANOS;
        if (elapsed < RETURN_NANOS) return overflow * (1.0F - ease((float) elapsed / RETURN_NANOS));
        running = false;
        return 0.0F;
    }

    public float fadeWidth() {
        return Math.min(FADE_WIDTH, width * 0.5F);
    }

    /** Edge fade strength for the amount of text hidden past that edge. */
    public float edgeStrength(float hidden) {
        return clamp(hidden / FULL_FADE_DISTANCE);
    }

    /** Alpha multiplier at a viewport-local x. Only hidden text activates an edge fade. */
    public float alphaAt(float localX, float offset) {
        if (!isOverflowing()) return 1.0F;
        if (localX < 0.0F || localX > width) return 0.0F;
        float fade = fadeWidth();
        float left = edgeStrength(offset);
        float right = edgeStrength(overflow - offset);
        return clamp((1.0F - left * (1.0F - ease(localX / fade)))
                * (1.0F - right * (1.0F - ease((width - localX) / fade))));
    }

    public float getOverflow() {
        return overflow;
    }

    public static float snapClipEdge(float localX, float scale, float translation) {
        return scale > 0.0F ? (Math.round(localX * scale + translation) - translation) / scale : localX;
    }

    private static float ease(float value) {
        float t = clamp(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
