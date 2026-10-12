package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.MathHelper;

/**
 * Clean-room Verlet integration cloth physics simulation for player capes,
 * based on the physics engine of WaveyCapes (tr7zw) and Sebastian Lague's rope physics.
 * 16 linked point masses with distance constraints, inertia, gravity, and travelling wind waves.
 */
public final class CapePhysicsSimulation {
    public static final int PART_COUNT = 16;
    public static final float SEGMENT_LENGTH = 1.0f / (float) PART_COUNT; // 0.0625 blocks per segment

    private final float[] x = new float[PART_COUNT];
    private final float[] y = new float[PART_COUNT];
    private final float[] z = new float[PART_COUNT];

    private final float[] prevX = new float[PART_COUNT];
    private final float[] prevY = new float[PART_COUNT];
    private final float[] prevZ = new float[PART_COUNT];

    private boolean initialized = false;
    private long lastSimulationTime = 0;

    public CapePhysicsSimulation() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < PART_COUNT; i++) {
            x[i] = 0;
            y[i] = -i * SEGMENT_LENGTH;
            z[i] = 0;
            prevX[i] = x[i];
            prevY[i] = prevZ[i] = prevX[i];
        }
        initialized = false;
    }

    /**
     * Advances the cloth physics tick.
     */
    public void update(
            float forwardVelocity,
            float verticalVelocity,
            float strafeVelocity,
            float gravityStrength,
            float waveSpeed,
            float waveStrength,
            boolean sneaking,
            boolean underwater
    ) {
        long now = System.currentTimeMillis();
        if (!initialized) {
            reset();
            initialized = true;
            lastSimulationTime = now;
            return;
        }

        float dt = MathHelper.clamp((now - lastSimulationTime) / 1000.0f, 0.001f, 0.05f);
        lastSimulationTime = now;

        // Save previous positions for frame-rate independent interpolation (lerp)
        for (int i = 0; i < PART_COUNT; i++) {
            prevX[i] = x[i];
            prevY[i] = y[i];
            prevZ[i] = z[i];
        }

        // Point 0 is anchored to player back
        x[0] = 0;
        y[0] = sneaking ? -0.1f : 0;
        z[0] = sneaking ? 0.05f : 0;

        // Apply external forces (movement inertia, gravity, wind)
        float gravity = 9.8f * gravityStrength;
        float windTime = (float) ((now / (underwater ? 9.0 : 3.0)) % 360.0);

        for (int i = 1; i < PART_COUNT; i++) {
            // Inertia from movement (cape flows backwards when walking/sprinting)
            float rel = (float) i / (float) PART_COUNT;
            float movementDragZ = forwardVelocity * 1.8f * rel;
            float movementDragY = -verticalVelocity * 0.8f * rel;
            float movementDragX = -strafeVelocity * 1.5f * rel;

            // Gravity pulling downwards
            y[i] -= gravity * dt * dt * 8.0f;
            y[i] += movementDragY * dt * 5.0f;

            // Backward drag from running
            z[i] -= movementDragZ * dt * 8.0f;
            x[i] += movementDragX * dt * 8.0f;

            // Travelling sine wave flutter
            float wavePhase = (float) Math.toRadians(rel * 360.0f - windTime * waveSpeed);
            float windSwing = (float) Math.sin(wavePhase) * 0.012f * waveStrength;
            z[i] += windSwing;
            x[i] += (float) Math.cos(wavePhase * 0.7f) * (windSwing * 0.5f);
        }

        // Prevent cape clipping into player body
        for (int i = 1; i < PART_COUNT; i++) {
            float maxZ = ((float) i / (float) PART_COUNT) * 0.8f;
            if (z[i] > 0.02f) {
                z[i] = 0.02f;
            }
            if (z[i] < -maxZ) {
                z[i] = -maxZ;
            }
        }

        // Verlet relaxation iterations to maintain strict distance between segments
        int iterations = 4;
        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < PART_COUNT - 1; i++) {
                int a = i;
                int b = i + 1;

                float dx = x[b] - x[a];
                float dy = y[b] - y[a];
                float dz = z[b] - z[a];

                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 1.0E-4F) continue;

                float diff = (dist - SEGMENT_LENGTH) / dist;

                if (a != 0) {
                    x[a] += dx * 0.5f * diff;
                    y[a] += dy * 0.5f * diff;
                    z[a] += dz * 0.5f * diff;
                }
                x[b] -= dx * (a == 0 ? 1.0f : 0.5f) * diff;
                y[b] -= dy * (a == 0 ? 1.0f : 0.5f) * diff;
                z[b] -= dz * (a == 0 ? 1.0f : 0.5f) * diff;
            }
        }
    }

    public float getLerpX(int index, float delta) {
        if (index < 0 || index >= PART_COUNT) return 0;
        return MathHelper.lerp(delta, prevX[index], x[index]);
    }

    public float getLerpY(int index, float delta) {
        if (index < 0 || index >= PART_COUNT) return 0;
        return MathHelper.lerp(delta, prevY[index], y[index]);
    }

    public float getLerpZ(int index, float delta) {
        if (index < 0 || index >= PART_COUNT) return 0;
        return MathHelper.lerp(delta, prevZ[index], z[index]);
    }

    /**
     * Calculates the rotation angle between segment i and i+1 in degrees.
     */
    public float getSegmentPitch(int index, float delta) {
        if (index >= PART_COUNT - 1) {
            return getSegmentPitch(PART_COUNT - 2, delta);
        }
        float dy = getLerpY(index + 1, delta) - getLerpY(index, delta);
        float dz = getLerpZ(index + 1, delta) - getLerpZ(index, delta);
        return (float) Math.toDegrees(Math.atan2(-dz, -dy));
    }

    public float getSegmentRoll(int index, float delta) {
        if (index >= PART_COUNT - 1) {
            return getSegmentRoll(PART_COUNT - 2, delta);
        }
        float dx = getLerpX(index + 1, delta) - getLerpX(index, delta);
        float dy = getLerpY(index + 1, delta) - getLerpY(index, delta);
        return (float) Math.toDegrees(Math.atan2(dx, -dy));
    }
}
