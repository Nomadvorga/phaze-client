package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Offline checks for the deterministic part of Minecraft 1.21.4 projectile launch velocity. */
public final class ProjectileLaunchChecks {
    private static final double EPSILON = 1.0E-10;
    private static int checks;

    public static void main(String[] args) {
        movementInheritance();
        bottleAndPotionRoll();
        crossbowMovement();
        vanillaLaunchMatrix();
        System.out.println("Projectile launch checks passed: " + checks);
    }

    private static void movementInheritance() {
        Vec3d movement = new Vec3d(0.25, 0.42, -0.1);
        vectorEquals(Predictions.initialMotionFor(Predictions.HeldType.SNOWBALL,
                        0, 0, 1.5, movement, true),
                new Vec3d(0.25, 0, 1.4), "grounded throws inherit horizontal movement only");
        vectorEquals(Predictions.initialMotionFor(Predictions.HeldType.SNOWBALL,
                        0, 0, 1.5, movement, false),
                new Vec3d(0.25, 0.42, 1.4), "airborne throws inherit jumping velocity");
        Vec3d falling = new Vec3d(-0.2, -0.78, 0.1);
        vectorEquals(Predictions.initialMotionFor(Predictions.HeldType.ENDER_PEARL,
                        0, 0, 1.5, falling, false),
                new Vec3d(-0.2, -0.78, 1.6), "airborne throws inherit falling velocity");
    }

    private static void bottleAndPotionRoll() {
        for (Predictions.HeldType type : new Predictions.HeldType[]{
                Predictions.HeldType.EXPERIENCE_BOTTLE, Predictions.HeldType.SPLASH_POTION}) {
            double speed = type == Predictions.HeldType.EXPERIENCE_BOTTLE ? 0.7 : 0.5;
            Vec3d motion = Predictions.initialMotionFor(type, 0, 50, speed, Vec3d.ZERO, true);
            float vertical = -MathHelper.sin(30 * ((float) Math.PI / 180.0F));
            float horizontal = MathHelper.cos(50 * ((float) Math.PI / 180.0F));
            vectorEquals(motion, new Vec3d(0, vertical, horizontal).normalize().multiply(speed),
                    "bottle/potion roll changes only the vertical launch component");
            near(motion.length(), speed, "rolled direction is normalized before scaling");
            check(motion.y > Predictions.initialMotionFor(Predictions.HeldType.SNOWBALL,
                            0, 50, speed, Vec3d.ZERO, true).y,
                    "bottles and potions launch higher than ordinary throwables");
        }
    }

    private static void crossbowMovement() {
        for (boolean onGround : new boolean[]{true, false}) {
            Vec3d stationary = Predictions.initialMotionFor(Predictions.HeldType.CROSSBOW,
                    -37.5F, 65, 3.15, Vec3d.ZERO, onGround);
            Vec3d moving = Predictions.initialMotionFor(Predictions.HeldType.CROSSBOW,
                    -37.5F, 65, 3.15, new Vec3d(0.3, -0.8, -0.2), onGround);
            vectorEquals(moving, stationary, "crossbows do not inherit shooter movement");
        }
    }

    private static void vanillaLaunchMatrix() {
        float[] yaws = {-720, -180, -90, -37.5F, 0, 45, 90, 145.25F, 360};
        float[] pitches = {-90, -70, -20, 0, 20, 65, 90};
        double[] speeds = {0.5, 0.7, 1.5, 2.5, 3.15};
        Vec3d[] movements = {Vec3d.ZERO, new Vec3d(0.3, 0.42, -0.2), new Vec3d(-0.2, -0.78, 0.1)};
        for (Predictions.HeldType type : Predictions.HeldType.values()) {
            if (type == Predictions.HeldType.NONE) continue;
            for (float yaw : yaws) {
                for (float pitch : pitches) {
                    for (double speed : speeds) {
                        for (Vec3d movement : movements) {
                            for (boolean onGround : new boolean[]{true, false}) {
                                Vec3d actual = Predictions.initialMotionFor(type, yaw, pitch, speed,
                                        movement, onGround);
                                vectorEquals(actual, referenceLaunch(type, yaw, pitch, speed, movement, onGround),
                                        "launch matches vanilla deterministic math for " + type);
                                check(Double.isFinite(actual.x) && Double.isFinite(actual.y)
                                                && Double.isFinite(actual.z),
                                        "vertical aim and wrapped yaw remain finite");
                                Vec3d inherited = type == Predictions.HeldType.CROSSBOW ? Vec3d.ZERO
                                        : new Vec3d(movement.x, onGround ? 0 : movement.y, movement.z);
                                near(actual.subtract(inherited).length(), speed,
                                        "launch speed is normalized before inheriting movement");
                            }
                        }
                    }
                }
            }
        }
    }

    /** ProjectileEntity.setVelocity(shooter, pitch, yaw, roll, speed, 0), without random spread. */
    private static Vec3d referenceLaunch(Predictions.HeldType type, float yaw, float pitch, double speed,
                                         Vec3d movement, boolean onGround) {
        float radians = (float) Math.PI / 180.0F;
        float roll = type == Predictions.HeldType.EXPERIENCE_BOTTLE
                || type == Predictions.HeldType.SPLASH_POTION ? -20 : 0;
        Vec3d direction = new Vec3d(
                -MathHelper.sin(yaw * radians) * MathHelper.cos(pitch * radians),
                -MathHelper.sin((pitch + roll) * radians),
                MathHelper.cos(yaw * radians) * MathHelper.cos(pitch * radians));
        Vec3d velocity = direction.normalize().multiply(speed);
        if (type != Predictions.HeldType.CROSSBOW) {
            velocity = velocity.add(movement.x, onGround ? 0 : movement.y, movement.z);
        }
        return velocity;
    }

    private static void vectorEquals(Vec3d actual, Vec3d expected, String message) {
        check(actual.squaredDistanceTo(expected) <= EPSILON * EPSILON,
                message + "\nExpected: " + expected + "\nActual: " + actual);
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
