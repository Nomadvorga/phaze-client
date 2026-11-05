package vorga.phazeclient.api.system.hud;

/**
 * Render-thread-only, bounded geometry cache. Keys retain exact float dimensions,
 * radius, physical-pixel tessellation count and AA width: moving a HUD or advancing
 * its color animation never rebuilds its caps. No scale/radius quantization.
 */
public final class HudBackgroundGeometry {
    private static final int SETS = 32, WAYS = 4;
    private static final HudBackgroundGeometry[] CACHE = new HudBackgroundGeometry[SETS * WAYS];
    private static final Caps[] CAP_CACHE = new Caps[SETS * WAYS];
    private static final int[] NEXT = new int[SETS], CAP_NEXT = new int[SETS];

    public final float width, height, radius, antialias;
    public final int strips, centerStart, centerEnd;
    private final float[] x0, x1, xProgress, inset, solidTop, solidBottom;
    private final boolean[] cap;
    private Colors colors;

    private HudBackgroundGeometry(float width, float height, float radius, int strips, float antialias) {
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.strips = strips;
        this.antialias = antialias;
        x0 = new float[strips]; x1 = new float[strips]; xProgress = new float[strips];
        inset = new float[strips]; solidTop = new float[strips]; solidBottom = new float[strips];
        cap = new boolean[strips];
        int start = strips, end = strips;
        for (int i = 0; i < strips; i++) {
            float p0 = i / (float) strips, p1 = (i + 1.0F) / strips;
            float progress = (p0 + p1) * 0.5F;
            x0[i] = p0 * width; x1[i] = p1 * width; xProgress[i] = progress;
            float distance = Math.min(progress * width, (1.0F - progress) * width);
            cap[i] = distance < radius;
            if (cap[i]) inset[i] = capInset(radius, distance);
            else { if (start == strips) start = i; end = i + 1; }
            solidTop[i] = Math.min(height - inset[i], inset[i] + antialias * 0.5F);
            solidBottom[i] = Math.max(inset[i], height - inset[i] - antialias * 0.5F);
        }
        centerStart = start; centerEnd = end;
    }

    public static HudBackgroundGeometry rounded(float width, float height, float radius, float physicalScaleX, float physicalScaleY) {
        int strips = clamp((int) Math.ceil(width * physicalScaleX * 1.5F), 48, 512);
        float aa = Math.min(radius, 1.0F / physicalScaleY);
        int set = hash(width, height, radius, strips, aa) & (SETS - 1);
        int base = set * WAYS;
        for (int i = 0; i < WAYS; i++) {
            HudBackgroundGeometry entry = CACHE[base + i];
            if (entry != null && entry.width == width && entry.height == height && entry.radius == radius
                    && entry.strips == strips && entry.antialias == aa) return entry;
        }
        HudBackgroundGeometry entry = new HudBackgroundGeometry(width, height, radius, strips, aa);
        CACHE[base + (NEXT[set]++ & (WAYS - 1))] = entry;
        return entry;
    }

    public static Caps caps(float radius, float physicalScaleX) {
        int strips = clamp((int) Math.ceil(radius * physicalScaleX * 1.5F), 8, 128);
        int set = hash(radius, 0, 0, strips, 0) & (SETS - 1);
        int base = set * WAYS;
        for (int i = 0; i < WAYS; i++) {
            Caps entry = CAP_CACHE[base + i];
            if (entry != null && entry.radius == radius && entry.strips == strips) return entry;
        }
        Caps entry = new Caps(radius, strips);
        CAP_CACHE[base + (CAP_NEXT[set]++ & (WAYS - 1))] = entry;
        return entry;
    }

    public float x0(int strip) { return x0[strip]; }
    public float x1(int strip) { return x1[strip]; }
    public float xProgress(int strip) { return xProgress[strip]; }
    public float inset(int strip) { return inset[strip]; }
    public float solidTop(int strip) { return solidTop[strip]; }
    public float solidBottom(int strip) { return solidBottom[strip]; }
    public boolean isCap(int strip) { return cap[strip]; }

    /** One cap-only palette per geometry; hover transitions do not allocate full-width color arrays. */
    public Colors staticColors(int first, int second, int direction) {
        if (colors == null || colors.first != first || colors.second != second || colors.direction != direction) {
            colors = new Colors(this, first, second, direction);
        }
        return colors;
    }

    /** Number of cosine segments needed for <0.31 continuous ARGB-channel error over the center. */
    public int horizontalCenterSegments() {
        if (centerStart == centerEnd) return 0;
        float span = (x1[centerEnd - 1] - x0[centerStart]) / width;
        return Math.max(1, (int) Math.ceil(span * 64.0F));
    }

    private static float capInset(float radius, float distance) {
        float dx = radius - distance;
        return radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - dx * dx));
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static int hash(float width, float height, float radius, int strips, float aa) {
        int hash = Float.floatToIntBits(width);
        hash = 31 * hash + Float.floatToIntBits(height);
        hash = 31 * hash + Float.floatToIntBits(radius);
        hash = 31 * hash + strips;
        hash = 31 * hash + Float.floatToIntBits(aa);
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        return hash ^ (hash >>> 15);
    }

    public static final class Caps {
        public final float radius;
        public final int strips;
        private final float[] x0, x1, width, inset;

        private Caps(float radius, int strips) {
            this.radius = radius; this.strips = strips;
            x0 = new float[strips]; x1 = new float[strips]; width = new float[strips]; inset = new float[strips];
            for (int i = 0; i < strips; i++) {
                float p0 = i / (float) strips, p1 = (i + 1.0F) / strips;
                x0[i] = p0 * radius; x1[i] = p1 * radius; width[i] = (p1 - p0) * radius;
                inset[i] = capInset(radius, (p0 + p1) * 0.5F * radius);
            }
        }

        public float x0(int strip) { return x0[strip]; }
        public float x1(int strip) { return x1[strip]; }
        public float width(int strip) { return width[strip]; }
        public float inset(int strip) { return inset[strip]; }
    }

    public static final class Colors {
        private final int first, second, direction;
        private final int centerStart, centerEnd;
        private final int[] top, bottom, exactTop, exactBottom;

        private Colors(HudBackgroundGeometry geometry, int first, int second, int direction) {
            this.first = first; this.second = second; this.direction = direction;
            centerStart = geometry.centerStart; centerEnd = geometry.centerEnd;
            int count = geometry.strips;
            int capCount = count - (centerEnd - centerStart);
            top = new int[capCount]; bottom = new int[capCount]; exactTop = new int[capCount]; exactBottom = new int[capCount];
            for (int i = 0; i < count; i++) {
                if (!geometry.isCap(i)) continue;
                int index = capIndex(i);
                float x = geometry.xProgress(i);
                top[index] = HudBackgroundGeometryGradients.sample(first, second, direction, x, geometry.solidTop(i) / geometry.height, 0, false);
                bottom[index] = HudBackgroundGeometryGradients.sample(first, second, direction, x, geometry.solidBottom(i) / geometry.height, 0, false);
                exactTop[index] = HudBackgroundGeometryGradients.sample(first, second, direction, x, geometry.inset(i) / geometry.height, 0, false);
                exactBottom[index] = HudBackgroundGeometryGradients.sample(first, second, direction, x, 1.0F - geometry.inset(i) / geometry.height, 0, false);
            }
        }

        private int capIndex(int strip) {
            if (strip < centerStart) return strip;
            if (strip >= centerEnd) return strip - (centerEnd - centerStart);
            throw new IllegalArgumentException("Straight-center colors are interpolated, not cached");
        }

        public int top(int strip) { return top[capIndex(strip)]; }
        public int bottom(int strip) { return bottom[capIndex(strip)]; }
        public int exactTop(int strip) { return exactTop[capIndex(strip)]; }
        public int exactBottom(int strip) { return exactBottom[capIndex(strip)]; }
    }
}
