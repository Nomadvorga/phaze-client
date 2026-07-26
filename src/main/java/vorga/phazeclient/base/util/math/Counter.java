package vorga.phazeclient.base.util.math;

public class Counter {
    private long time = System.currentTimeMillis();

    public void resetCounter() {
        time = System.currentTimeMillis();
    }

    public boolean isReached(long ms) {
        return getTime() >= ms;
    }

    public long getTime() {
        return System.currentTimeMillis() - time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
