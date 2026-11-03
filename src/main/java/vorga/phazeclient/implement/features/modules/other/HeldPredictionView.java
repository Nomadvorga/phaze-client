package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/** Render-only tick interpolation. No world queries, alpha fades, timers or per-vertex allocations. */
final class HeldPredictionView {
    static final int STEM_TICKS = 2;
    static final int STEM_SEGMENTS = 48;
    private static final double[] STEM_T = new double[STEM_SEGMENTS];
    private static final double[] STEM_B = new double[STEM_SEGMENTS];
    private static final double[] STEM_C = new double[STEM_SEGMENTS];
    private static final double[] STEM_D = new double[STEM_SEGMENTS];
    static {
        for (int i = 1; i < STEM_SEGMENTS; i++) {
            double t = (double) i / STEM_SEGMENTS;
            t = t * t * (3 - 2 * t);
            double u = 1 - t;
            STEM_T[i] = t;
            STEM_B[i] = 3 * u * u * t;
            STEM_C[i] = 3 * u * t * t;
            STEM_D[i] = t * t * t;
        }
    }
    private Object world;
    private Object owner;
    private Predictions.HeldType type;
    private long tick = Long.MIN_VALUE;
    private int boltCount;
    private float angle, yaw, pitch;
    private Predictions.TrajectoryResult previous;
    private Predictions.TrajectoryResult current;
    private Predictions.TrajectoryResult preparedCurrent, preparedPrevious;
    private double preparedAlpha = Double.NaN;
    private long physicalVersion, originVersion = -1;
    private int originMode, originPointCount;
    private Vec3d cachedOrigin, cachedCamera;
    private final Matrix4f cachedForward = new Matrix4f();
    private final Matrix4f cachedInverse = new Matrix4f();
    private double[] physicalCoordinates = new double[3 * 320];
    private double[] coordinates = new double[3 * (320 + STEM_SEGMENTS)];
    private int physicalPointCount, pointCount;
    private Vec3d impact;
    private final Vector4f stemPoint = new Vector4f();
    private final Vector4f stemEnd = new Vector4f();
    private final Vector4f stemNext = new Vector4f();

    void update(Object world, Object owner, Predictions.HeldType type, int boltCount, float angle,
                long tick, float yaw, float pitch, Predictions.TrajectoryResult result) {
        boolean sameContext = this.world == world && this.owner == owner && this.type == type
                && this.boltCount == boltCount && this.angle == angle;
        boolean sameAim = this.yaw == yaw && this.pitch == pitch;
        if (this.tick != tick || !sameContext) {
            previous = sameContext && sameAim && this.tick + 1 == tick && canInterpolate(current, result)
                    ? current : null;
        } else if (!sameAim || !canInterpolate(previous, result)) {
            // Mouse aim is immediate, not dragged behind a previously aimed trajectory.
            previous = null;
        }
        this.world = world;
        this.owner = owner;
        this.type = type;
        this.boltCount = boltCount;
        this.angle = angle;
        this.tick = tick;
        this.yaw = yaw;
        this.pitch = pitch;
        current = result;
    }

    private static boolean canInterpolate(Predictions.TrajectoryResult from, Predictions.TrajectoryResult to) {
        if (from == null || to == null || from.type() != to.type() || from.face() != to.face()
                || from.entity() != to.entity() || from.path().isEmpty() || to.path().isEmpty()
                || from.path().getFirst().squaredDistanceTo(to.path().getFirst()) > 16.0) return false;
        if (to.type() == HitResult.Type.BLOCK) {
            // Never interpolate a marker through a wall onto another parallel surface.
            double normalDistance = (from.impact().x - to.impact().x) * to.face().getOffsetX()
                    + (from.impact().y - to.impact().y) * to.face().getOffsetY()
                    + (from.impact().z - to.impact().z) * to.face().getOffsetZ();
            return Math.abs(normalDistance) < 1.0E-5;
        }
        return true;
    }

    boolean needsSurfaceSampling(Object world, Object owner, Predictions.HeldType type, long tick,
                                 Predictions.TrajectoryResult next) {
        return this.world == world && this.owner == owner && this.type == type && this.tick + 1 == tick
                && current != null && next != null && !current.path().isEmpty() && !next.path().isEmpty()
                && current.path().getFirst().squaredDistanceTo(next.path().getFirst()) <= 16
                && !canInterpolate(current, next);
    }

    void prepare(float tickDelta) {
        List<Vec3d> path = current.path();
        List<Vec3d> old = previous == null ? path : previous.path();
        double alpha = previous == null ? 1.0 : Math.max(0.0, Math.min(1.0, tickDelta));
        pointCount = 0;
        // Stationary/paused frames reuse the exact prepared arc; camera work is separate below.
        if (preparedCurrent == current && preparedPrevious == previous && preparedAlpha == alpha) return;
        preparedCurrent = current;
        preparedPrevious = previous;
        preparedAlpha = alpha;
        physicalVersion++;
        physicalPointCount = Math.max(path.size(), old.size());
        if (physicalCoordinates.length < physicalPointCount * 3) physicalCoordinates = new double[physicalPointCount * 3];
        if (old == path) {
            // Immediate aim and cache hits need a copy, not two resamplings and a blend per coordinate.
            for (int i = 0; i < physicalPointCount; i++) {
                Vec3d point = path.get(i);
                physicalCoordinates[i * 3] = point.x;
                physicalCoordinates[i * 3 + 1] = point.y;
                physicalCoordinates[i * 3 + 2] = point.z;
            }
            impact = path.getLast();
            return;
        }
        if (path.size() == old.size()) {
            for (int i = 0; i < physicalPointCount; i++) {
                Vec3d a = old.get(i), b = path.get(i);
                physicalCoordinates[i * 3] = a.x + (b.x - a.x) * alpha;
                physicalCoordinates[i * 3 + 1] = a.y + (b.y - a.y) * alpha;
                physicalCoordinates[i * 3 + 2] = a.z + (b.z - a.z) * alpha;
            }
        } else {
            for (int i = 0; i < physicalPointCount; i++) {
                double oldIndex = (double) i * (old.size() - 1) / (physicalPointCount - 1);
                double newIndex = (double) i * (path.size() - 1) / (physicalPointCount - 1);
                int oi = (int) oldIndex, ni = (int) newIndex;
                Vec3d oa = old.get(oi), ob = old.get(Math.min(oi + 1, old.size() - 1));
                Vec3d na = path.get(ni), nb = path.get(Math.min(ni + 1, path.size() - 1));
                double ot = oldIndex - oi, nt = newIndex - ni;
                int offset = i * 3;
                physicalCoordinates[offset] = blend(oa.x, ob.x, ot, na.x, nb.x, nt, alpha);
                physicalCoordinates[offset + 1] = blend(oa.y, ob.y, ot, na.y, nb.y, nt, alpha);
                physicalCoordinates[offset + 2] = blend(oa.z, ob.z, ot, na.z, nb.z, nt, alpha);
            }
        }
        int end = (physicalPointCount - 1) * 3;
        impact = new Vec3d(physicalCoordinates[end], physicalCoordinates[end + 1], physicalCoordinates[end + 2]);
    }

    private static double blend(double oa, double ob, double ot, double na, double nb, double nt, double alpha) {
        double old = oa + (ob - oa) * ot;
        double current = na + (nb - na) * nt;
        return old + (current - old) * alpha;
    }

    /** Unproject the real screen center, including view bobbing, hurt tilt and custom FOV. */
    static Vec3d screenCenter(Matrix4f projection, Matrix4f modelView, Matrix4f worldView, Vec3d camera,
                              Matrix4f inverse, Vector3f point) {
        inverse.set(projection).mul(modelView).mul(worldView).invert();
        inverse.transformProject(point.set(0.0F, 0.0F, -0.95F));
        return camera.add(point.x, point.y, point.z);
    }

    int size() { return pointCount; }
    double x(int point) { return coordinates[point * 3]; }
    double y(int point) { return coordinates[point * 3 + 1]; }
    double z(int point) { return coordinates[point * 3 + 2]; }
    Vec3d impact() { return impact; }
    Predictions.TrajectoryResult result() { return current; }

    void useImmediateResult() {
        previous = null;
    }

    void setWorldLaunchOrigin(Vec3d launch) {
        if (reuseOrigin(1, launch)) return;
        pointCount = physicalPointCount;
        if (coordinates.length < pointCount * 3) coordinates = new double[pointCount * 3];
        System.arraycopy(physicalCoordinates, 0, coordinates, 0, pointCount * 3);
        double dx = launch.x - physicalCoordinates[0];
        double dy = launch.y - physicalCoordinates[1];
        double dz = launch.z - physicalCoordinates[2];
        for (int i = 1; i < Math.min(STEM_TICKS, pointCount - 1); i++) {
            double weight = 1 - (double) i / STEM_TICKS;
            coordinates[i * 3] += dx * weight;
            coordinates[i * 3 + 1] += dy * weight;
            coordinates[i * 3 + 2] += dz * weight;
        }
        // Third person starts at the player, NOT on the camera's near plane in front of their body.
        // Keeping the real world depth lets the player/cape occlude the line normally.
        coordinates[0] = launch.x;
        coordinates[1] = launch.y;
        coordinates[2] = launch.z;
        rememberOrigin(1, launch);
    }

    void setLineOrigin(Vec3d origin) {
        if (reuseOrigin(2, origin)) return;
        int join = beginLineOrigin(origin);
        int end = join * 3;
        double ex = physicalCoordinates[end], ey = physicalCoordinates[end + 1], ez = physicalCoordinates[end + 2];
        double dx = ex - origin.x, dy = ey - origin.y, dz = ez - origin.z;
        double ax = dx / 3, ay = dy / 3, az = dz / 3;
        double bx = ax, by = ay, bz = az;
        if (join > 1) {
            double handle = Math.sqrt(dx * dx + dy * dy + dz * dz) / 3;
            int from = join == physicalPointCount - 1 ? end - 3 : end;
            int to = from + 3;
            double sx = physicalCoordinates[to] - physicalCoordinates[from];
            double sy = physicalCoordinates[to + 1] - physicalCoordinates[from + 1];
            double sz = physicalCoordinates[to + 2] - physicalCoordinates[from + 2];
            double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length > 1.0E-7) {
                bx = boundedHandle(sx / length * handle, dx);
                by = boundedHandle(sy / length * handle, dy);
                bz = boundedHandle(sz / length * handle, dz);
            }
        }

        // Ordered Bezier controls keep the whole connector inside its endpoint bounds.
        // A backwards ballistic tangent is clipped, never forced into a hook or sideways loop.
        // Endpoint subdivision is spatial only, not animation or mouse-input smoothing.
        // One-tick collisions use a straight connector, so it cannot overshoot their exact endpoint.
        for (int i = 1; i < STEM_SEGMENTS; i++) {
            double b = STEM_B[i], c = STEM_C[i], d = STEM_D[i];
            int offset = i * 3;
            coordinates[offset] = origin.x + b * ax + c * (dx - bx) + d * dx;
            coordinates[offset + 1] = origin.y + b * ay + c * (dy - by) + d * dy;
            coordinates[offset + 2] = origin.z + b * az + c * (dz - bz) + d * dz;
        }
        rememberOrigin(2, origin);
    }

    void setLineOrigin(Vec3d origin, Matrix4f forward, Matrix4f inverse, Vec3d camera) {
        if (physicalPointCount <= 2) {
            setLineOrigin(origin);
            return;
        }
        if (originMode == 3 && originVersion == physicalVersion && origin.equals(cachedOrigin)
                && camera.equals(cachedCamera) && forward.equals(cachedForward) && inverse.equals(cachedInverse)) {
            pointCount = originPointCount;
            return;
        }
        int join = beginLineOrigin(origin);
        int end = join * 3;
        forward.transform(stemPoint.set((float) (origin.x - camera.x), (float) (origin.y - camera.y),
                (float) (origin.z - camera.z), 1));
        forward.transform(stemEnd.set((float) (physicalCoordinates[end] - camera.x),
                (float) (physicalCoordinates[end + 1] - camera.y), (float) (physicalCoordinates[end + 2] - camera.z), 1));
        int next = join == physicalPointCount - 1 ? end - 3 : end + 3;
        forward.transform(stemNext.set((float) (physicalCoordinates[next] - camera.x),
                (float) (physicalCoordinates[next + 1] - camera.y), (float) (physicalCoordinates[next + 2] - camera.z), 1));
        if (!visible(stemPoint) || !visible(stemEnd)) {
            setLineOrigin(origin);
            return;
        }

        double ox = stemPoint.x / stemPoint.w, oy = stemPoint.y / stemPoint.w;
        double ex = stemEnd.x / stemEnd.w, ey = stemEnd.y / stemEnd.w;
        double dx = ex - ox, dy = ey - oy;
        double length = Math.sqrt(dx * dx + dy * dy);
        boolean nextVisible = visible(stemNext);
        double tx = nextVisible ? stemNext.x / stemNext.w - ex : 0;
        double ty = nextVisible ? stemNext.y / stemNext.w - ey : 0;
        if (next < end) { tx = -tx; ty = -ty; }
        double tangentLength = Math.sqrt(tx * tx + ty * ty);
        double ax = dx / 3, ay = dy / 3, bx = ax, by = ay;
        if (length > 1.0E-7 && tangentLength > 1.0E-7) {
            bx = boundedHandle(tx / tangentLength * length / 3, dx);
            by = boundedHandle(ty / tangentLength * length / 3, dy);
        }

        double oz = stemPoint.z, ow = stemPoint.w, ez = stemEnd.z, ew = stemEnd.w;
        // Bound the displayed curve IN the current projection: world-only limits are not enough
        // under perspective. Both controls stay ordered between the exact moving origin and join.
        for (int i = 1; i < STEM_SEGMENTS; i++) {
            double t = STEM_T[i], b = STEM_B[i], c = STEM_C[i], d = STEM_D[i];
            double x = ox + b * ax + c * (dx - bx) + d * dx;
            double y = oy + b * ay + c * (dy - by) + d * dy;
            // Preserve a monotonic camera-depth span, rather than flattening this into a HUD line.
            double z = (oz + (ez - oz) * t) / (ow + (ew - ow) * t);
            inverse.transform(stemPoint.set((float) x, (float) y, (float) z, 1));
            int offset = i * 3;
            coordinates[offset] = camera.x + stemPoint.x / stemPoint.w;
            coordinates[offset + 1] = camera.y + stemPoint.y / stemPoint.w;
            coordinates[offset + 2] = camera.z + stemPoint.z / stemPoint.w;
        }
        rememberOrigin(3, origin);
        cachedCamera = camera;
        cachedForward.set(forward);
        cachedInverse.set(inverse);
    }

    private boolean reuseOrigin(int mode, Vec3d origin) {
        if (originMode != mode || originVersion != physicalVersion || !origin.equals(cachedOrigin)) return false;
        pointCount = originPointCount;
        return true;
    }

    private void rememberOrigin(int mode, Vec3d origin) {
        originMode = mode;
        originVersion = physicalVersion;
        originPointCount = pointCount;
        cachedOrigin = origin;
    }

    private static double boundedHandle(double handle, double span) {
        // P1 = origin + span/3; P2 = end - handle. Keep P0 <= P1 <= P2 <= P3
        // (or the reverse for a negative span), so every projected component is monotonic.
        double limit = span * (2.0 / 3.0);
        return span > 0 ? Math.max(0, Math.min(handle, limit)) : Math.min(0, Math.max(handle, limit));
    }

    private int beginLineOrigin(Vec3d origin) {
        // Replace only the existing two-tick visual stem, never a longer section of the prediction.
        int join = Math.min(STEM_TICKS, physicalPointCount - 1);
        pointCount = STEM_SEGMENTS + physicalPointCount - join;
        if (coordinates.length < pointCount * 3) coordinates = new double[pointCount * 3];
        coordinates[0] = origin.x;
        coordinates[1] = origin.y;
        coordinates[2] = origin.z;
        // Copy the join and the remaining physical arc verbatim, including the landing point.
        System.arraycopy(physicalCoordinates, join * 3, coordinates, STEM_SEGMENTS * 3, (physicalPointCount - join) * 3);
        return join;
    }

    private static boolean visible(Vector4f point) {
        return point.w > 1.0E-4F && Float.isFinite(point.w) && Float.isFinite(point.x)
                && Float.isFinite(point.y) && Float.isFinite(point.z);
    }

    void reset() {
        previous = null;
        current = null;
        world = null;
        owner = null;
        tick = Long.MIN_VALUE;
        physicalPointCount = pointCount = 0;
        impact = null;
        preparedCurrent = preparedPrevious = null;
        preparedAlpha = Double.NaN;
        originVersion = -1;
        originMode = 0;
        cachedOrigin = cachedCamera = null;
    }
}
