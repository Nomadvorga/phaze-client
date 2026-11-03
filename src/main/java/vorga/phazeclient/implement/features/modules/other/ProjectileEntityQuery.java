package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Reuses a local broad phase within ONE synchronous simulation; narrow-phase hits remain exact. */
final class ProjectileEntityQuery<T> {
    private static final double QUERY_PADDING = 8.0;

    interface Source<T> {
        List<T> collect(Box bounds);
        Box bounds(T entity);
        boolean canHit(T entity);
    }

    record Contact<T>(T entity, Vec3d position) {}

    private final Source<T> source;
    private Box queryBounds;
    private List<T> candidates = List.of();

    ProjectileEntityQuery(Source<T> source) {
        this.source = source;
    }

    Contact<T> collision(Vec3d start, Vec3d end, Box search) {
        if (queryBounds == null || !contains(queryBounds, search)) {
            queryBounds = search.expand(QUERY_PADDING);
            candidates = source.collect(queryBounds);
        }
        T nearest = null;
        Vec3d position = null;
        double distance = Double.MAX_VALUE;
        for (int i = 0, size = candidates.size(); i < size; i++) {
            T entity = candidates.get(i);
            Box bounds = source.bounds(entity);
            // Match the ORIGINAL, unexpanded world query before applying vanilla's hit margin.
            // The padded query is just a candidate cache, never a larger projectile hitbox.
            if (!bounds.intersects(search) || !source.canHit(entity)) continue;
            var hit = bounds.expand(ProjectilePhysics.HIT_MARGIN).raycast(start, end);
            if (hit.isEmpty()) continue;
            Vec3d point = hit.get();
            double nextDistance = start.squaredDistanceTo(point);
            if (nextDistance < distance) {
                nearest = entity;
                position = point;
                distance = nextDistance;
            }
        }
        return nearest == null ? null : new Contact<>(nearest, position);
    }

    private static boolean contains(Box outer, Box inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }
}
