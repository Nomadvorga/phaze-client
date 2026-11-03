package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Deterministic Minecraft 1.21.4 flight, independent of rendering and world mutation. */
final class ProjectilePhysics {
    static final float HIT_MARGIN = 0.3F;

    enum Profile {
        THROWN(false, 0.03, 0.8F, 0.25F),
        EXPERIENCE_BOTTLE(false, 0.07, 0.8F, 0.25F),
        POTION(false, 0.05, 0.8F, 0.25F),
        ARROW(true, 0.05, 0.6F, 0.5F),
        TRIDENT(true, 0.05, 0.99F, 0.5F);

        final boolean persistent;
        final double gravity;
        final float waterDrag;
        final float size;

        Profile(boolean persistent, double gravity, float waterDrag, float size) {
            this.persistent = persistent;
            this.gravity = gravity;
            this.waterDrag = waterDrag;
            this.size = size;
        }

        static Profile forHeld(Predictions.HeldType type) {
            return switch (type) {
                case EXPERIENCE_BOTTLE -> EXPERIENCE_BOTTLE;
                case SPLASH_POTION -> POTION;
                case BOW, CROSSBOW -> ARROW;
                case TRIDENT -> TRIDENT;
                default -> THROWN;
            };
        }
    }

    interface Environment {
        BlockHitResult blockCollision(Vec3d start, Vec3d end);

        HitResult entityCollision(Vec3d start, Vec3d clippedEnd, Box searchBox);

        /** Entity.baseTick updates fluid flags and pushes velocity AFTER flight integration. */
        FluidMotion fluids(Box bounds, Vec3d motion);
    }

    record FluidMotion(boolean touchingWater, Vec3d motion) {
    }

    private ProjectilePhysics() {
    }

    static double bowSpeed(float pull) {
        return pull < 0.1F ? 0.0 : (double) (pull * 3.0F);
    }

    static Vec3d launchPosition(Vec3d eye) {
        return eye.add(0.0, -(double) 0.1F, 0.0);
    }

    /** CrossbowItem.shoot rotates around the shooter's local up vector, including at vertical aim. */
    static Vec3d crossbowMotion(Vec3d look, Vec3d up, float angle, double speed) {
        Quaternionf rotation = new Quaternionf().setAngleAxis(
                (double) (angle * ((float) Math.PI / 180.0F)), up.x, up.y, up.z);
        Vector3f direction = look.toVector3f().rotate(rotation);
        return new Vec3d(direction.x, direction.y, direction.z).normalize().multiply(speed);
    }

    static float crossbowAngle(int index, int count, float spread) {
        float step = count == 1 ? 0.0F : 2.0F * spread / (float) (count - 1);
        float offset = (float) ((count - 1) % 2) * step / 2.0F;
        float sign = index % 2 == 0 ? 1.0F : -1.0F;
        return offset + sign * (float) ((index + 1) / 2) * step;
    }

    static Box bounds(Vec3d position, float width, float height) {
        double radius = (double) width / 2.0;
        return new Box(position.x - radius, position.y, position.z - radius,
                position.x + radius, position.y + (double) height, position.z + radius);
    }

    /** Both vanilla projectile helpers clip entity queries to the first block/world-border hit. */
    static HitResult collision(Environment environment, Vec3d start, Vec3d end, Box bounds) {
        BlockHitResult block = environment.blockCollision(start, end);
        Vec3d clippedEnd = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        HitResult entity = environment.entityCollision(start, clippedEnd,
                bounds.stretch(end.subtract(start)).expand(1.0));
        return entity != null ? entity : block;
    }

    static HitResult simulate(Vec3d start, Vec3d velocity, Profile profile, double gravity,
                              float width, float height, boolean touchingWater,
                              Environment environment, List<Vec3d> path, int maxTicks, double bottomY) {
        Vec3d pos = start;
        Vec3d motion = velocity;
        path.clear();
        path.add(pos);
        for (int tick = 0; tick < maxTicks; tick++) {
            Vec3d movement = motion;
            float drag = touchingWater ? profile.waterDrag : 0.99F;
            if (!profile.persistent) {
                // ThrownEntity: gravity, then drag, THEN collision and movement.
                motion = motion.add(0.0, -gravity, 0.0).multiply(drag);
                movement = motion;
            } else {
                // PersistentProjectileEntity captures movement at tick start.
                motion = motion.multiply(drag);
            }

            Vec3d next = pos.add(movement);
            HitResult hit = collision(environment, pos, next, bounds(pos, width, height));
            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getPos());
                return hit;
            }
            pos = next;
            path.add(pos);
            if (profile.persistent) {
                motion = motion.add(0.0, -gravity, 0.0);
            }
            FluidMotion fluid = environment.fluids(bounds(pos, width, height), motion);
            touchingWater = fluid.touchingWater();
            motion = fluid.motion();
            if (pos.y < bottomY) break;
        }
        return BlockHitResult.createMissed(pos, Direction.UP, BlockPos.ofFloored(pos));
    }
}
