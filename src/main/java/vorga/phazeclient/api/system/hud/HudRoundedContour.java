package vorga.phazeclient.api.system.hud;

/** Cached inner/outer contours, separated by one physical pixel along the edge normal. */
public final class HudRoundedContour {
    private static final HudRoundedContour[] CACHE = new HudRoundedContour[128];
    private static final int[] NEXT = new int[32];
    public final float width, height, radius, scaleX, scaleY;
    public final int capSegments;
    // Left cap, straight centre, right cap. Each boundary stores inner/outer top/bottom.
    private final float[] ix, ox, iy, oy;
    private int[] palette;
    private int firstColor, secondColor, colorDirection;

    private HudRoundedContour(float width, float height, float radius, float scaleX, float scaleY) {
        this.width = width; this.height = height; this.radius = radius;
        this.scaleX = scaleX; this.scaleY = scaleY;
        // Chord sagitta <= ~0.08 physical pixels (until the deliberately bounded cap).
        capSegments = Math.max(4, Math.min(64, (int) Math.ceil(2 * Math.sqrt(radius * Math.max(scaleX, scaleY)))));
        int count = capSegments * 2 + 2;
        ix = new float[count]; ox = new float[count]; iy = new float[count]; oy = new float[count];
        float minScale = Math.min(scaleX, scaleY), maxScale = Math.max(scaleX, scaleY);
        // Do not offset past the minimum curvature radius of a tiny scaled ellipse.
        float half = Math.min(0.5F, radius * minScale * minScale / maxScale * 0.5F);
        for (int i = 0; i < count; i++) {
            boolean left = i <= capSegments;
            int step = left ? i : i - capSegments - 1;
            double angle = step * (Math.PI * 0.5 / capSegments);
            float nx = left ? -(float) Math.cos(angle) : (float) Math.sin(angle);
            float ny = left ? -(float) Math.sin(angle) : -(float) Math.cos(angle);
            // Exact endpoints prevent cracks between caps and the straight centre/sides.
            if (step == 0) { nx = left ? -1 : 0; ny = left ? 0 : -1; }
            if (step == capSegments) { nx = left ? 0 : 1; ny = left ? -1 : 0; }
            float x = (left ? radius : width - radius) + nx * radius;
            float y = radius + ny * radius;
            float length = (float) Math.hypot(nx / scaleX, ny / scaleY);
            float dx = half * nx / (scaleX * scaleX * length);
            float dy = half * ny / (scaleY * scaleY * length);
            ix[i] = x - dx; ox[i] = x + dx; iy[i] = y - dy; oy[i] = y + dy;
        }
    }

    public static HudRoundedContour get(float width, float height, float radius, float sx, float sy) {
        sx = Math.max(0.01F, sx); sy = Math.max(0.01F, sy);
        radius = Math.min(radius, Math.min(width, height) * 0.5F);
        int hash = Float.floatToIntBits(width);
        hash = 31 * hash + Float.floatToIntBits(height);
        hash = 31 * hash + Float.floatToIntBits(radius);
        hash = 31 * hash + Float.floatToIntBits(sx);
        hash = 31 * hash + Float.floatToIntBits(sy);
        int set = (hash ^ (hash >>> 16)) & 31;
        for (int i = set * 4; i < set * 4 + 4; i++) {
            HudRoundedContour mesh = CACHE[i];
            if (mesh != null && mesh.width == width && mesh.height == height && mesh.radius == radius
                    && mesh.scaleX == sx && mesh.scaleY == sy) return mesh;
        }
        HudRoundedContour mesh = new HudRoundedContour(width, height, radius, sx, sy);
        CACHE[set * 4 + (NEXT[set]++ & 3)] = mesh;
        return mesh;
    }

    public int points() { return ix.length; }
    public int[] colors(int first, int second, int direction) {
        if (palette == null || firstColor != first || secondColor != second || colorDirection != direction) {
            if (palette == null) palette = new int[points() * 2];
            firstColor = first; secondColor = second; colorDirection = direction;
            for (int i = 0; i < points(); i++) {
                palette[i * 2] = HudBackgroundGeometryGradients.sample(first, second, direction, ix[i] / width, iy[i] / height, 0, false);
                palette[i * 2 + 1] = HudBackgroundGeometryGradients.sample(first, second, direction, ix[i] / width, 1 - iy[i] / height, 0, false);
            }
        }
        return palette;
    }
    public float innerX(int i) { return ix[i]; }
    public float outerX(int i) { return ox[i]; }
    public float innerY(int i) { return iy[i]; }
    public float outerY(int i) { return oy[i]; }
}
