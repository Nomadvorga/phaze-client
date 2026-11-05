package vorga.phazeclient.api.system.hud;

/** The existing ARGB color field, with a bounded-error cosine lookup instead of per-vertex trig. */
public final class HudBackgroundGeometryGradients {
    public static final int LEFT = 0, RIGHT = 1, DOWN = 2, UP = 3, DIAGONAL_DOWN = 4, DIAGONAL_UP = 5, PULSE = 6;
    private static final int WAVE_SIZE = 4096;
    private static final float[] WAVE = new float[WAVE_SIZE + 1];

    static {
        for (int i = 0; i <= WAVE_SIZE; i++) {
            WAVE[i] = 0.5F - 0.5F * (float) Math.cos(i / (double) WAVE_SIZE * Math.PI * 2.0D);
        }
    }

    private HudBackgroundGeometryGradients() {}

    public static int direction(String direction) {
        if (direction == null) return LEFT;
        return switch (direction) {
            case "Right to Left" -> RIGHT;
            case "Top to Bottom" -> DOWN;
            case "Bottom to Top" -> UP;
            case "Diagonal Down" -> DIAGONAL_DOWN;
            case "Diagonal Up" -> DIAGONAL_UP;
            case "Pulse" -> PULSE;
            default -> LEFT;
        };
    }

    public static boolean horizontal(int direction) { return direction == LEFT || direction == RIGHT; }
    public static boolean diagonal(int direction) { return direction == DIAGONAL_DOWN || direction == DIAGONAL_UP; }

    public static int sample(int first, int second, int direction, float x, float y, float offset, boolean animated) {
        if (direction == PULSE) return animated ? moving(first, second, offset) : first;
        float coordinate;
        float phase;
        switch (direction) {
            case RIGHT -> { coordinate = 1.0F - x; phase = offset; }
            case DOWN -> { coordinate = y; phase = -offset; }
            case UP -> { coordinate = 1.0F - y; phase = offset; }
            case DIAGONAL_DOWN -> { coordinate = (x + y) * 0.5F; phase = -offset; }
            case DIAGONAL_UP -> { coordinate = (x + 1.0F - y) * 0.5F; phase = -offset; }
            default -> { coordinate = x; phase = -offset; }
        }
        return animated ? moving(first, second, coordinate + phase) : lerp(first, second, coordinate);
    }

    /** Interpolated table error is below 4e-7 in blend, or one rounding unit per ARGB channel. */
    public static float wave(float coordinate) {
        float wrapped = coordinate - (float) Math.floor(coordinate);
        float position = wrapped * WAVE_SIZE;
        int index = Math.min(WAVE_SIZE - 1, (int) position);
        return WAVE[index] + (WAVE[index + 1] - WAVE[index]) * (position - index);
    }

    public static int moving(int first, int second, float coordinate) { return lerp(first, second, wave(coordinate)); }

    public static int lerp(int first, int second, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        int a = Math.round((first >>> 24) + ((second >>> 24) - (first >>> 24)) * t);
        int r = channel(first, second, 16, t);
        int g = channel(first, second, 8, t);
        int b = channel(first, second, 0, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int first, int second, int shift, float t) {
        int from = (first >>> shift) & 255;
        return Math.round(from + (((second >>> shift) & 255) - from) * t);
    }
}
