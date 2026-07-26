package vorga.phazeclient.api.system.animation;

import lombok.Setter;
import lombok.experimental.Accessors;
import vorga.phazeclient.base.util.math.Counter;

import static vorga.phazeclient.api.system.animation.Direction.FORWARDS;

@Setter
@Accessors(chain = true)
public abstract class Animation implements AnimationCalculation {
    private final Counter counter = new Counter();
    protected int ms;
    protected double value;

    protected Direction direction = FORWARDS;

    public int getMs() {
        return ms;
    }

    public void reset() {
        counter.resetCounter();
    }

    public boolean isDone() {
        return counter.isReached(ms);
    }

    public boolean isFinished(Direction direction) {
        return this.direction == direction && isDone();
    }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            adjustTimer();
        }
    }

    public Animation setDirectionAndFinish(Direction direction) {
        this.direction = direction;
        counter.setTime(System.currentTimeMillis() - ms);
        return this;
    }

    public boolean isDirection(Direction direction) {
        return this.direction == direction;
    }

    private void adjustTimer() {
        counter.setTime(
                System.currentTimeMillis() - ((long) ms - Math.min(ms, counter.getTime()))
        );
    }

    public Double getOutput() {
        // Boxed-Double API kept for back-compat with non-hot call sites.
        // Hot paths (per-frame menu rendering) MUST use getOutputDouble
        // / getOutputFloat to avoid the Double allocation that this
        // method's return-type widening forces. {@code Double.valueOf}
        // does NOT use a small-value cache like {@code Integer.valueOf},
        // so every invocation here allocates a fresh wrapper - at 60+
        // animations sampled per frame across the menu (cards, chips,
        // settings) that's 1000+ Double allocations per second of GC
        // pressure visible as menu hitches on long sessions.
        return getOutputDouble();
    }

    /**
     * Primitive-double variant of {@link #getOutput} for hot paths.
     * Returns the same numeric value without allocating a Double
     * wrapper. Prefer this in any per-frame render path
     * (ModuleComponent, MenuStyle palette mix coefficients, etc.).
     */
    public double getOutputDouble() {
        double time = (1 - calculation(counter.getTime())) * value;
        return direction == FORWARDS ? endValue() : isDone() ? 0.0 : time;
    }

    /**
     * Primitive-float variant of {@link #getOutput} for hot paths
     * that ultimately need {@code float} (most GUI rendering).
     * Equivalent to {@code (float) getOutputDouble()}, but exposed as
     * a single intrinsic call so hot-path readers don't accidentally
     * regress to the boxed {@link #getOutput} via IDE autocomplete.
     */
    public float getOutputFloat() {
        return (float) getOutputDouble();
    }

    private double endValue() {
        return isDone() ? value : calculation(counter.getTime()) * value;
    }
}
