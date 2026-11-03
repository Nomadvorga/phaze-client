package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Movement-only screen-space origin; aiming and world travel never accumulate a visual offset. */
final class HeldLineAnchor {
    private static final double RETURN_RATE = 12.0;
    private static final long SETTLE_NANOS = 100_000_000L;
    private static final int REFERENCE_TICKS = 8;
    private final Matrix4f forward = new Matrix4f();
    private final Vector4f point = new Vector4f();
    private final Vector4f motionPoint = new Vector4f();
    private Object world, owner, type;
    private long time, tick, lastMotionTime;
    private double offsetX, offsetY;
    private double cameraX, cameraY, cameraZ;
    private boolean sampled;
    private Vec3d origin;

    Vec3d updateForLaunch(Object world, Object owner, Object type, long tick, long now,
                         Matrix4f projection, Matrix4f modelView, Matrix4f worldView, Matrix4f inverse,
                         Vec3d camera, Vec3d launch, Vec3d aimMotion, Vec3d movement, ProjectilePhysics.Profile profile) {
        // Aim determines reference depth ONLY. The origin's displacement comes from player movement.
        // The fixed flight phase is independent of collision/line length and of the visual stem.
        // Eight primitive air steps only: no world queries or additional trajectory prediction.
        double x = launch.x, y = launch.y, z = launch.z;
        double vx = aimMotion.x, vy = aimMotion.y, vz = aimMotion.z;
        double drag = (double) 0.99F;
        double factor = 1, movementWeight = 0;
        for (int i = 0; i < REFERENCE_TICKS; i++) {
            if (!profile.persistent) {
                vx *= drag;
                vy = (vy - profile.gravity) * drag;
                vz *= drag;
                factor *= drag;
            }
            x += vx;
            y += vy;
            z += vz;
            movementWeight += factor;
            if (profile.persistent) {
                vx *= drag;
                vy = vy * drag - profile.gravity;
                vz *= drag;
                factor *= drag;
            }
        }
        return update(world, owner, type, tick, now, projection, modelView, worldView, inverse,
                camera, x, y, z, movement.x * movementWeight, movement.y * movementWeight, movement.z * movementWeight);
    }

    Vec3d update(Object world, Object owner, Object type, long tick, long now,
                 Matrix4f projection, Matrix4f modelView, Matrix4f worldView, Matrix4f inverse,
                 Vec3d camera, double referenceX, double referenceY, double referenceZ,
                 double movementX, double movementY, double movementZ) {
        forward.set(projection).mul(modelView).mul(worldView);
        forward.transform(point.set((float) (referenceX - camera.x), (float) (referenceY - camera.y),
                (float) (referenceZ - camera.z), 1));
        // A direction (w=0) excludes camera/view-bob translation and the launch's off-center bias.
        // Rebase the movement on the exact crosshair, not on a previously aimed world point.
        forward.transform(motionPoint.set((float) movementX, (float) movementY, (float) movementZ, 0));
        double depth = (double) point.w + motionPoint.w;
        boolean visible = point.w > 1.0E-4F && Float.isFinite(point.w) && depth > 1.0E-4 && Double.isFinite(depth)
                && Float.isFinite(motionPoint.x) && Float.isFinite(motionPoint.y);
        double x = visible ? motionPoint.x / depth : 0;
        double y = visible ? motionPoint.y / depth : 0;
        boolean moving = movementX * movementX + movementY * movementY + movementZ * movementZ > 1.0E-14;
        double seconds = (now - time) * 1.0E-9;
        double cameraDx = camera.x - cameraX, cameraDy = camera.y - cameraY, cameraDz = camera.z - cameraZ;
        boolean reset = !sampled || this.world != world || this.owner != owner || this.type != type
                || (this.tick != tick && this.tick + 1 != tick) || seconds < 0 || seconds > 0.25
                || !visible || cameraDx * cameraDx + cameraDy * cameraDy + cameraDz * cameraDz > 64;
        if (reset) {
            offsetX = offsetY = 0;
            lastMotionTime = now;
        }
        if (visible && moving) {
            // Follow the full CURRENT movement, without a cap, damping or cumulative left/right drift.
            offsetX = x;
            offsetY = y;
            lastMotionTime = now;
        } else if (!reset) {
            // Camera rotation never refreshes this timer. Only stopping player movement starts return.
            // Integrate only the idle part of this frame for an FPS-independent return.
            double idleSeconds = Math.max(0, (now - Math.max(time, lastMotionTime + SETTLE_NANOS)) * 1.0E-9);
            double decay = Math.exp(-RETURN_RATE * idleSeconds);
            offsetX *= decay;
            offsetY *= decay;
            if (Math.abs(offsetX) < 1.0E-7) offsetX = 0;
            if (Math.abs(offsetY) < 1.0E-7) offsetY = 0;
        }
        this.world = world;
        this.owner = owner;
        this.type = type;
        this.tick = tick;
        time = now;
        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
        sampled = visible;
        inverse.transform(point.set((float) offsetX, (float) offsetY, -0.95F, 1));
        origin = camera.add(point.x / point.w, point.y / point.w, point.z / point.w);
        return origin;
    }

    Vec3d origin() { return origin; }

    void reset() {
        sampled = false;
        world = owner = type = null;
        offsetX = offsetY = 0;
        origin = null;
    }
}
