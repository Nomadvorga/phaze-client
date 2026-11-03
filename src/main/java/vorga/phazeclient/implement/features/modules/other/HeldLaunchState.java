package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.Vec3d;

/** Tick-sampled launch inputs for immediate mouse aim without tick-stepped movement. */
final class HeldLaunchState {
    private Object world, owner;
    private Predictions.HeldType type;
    private long tick = Long.MIN_VALUE;
    private long lastAimTick = Long.MIN_VALUE;
    private long lastSurfaceTick = Long.MIN_VALUE;
    private float yaw, pitch;
    private Vec3d previousMovement = Vec3d.ZERO, movement = Vec3d.ZERO;
    private double previousSpeed, speed;

    void update(Object world, Object owner, Predictions.HeldType type, long tick, float yaw, float pitch,
                Vec3d movement, boolean onGround, double speed) {
        Vec3d inherited = onGround ? new Vec3d(movement.x, 0, movement.z) : movement;
        boolean reset = this.world != world || this.owner != owner || this.type != type
                || (this.tick != tick && this.tick + 1 != tick);
        if (reset) {
            previousMovement = inherited;
            this.movement = inherited;
            previousSpeed = speed;
            this.speed = speed;
            lastAimTick = Long.MIN_VALUE;
            lastSurfaceTick = Long.MIN_VALUE;
        } else {
            if (this.tick != tick) {
                previousMovement = this.movement;
                this.movement = inherited;
                previousSpeed = this.speed;
                this.speed = speed;
            }
            if (this.yaw != yaw || this.pitch != pitch) lastAimTick = tick;
        }
        this.world = world;
        this.owner = owner;
        this.type = type;
        this.tick = tick;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    boolean isAiming() {
        return lastAimTick >= tick - 1;
    }

    boolean needsFrameSampling() {
        return isAiming() || lastSurfaceTick >= tick - 1;
    }

    boolean isSamplingSurface() { return lastSurfaceTick >= tick - 1; }

    void sampleSurfaceTransition() { lastSurfaceTick = tick; }

    Vec3d movement(float tickDelta) {
        return previousMovement.lerp(movement, Math.max(0, Math.min(1, tickDelta)));
    }

    double speed(float tickDelta) {
        return previousSpeed + (speed - previousSpeed) * Math.max(0, Math.min(1, tickDelta));
    }

    void reset() {
        world = null;
        owner = null;
        type = null;
        tick = Long.MIN_VALUE;
        lastAimTick = Long.MIN_VALUE;
        lastSurfaceTick = Long.MIN_VALUE;
    }
}
