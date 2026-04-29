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
        double time = (1 - calculation(counter.getTime())) * value;

        return direction == FORWARDS ? endValue() : isDone() ? 0.0 : time;
    }

    private double endValue() {
        return isDone() ? value : calculation(counter.getTime()) * value;
    }
}
