package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.MathHelper;

/**
 * 1:1 Clean-room cloth physics engine based on WaveyCapes (tr7zw) stick simulation.
 * 16 point masses connected by rigid distance constraints with torso clipping avoidance and inertia.
 */
public final class CapePhysicsSimulation {
    public static final int PART_COUNT = 16;

    private final float[] x = new float[PART_COUNT];
    private final float[] y = new float[PART_COUNT];
    private final float[] z = new float[PART_COUNT];

    private final float[] prevX = new float[PART_COUNT];
    private final float[] prevY = new float[PART_COUNT];
    private final float[] prevZ = new float[PART_COUNT];

    private boolean initialized = false;
    private boolean sneaking = false;
    private float dirX, dirY, dirZ;

    public CapePhysicsSimulation() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < PART_COUNT; i++) {
            x[i] = -i;
            y[i] = -i;
            z[i] = 0;
            prevX[i] = x[i];
            prevY[i] = y[i];
            prevZ[i] = z[i];
        }
        initialized = false;
    }

    public void update(
            float forwardDelta,
            float verticalDelta,
            float strafeDelta,
            float gravityStrength,
            float heightMultiplier,
            float strafeMultiplier,
            boolean isSneaking,
            boolean isUnderwater
    ) {
        if (!initialized) {
            reset();
            initialized = true;
            return;
        }

        // Save previous positions for frame interpolation
        System.arraycopy(x, 0, prevX, 0, PART_COUNT);
        System.arraycopy(y, 0, prevY, 0, PART_COUNT);
        System.arraycopy(z, 0, prevZ, 0, PART_COUNT);

        // Player movement delta on anchor point 0
        double changeX = forwardDelta + (isSneaking && !sneaking ? 3.0 : 0.0);
        double changeY = (verticalDelta * heightMultiplier) + (isSneaking && !sneaking ? 1.0 : 0.0);
        double changeZ = -strafeDelta * strafeMultiplier;
        sneaking = isSneaking;

        x[0] += (float) changeX;
        y[0] += (float) changeY;
        z[0] += (float) changeZ;

        // Apply downward gravity (fixed 50ms timescale factor)
        float fall = (isUnderwater ? gravityStrength / 10.0f : gravityStrength) * 0.05f;
        for (int i = 1; i < PART_COUNT; i++) {
            y[i] -= fall;
        }

        // Keep the same short-rope constraints as the public WaveyCapes
        // simulation: prevent a segment from folding back through its
        // neighbour before the distance solver runs.
        preventHardBends();
        preventSelfClipping(32);

        // Prevent cape clipping into player body
        for (int i = 1; i < PART_COUNT; i++) {
            if (x[i] - x[0] > 0) {
                x[i] = x[0];
            }
            float fraction = (float) i / (float) PART_COUNT;
            float maxZ = fraction * fraction * 5.0f;
            float offset = z[0] - z[i];
            if (offset > maxZ) {
                z[i] = z[0] - maxZ;
            }
            if (offset < -maxZ) {
                z[i] = z[0] + maxZ;
            }
        }

        // Relaxation solver: 3 passes backwards
        for (int iter = 0; iter < 3; iter++) {
            for (int a = PART_COUNT - 2; a >= 0; a--) {
                direction(a, a + 1);
                separate(a, a + 1);
            }
        }

        preventSelfClipping(32);

        // Limit length: rigidly lock segment lengths to prevent stretching/springiness
        for (int a = 0; a < PART_COUNT - 1; a++) {
            direction(a, a + 1);
            x[a + 1] = x[a] - dirX;
            y[a + 1] = y[a] - dirY;
            z[a + 1] = z[a] - dirZ;
        }
    }

    private void preventHardBends() {
        final double maxBend = 20.0;
        for (int i = PART_COUNT - 2; i >= 1; i--) {
            double first = Math.atan2(y[i + 1] - y[i], x[i + 1] - x[i]);
            double second = Math.atan2(y[i - 1] - y[i], x[i - 1] - x[i]);
            double angle = Math.toDegrees(first - second);
            while (angle > 360.0) angle -= 360.0;
            while (angle < -360.0) angle += 360.0;
            double abs = Math.abs(angle);
            if (abs < 180.0 - maxBend) {
                replaceBend(i, angle, 180.0 - maxBend + 1.0);
            } else if (abs > 180.0 + maxBend) {
                replaceBend(i, angle, 180.0 + maxBend - 1.0);
            }
        }
    }

    private void preventSelfClipping(int maxRuns) {
        int runs = 0;
        boolean clipped;
        do {
            clipped = false;
            for (int a = 0; a < PART_COUNT; a++) {
                for (int b = a + 1; b < PART_COUNT; b++) {
                    float dx = x[a] - x[b];
                    float dy = y[a] - y[b];
                    float dz = z[a] - z[b];
                    if (dx * dx + dy * dy + dz * dz < 0.99f) {
                        clipped = true;
                        direction(a, b);
                        separate(a, b);
                        if (++runs >= maxRuns) return;
                    }
                }
            }
        } while (clipped);
    }

    private void replaceBend(int i, double angle, double target) {
        double theta = Math.toRadians(target) * (angle < 0.0 ? -1.0 : 1.0);
        float ox = x[i - 1] - x[i];
        float oy = y[i - 1] - y[i];
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);
        x[i + 1] = cos * ox - sin * oy + x[i];
        y[i + 1] = sin * ox + cos * oy + y[i];
    }

    private void direction(int a, int b) {
        dirX = x[a] - x[b];
        dirY = y[a] - y[b];
        dirZ = z[a] - z[b];
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len < 1.0E-4F) {
            dirX = dirY = dirZ = 0;
        } else {
            dirX /= len;
            dirY /= len;
            dirZ /= len;
        }
    }

    private void separate(int a, int b) {
        float cx = (x[a] + x[b]) / 2.0f;
        float cy = (y[a] + y[b]) / 2.0f;
        float cz = (z[a] + z[b]) / 2.0f;
        if (a != 0) {
            x[a] = cx + dirX / 2.0f;
            y[a] = cy + dirY / 2.0f;
            z[a] = cz + dirZ / 2.0f;
        }
        x[b] = cx - dirX / 2.0f;
        y[b] = cy - dirY / 2.0f;
        z[b] = cz - dirZ / 2.0f;
    }

    public float getLerpX(int part, float delta) {
        if (part < 0 || part >= PART_COUNT) return 0;
        return MathHelper.lerp(delta, prevX[part], x[part]);
    }

    public float getLerpY(int part, float delta) {
        if (part < 0 || part >= PART_COUNT) return 0;
        return MathHelper.lerp(delta, prevY[part], y[part]);
    }

    public float getLerpZ(int part, float delta) {
        if (part < 0 || part >= PART_COUNT) return 0;
        return MathHelper.lerp(delta, prevZ[part], z[part]);
    }

    public float getSegmentRotation(int part, float delta) {
        if (part >= PART_COUNT - 1) {
            return getSegmentRotation(PART_COUNT - 2, delta);
        }
        float dx = getLerpX(part + 1, delta) - getLerpX(part, delta);
        float dy = getLerpY(part + 1, delta) - getLerpY(part, delta);
        return (float) Math.toDegrees(Math.atan2(dx, dy)) + 180.0f;
    }
}
