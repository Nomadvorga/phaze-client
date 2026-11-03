package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Offline regression checks exercise the SAME flight loop used by held and live predictions. */
public final class ProjectileFlightChecks {
    private static int checks;

    public static void main(String[] args) {
        profiles();
        bowCharge();
        launchPosition();
        multishot();
        tickOrderAndWaterEntry();
        closedFormFlights();
        landingMatrix();
        collisions();
        limits();
        System.out.println("Projectile flight checks passed: " + checks);
    }

    private static void profiles() {
        near(ProjectilePhysics.Profile.forHeld(Predictions.HeldType.EXPERIENCE_BOTTLE).gravity,
                0.07, 0, "XP bottles have 0.07 gravity");
        near(ProjectilePhysics.Profile.forHeld(Predictions.HeldType.SPLASH_POTION).gravity,
                0.05, 0, "potions have 0.05 gravity");
        near(ProjectilePhysics.Profile.ARROW.waterDrag, (double) 0.6F, 0, "arrow water drag");
        near(ProjectilePhysics.Profile.TRIDENT.waterDrag, (double) 0.99F, 0, "trident water drag");
        for (Predictions.HeldType type : Predictions.HeldType.values()) {
            boolean persistent = type == Predictions.HeldType.BOW || type == Predictions.HeldType.CROSSBOW
                    || type == Predictions.HeldType.TRIDENT;
            check(ProjectilePhysics.Profile.forHeld(type).persistent == persistent, "held flight family: " + type);
        }
    }

    private static void bowCharge() {
        for (int ticks = 0; ticks <= 100; ticks++) {
            float pull = (float) ticks / 20.0F;
            pull = Math.min((pull * pull + 2.0F * pull) / 3.0F, 1.0F);
            double expected = pull < 0.1F ? 0.0 : (double) (3.0F * pull);
            near(ProjectilePhysics.bowSpeed(pull), expected, 0, "release speed for vanilla pull at " + ticks);
        }
        check(ProjectilePhysics.bowSpeed(bowPull(2)) == 0 && ProjectilePhysics.bowSpeed(bowPull(3)) > 0,
                "bow rejects release below vanilla minimum pull");
        near(ProjectilePhysics.bowSpeed(bowPull(10)), 1.25, 0, "half-charge is not half full speed");
        near(ProjectilePhysics.bowSpeed(bowPull(20)), 3.0, 0, "full bow charge");
    }

    /** BowItem.getPullProgress, without loading the game registries in a standalone JVM. */
    private static float bowPull(int ticks) {
        float pull = (float) ticks / 20.0F;
        return Math.min((pull * pull + 2.0F * pull) / 3.0F, 1.0F);
    }

    private static void launchPosition() {
        for (Vec3d eye : new Vec3d[]{new Vec3d(0, 65.62, 0), new Vec3d(-15, -58.73, 6),
                new Vec3d(29_999_000.125, 180.4, -29_999_000.375)}) {
            Vec3d start = ProjectilePhysics.launchPosition(eye);
            near(start.x, eye.x, 0, "launch X retains double coordinates");
            near(start.z, eye.z, 0, "launch Z retains double coordinates");
            near(start.y, eye.y - (double) 0.1F, 0, "vanilla float eye offset");
        }
        Box bounds = ProjectilePhysics.bounds(new Vec3d(1, 2, 3), 0.25F, 0.25F);
        near(bounds.minY, 2, 0, "projectile coordinates refer to bottom, not center");
        near(bounds.maxY, 2.25, 0, "fluid overlap uses actual projectile height");
        near(bounds.minX, 0.875, 0, "fluid overlap includes projectile width");
    }

    private static void multishot() {
        near(ProjectilePhysics.crossbowAngle(0, 3, 10), 0, 0, "first multishot bolt is central");
        near(ProjectilePhysics.crossbowAngle(1, 3, 10), -10, 0, "second multishot bolt");
        near(ProjectilePhysics.crossbowAngle(2, 3, 10), 10, 0, "third multishot bolt");
        near(ProjectilePhysics.crossbowAngle(0, 1, 10), 0, 0, "single loaded arrow never fans");
        near(ProjectilePhysics.crossbowAngle(1, 3, 0), 0, 0, "removed multishot has no firing spread");
        for (float pitch : new float[]{-90, -60, 0, 60, 90}) {
            for (float yaw : new float[]{-145, 0, 75, 180}) {
                Vec3d look = rotation(pitch, yaw);
                Vec3d up = rotation(pitch - 90, yaw);
                Vec3d center = ProjectilePhysics.crossbowMotion(look, up, 0, (double) 3.15F);
                near(center.length(), (double) 3.15F, 1E-10, "crossbow direction normalized after float rotation");
                for (float angle : new float[]{-10, 10}) {
                    Vec3d side = ProjectilePhysics.crossbowMotion(look, up, angle, (double) 3.15F);
                    double cosine = center.normalize().dotProduct(side.normalize());
                    near(cosine, Math.cos(Math.toRadians(10)), 3E-5, "multishot preserves spread at all pitches");
                    near(side.length(), (double) 3.15F, 1E-10, "all bolts have equal speed");
                    check(Math.abs(side.x - center.x) + Math.abs(side.z - center.z) > 0.1,
                            "vertical aim still produces distinct side bolts");
                }
            }
        }
    }

    private static Vec3d rotation(float pitch, float yaw) {
        float radians = (float) Math.PI / 180.0F;
        return new Vec3d(-MathHelper.sin(yaw * radians) * MathHelper.cos(pitch * radians),
                -MathHelper.sin(pitch * radians), MathHelper.cos(yaw * radians) * MathHelper.cos(pitch * radians));
    }

    private static void tickOrderAndWaterEntry() {
        Vec3d start = new Vec3d(0, 10, 0);
        Vec3d velocity = new Vec3d(1, 0, 0);
        for (ProjectilePhysics.Profile profile : ProjectilePhysics.Profile.values()) {
            TestWorld world = new TestWorld();
            List<Vec3d> path = simulate(start, velocity, profile, profile.gravity, false, world, 1);
            Vec3d first = profile.persistent ? start.add(velocity)
                    : start.add(new Vec3d((double) 0.99F, -profile.gravity * (double) 0.99F, 0));
            vector(path.get(1), first, 1E-10, "first tick order: " + profile);

            world.water = true;
            path = simulate(start, velocity, profile, profile.gravity, false, world, 2);
            vector(path.get(1), first, 1E-10, "fresh spawn starts with unset water flag");
            Vec3d secondMotion = profile.persistent
                    ? new Vec3d((double) 0.99F, -profile.gravity, 0)
                    : new Vec3d((double) 0.99F, -profile.gravity * (double) 0.99F - profile.gravity, 0)
                            .multiply(profile.waterDrag);
            vector(path.get(2), first.add(secondMotion), 1E-10,
                    "water entry updates drag only after Entity.baseTick: " + profile);
        }
        TestWorld flowing = new TestWorld();
        flowing.push = new Vec3d(0.014, 0, 0);
        List<Vec3d> path = simulate(start, velocity, ProjectilePhysics.Profile.ARROW, 0, false, flowing, 2);
        near(path.get(2).x, 1 + (double) 0.99F + 0.014, 1E-10,
                "fluid current applied after movement, before next tick");
    }

    private static void closedFormFlights() {
        Vec3d start = new Vec3d(12.25, 64.5, -9.75);
        for (ProjectilePhysics.Profile profile : ProjectilePhysics.Profile.values()) {
            for (boolean water : new boolean[]{false, true}) {
                TestWorld world = new TestWorld();
                world.water = water;
                double drag = water ? profile.waterDrag : (double) 0.99F;
                for (double gravity : new double[]{0, profile.gravity}) {
                    for (Vec3d velocity : new Vec3d[]{new Vec3d(1.5, 0, 0), new Vec3d(-0.4, 2.5, 1.2),
                            new Vec3d(0, -1.5, 0)}) {
                        for (int ticks : new int[]{1, 2, 10, 40, 120, 300}) {
                            List<Vec3d> path = simulate(start, velocity, profile, gravity, water, world, ticks);
                            double sum = (1 - Math.pow(drag, ticks)) / (1 - drag);
                            double velocitySum = profile.persistent ? sum : drag * sum;
                            double gravitySum = profile.persistent ? gravity / (1 - drag) * (ticks - sum)
                                    : gravity * drag / (1 - drag) * (ticks - drag * sum);
                            Vec3d expected = start.add(velocity.multiply(velocitySum)).add(0, -gravitySum, 0);
                            vector(path.getLast(), expected, 1E-7, "closed-form trajectory: " + profile);
                            check(path.size() == ticks + 1, "exact number of simulated ticks");
                        }
                    }
                }
            }
        }
    }

    private static void landingMatrix() {
        for (Predictions.HeldType type : Predictions.HeldType.values()) {
            if (type == Predictions.HeldType.NONE) continue;
            ProjectilePhysics.Profile profile = ProjectilePhysics.Profile.forHeld(type);
            double speed = switch (type) {
                case EXPERIENCE_BOTTLE -> (double) 0.7F;
                case SPLASH_POTION -> 0.5;
                case TRIDENT -> 2.5;
                case BOW -> 3;
                case CROSSBOW -> (double) 3.15F;
                default -> 1.5;
            };
            for (float pitch : new float[]{-80, -45, -10, 0, 30, 80}) {
                for (float yaw : new float[]{-125, 0, 75}) {
                    for (Vec3d movement : new Vec3d[]{Vec3d.ZERO, new Vec3d(0.3, 0.42, -0.2),
                            new Vec3d(-0.2, -0.78, 0.1)}) {
                        for (boolean water : new boolean[]{false, true}) {
                            Vec3d start = ProjectilePhysics.launchPosition(new Vec3d(5.25, 4.62, -2.5));
                            Vec3d motion = Predictions.initialMotionFor(type, yaw, pitch, speed, movement, false);
                            TestWorld world = new TestWorld();
                            world.floor = true;
                            world.water = water;
                            List<Vec3d> path = new ArrayList<>();
                            HitResult hit = ProjectilePhysics.simulate(start, motion, profile, profile.gravity,
                                    profile.size, profile.size, water, world, path, 300, -128);
                            check(hit.getType() == HitResult.Type.BLOCK, "ballistic trajectory lands: " + type);
                            vector(hit.getPos(), referenceLanding(start, motion, profile, water), 1E-8,
                                    "landing agrees with scalar vanilla recurrence: " + type);
                            vector(path.getLast(), hit.getPos(), 0, "last line point equals impact");
                            check(path.size() > 2, "trajectory is not snapped to a straight target ray");
                        }
                    }
                }
            }
        }
    }

    private static Vec3d referenceLanding(Vec3d start, Vec3d velocity,
                                          ProjectilePhysics.Profile profile, boolean water) {
        double x = start.x, y = start.y, z = start.z;
        double vx = velocity.x, vy = velocity.y, vz = velocity.z;
        double drag = water ? profile.waterDrag : (double) 0.99F;
        for (int tick = 0; tick < 300; tick++) {
            if (!profile.persistent) {
                vx *= drag;
                vy = (vy - profile.gravity) * drag;
                vz *= drag;
            }
            if (y + vy <= 0) {
                double fraction = -y / vy;
                return new Vec3d(x + vx * fraction, 0, z + vz * fraction);
            }
            x += vx;
            y += vy;
            z += vz;
            if (profile.persistent) {
                vx *= drag;
                vy = vy * drag - profile.gravity;
                vz *= drag;
            }
        }
        throw new AssertionError("Reference did not land");
    }

    private static void collisions() {
        Vec3d start = new Vec3d(0, 1, 0), end = new Vec3d(4, 1, 0);
        TestWorld world = new TestWorld();
        world.wallX = 2;
        world.entities.add(new Box(1, 0, -0.25, 1.5, 2, 0.25));
        HitResult hit = ProjectilePhysics.collision(world, start, end, ProjectilePhysics.bounds(start, 0.25F, 0.25F));
        check(hit.getType() == HitResult.Type.ENTITY, "entity closer than a wall wins within the same tick");
        near(hit.getPos().x, 1 - (double) ProjectilePhysics.HIT_MARGIN, 1E-10, "vanilla 0.3F hitbox margin");
        near(world.lastEntityEnd.x, 2, 0, "entity query clipped to wall");
        world.entities.clear();
        world.entities.add(new Box(3, 0, -0.25, 3.5, 2, 0.25));
        hit = ProjectilePhysics.collision(world, start, end, ProjectilePhysics.bounds(start, 0.25F, 0.25F));
        check(hit.getType() == HitResult.Type.BLOCK, "entity behind a wall cannot replace block impact");

        world.wallX = Double.POSITIVE_INFINITY;
        world.entities.clear();
        world.entities.add(new Box(2.5, 0, 0.2, 3, 2, 0.45));
        world.entities.add(new Box(1, 0, 0.2, 1.5, 2, 0.45));
        hit = ProjectilePhysics.collision(world, start, end, ProjectilePhysics.bounds(start, 0.25F, 0.25F));
        check(hit.getType() == HitResult.Type.ENTITY, "grazing projectile uses expanded collision box");
        near(hit.getPos().x, 1 - (double) ProjectilePhysics.HIT_MARGIN, 1E-10, "nearest of several entities wins");
    }

    private static void limits() {
        TestWorld world = new TestWorld();
        List<Vec3d> path = new ArrayList<>();
        HitResult hit = ProjectilePhysics.simulate(new Vec3d(0, -127, 0), new Vec3d(0, -2, 0),
                ProjectilePhysics.Profile.ARROW, 0.05, 0.5F, 0.5F, false, world, path, 300, -128);
        check(hit.getType() == HitResult.Type.MISS && path.size() == 2, "void exit is a miss, not a landing marker");
        Vec3d start = new Vec3d(29_999_000.25, 20, -29_999_000.125);
        Vec3d velocity = new Vec3d(1.5, 0, 0.3);
        List<Vec3d> far = simulate(start, velocity, ProjectilePhysics.Profile.THROWN, 0.03, false, world, 40);
        List<Vec3d> origin = simulate(new Vec3d(0, 20, 0), velocity, ProjectilePhysics.Profile.THROWN,
                0.03, false, world, 40);
        vector(far.getLast().subtract(new Vec3d(start.x, 0, start.z)), origin.getLast(), 1E-6,
                "far-world simulation never truncates world coordinates to floats");
    }

    private static List<Vec3d> simulate(Vec3d start, Vec3d velocity, ProjectilePhysics.Profile profile,
                                       double gravity, boolean water, TestWorld world, int ticks) {
        List<Vec3d> path = new ArrayList<>();
        ProjectilePhysics.simulate(start, velocity, profile, gravity, profile.size, profile.size,
                water, world, path, ticks, -1_000_000);
        return path;
    }

    private static final class TestWorld implements ProjectilePhysics.Environment {
        boolean floor, water;
        double wallX = Double.POSITIVE_INFINITY;
        Vec3d push = Vec3d.ZERO;
        Vec3d lastEntityEnd;
        final List<Box> entities = new ArrayList<>();

        @Override
        public BlockHitResult blockCollision(Vec3d start, Vec3d end) {
            double t = 1.0;
            Direction face = Direction.UP;
            boolean hit = false;
            if (floor && start.y > 0 && end.y <= 0) {
                t = -start.y / (end.y - start.y);
                hit = true;
            }
            if (start.x < wallX && end.x >= wallX) {
                double wallT = (wallX - start.x) / (end.x - start.x);
                if (wallT <= t) {
                    t = wallT;
                    face = Direction.WEST;
                    hit = true;
                }
            }
            Vec3d position = start.lerp(end, t);
            return hit ? new BlockHitResult(position, face, BlockPos.ofFloored(position), false)
                    : BlockHitResult.createMissed(end, Direction.UP, BlockPos.ofFloored(end));
        }

        @Override
        public HitResult entityCollision(Vec3d start, Vec3d end, Box searchBox) {
            lastEntityEnd = end;
            Vec3d nearest = null;
            for (Box entity : entities) {
                var hit = entity.expand(ProjectilePhysics.HIT_MARGIN).raycast(start, end);
                if (hit.isPresent() && (nearest == null
                        || start.squaredDistanceTo(hit.get()) < start.squaredDistanceTo(nearest))) {
                    nearest = hit.get();
                }
            }
            // Null entity is intentional: these checks test collision geometry without a running game.
            return nearest == null ? null : new EntityHitResult(null, nearest);
        }

        @Override
        public ProjectilePhysics.FluidMotion fluids(Box bounds, Vec3d motion) {
            return new ProjectilePhysics.FluidMotion(water, motion.add(push));
        }
    }

    private static void vector(Vec3d actual, Vec3d expected, double epsilon, String message) {
        check(actual.distanceTo(expected) <= epsilon, message + " expected=" + expected + " actual=" + actual);
    }

    private static void near(double actual, double expected, double epsilon, String message) {
        check(Math.abs(actual - expected) <= epsilon, message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
