package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Offline checks for screen-center anchoring and render-only smoothness at arbitrary frame rates. */
public final class PredictionRenderingChecks {
    private static final Object WORLD = new Object();
    private static final Object OWNER = new Object();
    private static int checks;

    public static void main(String[] args) throws Exception {
        centerProjection();
        cameraOnlyAnchor();
        followingLineOrigin();
        launchReference();
        movementAnchorSymmetry();
        steadyMovementAnchor();
        cameraDoesNotDelayReturn();
        boundedLineStem();
        coherentLineStem();
        projectedLineStem();
        thirdPersonOcclusion();
        smoothMovement();
        immediateAppearanceAndAim();
        movingWhileAiming();
        discontinuities();
        steppedSurfaces();
        changingPathLength();
        bufferReuse();
        System.out.println("Prediction rendering checks passed: " + checks);
    }

    private static void centerProjection() {
        Matrix4f inverse = new Matrix4f();
        Vector3f point = new Vector3f();
        for (float fov : new float[]{30, 70, 110}) {
            for (float aspect : new float[]{1, 16.0F / 9.0F, 21.0F / 9.0F}) {
                Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.05F, 1024);
                for (float yaw : new float[]{0, 65, 180}) {
                    for (float pitch : new float[]{-85, 0, 85}) {
                        Matrix4f world = new Matrix4f().rotateX((float) Math.toRadians(pitch))
                                .rotateY((float) Math.toRadians(yaw));
                        for (float bob : new float[]{0, 0.03F, -0.05F}) {
                            Matrix4f model = new Matrix4f().translate(bob, bob * 0.4F, 0)
                                    .rotateZ(bob * 0.5F);
                            for (Vec3d camera : new Vec3d[]{new Vec3d(5, 65, -9),
                                    new Vec3d(29_999_000.25, 110, -29_999_000.125)}) {
                                Vec3d anchor = HeldPredictionView.screenCenter(projection, model, world,
                                        camera, inverse, point);
                                Vector3f clip = anchor.subtract(camera).toVector3f();
                                new Matrix4f(projection).mul(model).mul(world).transformProject(clip);
                                near(clip.x, 0, 1E-5, "line origin projects to horizontal screen center");
                                near(clip.y, 0, 1E-5, "line origin projects to vertical center despite bobbing");
                                near(clip.z, -0.95, 1E-5, "origin stays inside the near clip plane");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void cameraOnlyAnchor() {
        for (ProjectilePhysics.Profile profile : ProjectilePhysics.Profile.values()) {
            for (Vec3d camera : new Vec3d[]{new Vec3d(5, 65, -9),
                    new Vec3d(29_999_000.25, 110, -29_999_000.125)}) {
                HeldLineAnchor anchor = new HeldLineAnchor();
                for (int frame = 0; frame <= 120; frame++) {
                    Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70 + 35 * Math.sin(frame * 0.07)),
                            16.0F / 9, 0.05F, 1024);
                    Matrix4f worldView = new Matrix4f().rotateX((float) (0.9 * Math.sin(frame * 0.03)))
                            .rotateY(frame * 0.04F);
                    Matrix4f modelView = new Matrix4f().translate((float) (0.03 * Math.sin(frame * 0.09)), 0.012F, 0)
                            .rotateZ((float) (0.025 * Math.sin(frame * 0.05)));
                    Matrix4f forward = new Matrix4f(projection).mul(modelView).mul(worldView);
                    Matrix4f inverse = new Matrix4f(forward).invert();
                    Vector3f look = new Matrix4f(worldView).invert().transformDirection(new Vector3f(0, 0, -1));
                    Vec3d motion = new Vec3d(look.x, look.y, look.z).multiply(1.5 + 0.6 * Math.sin(frame * 0.08));
                    Vec3d origin = anchor.updateForLaunch(WORLD, OWNER, profile, frame / 3, Math.round(frame * 1E9 / 60),
                            projection, modelView, worldView, inverse, camera, ProjectilePhysics.launchPosition(camera),
                            motion, Vec3d.ZERO, profile);
                    Vec3d projected = project(forward, camera, origin);
                    near(projected.x, 0, 1E-5, "camera-only yaw/pitch/FOV and charging cannot push the origin sideways");
                    near(projected.y, 0, 1E-5, "stationary aiming keeps the line origin at the actual crosshair");
                    near(projected.z, -0.95, 1E-5, "stationary origin remains inside the near clipping plane");
                }
            }
        }
    }

    private static void followingLineOrigin() {
        Matrix4f identity = new Matrix4f();
        for (int fps : new int[]{30, 60, 144, 240}) {
            HeldLineAnchor anchor = new HeldLineAnchor();
            Vec3d origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 0,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
            near(origin.x, 0, 0, "first appearance starts at the crosshair");
            for (int frame = 1; frame <= fps; frame++) {
                double seconds = (double) frame / fps;
                origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL,
                        (long) (seconds * 20), Math.round(seconds * 1E9),
                        identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, seconds * 0.8, -seconds * 0.3, 0);
                near(origin.x, seconds * 0.8, 1E-7, "origin follows the full CURRENT horizontal movement at " + fps + " FPS");
                near(origin.y, -seconds * 0.3, 1E-7, "moving origin has no immediate return damping");
            }
            near(origin.x, 0.8, 1E-7, "origin is not constrained by the former tiny screen-space cap");
            for (int frame = 1; frame <= fps; frame++) {
                double seconds = 1 + (double) frame / fps;
                origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL,
                        (long) (seconds * 20), Math.round(seconds * 1E9),
                        identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
                if (frame * 10 <= fps) near(origin.x, 0.8, 1E-7,
                        "stopping does not pull the origin back during the settling interval");
            }
            near(origin.x, 0.8 * Math.exp(-12 * 0.9), 1E-7,
                    "return starts only after player movement settles and is independent of FPS");
            origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 40, 2_001_000_000L,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0.8, -0.3, 0);
            near(origin.x, 0.8, 1E-7, "resumed movement replaces the stale returning offset instead of accumulating it");
            origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 40, 2_001_000_000L,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 1.6, -0.3, 0);
            near(origin.x, 1.6, 1E-7, "fast movement follows a displacement above 0.5 immediately and without a cap");
            for (int frame = 1; frame <= fps * 2; frame++) {
                double seconds = 2.001 + (double) frame / fps;
                origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL,
                        (long) (seconds * 20), Math.round(seconds * 1E9),
                        identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
            }
            near(origin.x, 0, 1E-7, "settled origin eventually returns exactly to the crosshair");
            anchor.reset();
            origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 81, 4_050_000_000L,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
            near(origin.x, 0, 0, "reappearance has no stale return animation");
            origin = anchor.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 81, 4_050_000_000L,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, -0.8, 0, 0);
            near(origin.x, -0.8, 1E-7, "reappearing while moving has no fade-in or opposite-side residual");
        }
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult physical = trajectory(0, 30, 0);
        update(view, 1, 0, physical);
        view.prepare(1);
        view.setLineOrigin(new Vec3d(0.1, 0.2, 0.3));
        vector(view.impact(), physical.impact(), 0, "moving stem leaves the landing point unchanged");
        check(view.result() == physical, "moving stem never changes launch physics");
        HeldLineAnchor walking = new HeldLineAnchor();
        walking.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 0,
                identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
        Vec3d shifted = walking.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 16_666_667L,
                identity, identity, identity, identity, new Vec3d(0.02, 0, 0), 0.02, 0, -1, 0.02, 0, 0);
        near(shifted.x - 0.02, 0.02, 1E-7, "camera translation cannot cancel or weaken the moving origin");
        Vec3d teleportCamera = new Vec3d(20, 0, 0);
        Vec3d teleportOrigin = walking.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 1, 50_000_000L,
                identity, identity, identity, identity, teleportCamera, 20.02, 0, -1, 0, 0, 0);
        near(teleportOrigin.x, teleportCamera.x, 0, "actual camera teleports reset the origin instead of drawing a phantom offset");

        HeldLineAnchor turning = new HeldLineAnchor();
        turning.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 0,
                identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -2, 0, 0, 0);
        Matrix4f rotated = new Matrix4f().rotateY(0.1F);
        Matrix4f inverse = new Matrix4f(rotated).invert();
        Vector3f aimed = inverse.transformPosition(new Vector3f(0, 0, -2));
        Vec3d turningOrigin = turning.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 16_666_667L,
                identity, identity, rotated, inverse, Vec3d.ZERO, aimed.x, aimed.y, aimed.z, 0, 0, 0);
        Vector3f projectedOrigin = rotated.transformPosition(turningOrigin.toVector3f());
        near(projectedOrigin.x, 0, 1E-6, "mouse rotation cannot create a movement offset");
        near(projectedOrigin.y, 0, 1E-6, "camera-only rotation keeps the origin on the crosshair");

        HeldLineAnchor uneven = new HeldLineAnchor();
        uneven.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 0,
                identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
        for (long now : new long[]{7_000_000L, 26_000_000L, 49_000_000L, 73_000_000L, 100_000_000L}) {
            double position = now * 1E-9;
            Vec3d origin = uneven.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, now / 50_000_000L, now,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, position, 0, 0);
            near(origin.x, position, 1E-7, "uneven frame times do not attenuate trajectory motion");
        }
        Vec3d sameFrame = uneven.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 2, 100_000_000L,
                identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0.2, 0, 0);
        near(sameFrame.x, 0.2, 1E-7, "a same-timestamp movement update has no one-frame delay");
        for (long now : new long[]{119_000_000L, 149_000_000L, 198_000_000L, 224_000_000L, 249_000_000L}) {
            Vec3d origin = uneven.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, now / 50_000_000L, now,
                    identity, identity, identity, identity, Vec3d.ZERO, 0, 0, -1, 0, 0, 0);
            double returning = Math.max(0, (now - 200_000_000L) * 1E-9);
            near(origin.x, 0.2 * Math.exp(-12 * returning), 1E-7,
                    "a frame crossing the idle threshold integrates only its returning portion");
        }
    }

    private static void launchReference() {
        Matrix4f identity = new Matrix4f();
        Vec3d launch = new Vec3d(0.1, 1.5, -0.2);
        Vec3d aimMotion = new Vec3d(0.3, 0.2, -1.5);
        Vec3d movement = new Vec3d(0.1, -0.1, 0.05);
        for (ProjectilePhysics.Profile profile : ProjectilePhysics.Profile.values()) {
            List<Vec3d> stationary = airPath(launch, aimMotion, profile, 8);
            List<Vec3d> moving = airPath(launch, aimMotion.add(movement), profile, 8);
            HeldLineAnchor anchor = new HeldLineAnchor();
            anchor.updateForLaunch(WORLD, OWNER, profile, 0, 0, identity, identity, identity, identity,
                    Vec3d.ZERO, launch, aimMotion, Vec3d.ZERO, profile);
            Vec3d origin = anchor.updateForLaunch(WORLD, OWNER, profile, 0, 16_666_667L,
                    identity, identity, identity, identity, Vec3d.ZERO, launch, aimMotion, movement, profile);
            near(origin.x, moving.getLast().x - stationary.getLast().x, 1E-6,
                    "movement-only sample retains the full fixed-flight displacement for " + profile);
            near(origin.y, moving.getLast().y - stationary.getLast().y, 1E-6,
                    "movement weight retains the projectile-specific gravity/drag order for " + profile);
        }
    }

    private static void movementAnchorSymmetry() {
        for (float fov : new float[]{30, 70, 110}) {
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), 16.0F / 9, 0.05F, 1024);
            for (float yaw : new float[]{-1.4F, 0, 2.6F}) {
                for (float pitch : new float[]{-1, 0, 1}) {
                    Matrix4f worldView = new Matrix4f().rotateX(pitch).rotateY(yaw);
                    Matrix4f modelView = new Matrix4f().translate(0.03F, 0.012F, 0).rotateZ(0.015F);
                    Matrix4f forward = new Matrix4f(projection).mul(modelView).mul(worldView);
                    Matrix4f inverse = new Matrix4f(forward).invert();
                    Matrix4f worldInverse = new Matrix4f(worldView).invert();
                    Vector3f look = worldInverse.transformDirection(new Vector3f(0, 0, -1));
                    Vector3f right = worldInverse.transformDirection(new Vector3f(1, 0, 0));
                    Vec3d aim = new Vec3d(look.x, look.y, look.z).multiply(1.5);
                    Vec3d movement = new Vec3d(right.x, right.y, right.z).multiply(0.28);
                    for (Vec3d camera : new Vec3d[]{new Vec3d(5, 65, -9),
                            new Vec3d(29_999_000.25, 110, -29_999_000.125)}) {
                        Vec3d launch = ProjectilePhysics.launchPosition(camera);
                        for (ProjectilePhysics.Profile profile : ProjectilePhysics.Profile.values()) {
                            HeldLineAnchor anchor = new HeldLineAnchor();
                            Vec3d rightOrigin = anchor.updateForLaunch(WORLD, OWNER, profile, 0, 0,
                                    projection, modelView, worldView, inverse, camera, launch, aim, movement, profile);
                            Vec3d rightScreen = project(forward, camera, rightOrigin);
                            Vec3d expected = movementProjection(forward, camera, launch, aim, movement, profile);
                            vector(rightScreen, expected, 1E-5, "moving origin matches full projectile-profile displacement without an aim bias");
                            Vec3d leftOrigin = anchor.updateForLaunch(WORLD, OWNER, profile, 0, 16_666_667L,
                                    projection, modelView, worldView, inverse, camera, launch, aim, movement.multiply(-1), profile);
                            Vec3d leftScreen = project(forward, camera, leftOrigin);
                            near(leftScreen.x, -rightScreen.x, 1E-5, "left/right movement is symmetric despite FOV, yaw and view-bob translation");
                            near(leftScreen.y, -rightScreen.y, 1E-5, "view tilt cannot add a permanent offset to mirrored movement");
                            check(rightScreen.x > 0.02, "movement remains visible rather than being pinned to the crosshair");

                            // Forward/backward movement must not inherit the translated camera's left/right bias.
                            for (double speed : new double[]{0.28, -0.28}) {
                                Vec3d alongAim = new Vec3d(look.x, look.y, look.z).multiply(speed);
                                Vec3d origin = anchor.updateForLaunch(WORLD, OWNER, profile, 0, 33_333_333L,
                                        projection, modelView, worldView, inverse, camera, launch, aim, alongAim, profile);
                                Vec3d screen = project(forward, camera, origin);
                                near(screen.x, 0, 1E-5, "forward/backward travel cannot create a left-side bias from camera bobbing");
                                near(screen.y, 0, 1E-5, "movement along the camera ray has no artificial screen-space displacement");
                            }
                        }
                    }
                }
            }
        }
    }

    private static Vec3d movementProjection(Matrix4f forward, Vec3d camera, Vec3d launch, Vec3d aim,
                                           Vec3d movement, ProjectilePhysics.Profile profile) {
        Vec3d stationary = airPath(launch, aim, profile, 8).getLast();
        Vec3d moving = airPath(launch, aim.add(movement), profile, 8).getLast();
        Vec3d relative = stationary.subtract(camera), delta = moving.subtract(stationary);
        Vector4f reference = forward.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1));
        Vector4f direction = forward.transform(new Vector4f((float) delta.x, (float) delta.y, (float) delta.z, 0));
        double depth = (double) reference.w + direction.w;
        return new Vec3d(direction.x / depth, direction.y / depth, -0.95);
    }

    private static void steadyMovementAnchor() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16.0F / 9, 0.05F, 1024);
        Matrix4f identity = new Matrix4f();
        Matrix4f inverse = new Matrix4f(projection).invert();
        Vec3d aim = new Vec3d(0, 0, -1.5), movement = new Vec3d(0.28, 0, 0);
        for (int fps : new int[]{30, 60, 144, 240}) {
            for (Vec3d base : new Vec3d[]{new Vec3d(5, 65, -9),
                    new Vec3d(29_999_000.25, 110, -29_999_000.125)}) {
                for (ProjectilePhysics.Profile profile : new ProjectilePhysics.Profile[]{
                        ProjectilePhysics.Profile.THROWN, ProjectilePhysics.Profile.ARROW}) {
                    HeldLineAnchor anchor = new HeldLineAnchor();
                    Vec3d expected = movementProjection(projection, base, ProjectilePhysics.launchPosition(base), aim, movement, profile);
                    Vec3d camera = base;
                    for (int frame = 0; frame <= fps * 10; frame++) {
                        double seconds = (double) frame / fps;
                        camera = base.add(movement.multiply(seconds * 20));
                        Vec3d origin = anchor.updateForLaunch(WORLD, OWNER, profile, (long) (seconds * 20), Math.round(seconds * 1E9),
                                projection, identity, identity, inverse, camera, ProjectilePhysics.launchPosition(camera), aim, movement, profile);
                        Vec3d screen = project(projection, camera, origin);
                        near(screen.x, expected.x, 1E-5, "ten seconds of walking cannot accumulate lateral drift at " + fps + " FPS");
                        near(screen.y, expected.y, 1E-5, "steady movement has no continuous return damping or accumulated vertical offset");
                    }
                    Vec3d opposite = anchor.updateForLaunch(WORLD, OWNER, profile, 200, 10_000_000_000L,
                            projection, identity, identity, inverse, camera, ProjectilePhysics.launchPosition(camera),
                            aim, movement.multiply(-1), profile);
                    near(project(projection, camera, opposite).x, -expected.x, 1E-5,
                            "changing strafe direction replaces the previous side instead of retaining a left/right bias");
                    for (int frame = 1; frame <= fps; frame++) {
                        double seconds = 10 + (double) frame / fps;
                        Vec3d origin = anchor.updateForLaunch(WORLD, OWNER, profile, (long) (seconds * 20), Math.round(seconds * 1E9),
                                projection, identity, identity, inverse, camera, ProjectilePhysics.launchPosition(camera), aim, Vec3d.ZERO, profile);
                        double returning = Math.max(0, seconds - 10.1);
                        near(project(projection, camera, origin).x, -expected.x * Math.exp(-12 * returning), 1E-5,
                                "stopping after a long walk returns to the crosshair with the same FPS-independent timing");
                    }
                }
            }
        }
    }

    private static void cameraDoesNotDelayReturn() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16.0F / 9, 0.05F, 1024);
        Matrix4f identity = new Matrix4f();
        Matrix4f inverse = new Matrix4f(projection).invert();
        Vec3d camera = new Vec3d(5, 65, -9);
        HeldLineAnchor anchor = new HeldLineAnchor();
        Vec3d origin = anchor.updateForLaunch(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 0, 0,
                projection, identity, identity, inverse, camera, ProjectilePhysics.launchPosition(camera),
                new Vec3d(0, 0, -1.5), new Vec3d(0.28, 0.1, 0), ProjectilePhysics.Profile.THROWN);
        Vec3d initial = project(projection, camera, origin);
        Vec3d screen = initial;
        for (int frame = 1; frame <= 240; frame++) {
            double seconds = (double) frame / 120;
            projection = new Matrix4f().perspective((float) Math.toRadians(70 + 30 * Math.sin(frame * 0.05)),
                    16.0F / 9, 0.05F, 1024);
            Matrix4f worldView = new Matrix4f().rotateX((float) (0.9 * Math.sin(frame * 0.04))).rotateY(frame * 0.08F);
            Matrix4f modelView = new Matrix4f().translate((float) (0.03 * Math.sin(frame * 0.02)), 0.012F, 0).rotateZ(0.02F);
            Matrix4f forward = new Matrix4f(projection).mul(modelView).mul(worldView);
            inverse = new Matrix4f(forward).invert();
            Vector3f look = new Matrix4f(worldView).invert().transformDirection(new Vector3f(0, 0, -1));
            Vec3d aim = new Vec3d(look.x, look.y, look.z).multiply(1.5 + 0.6 * Math.sin(frame * 0.08));
            origin = anchor.updateForLaunch(WORLD, OWNER, Predictions.HeldType.SNOWBALL, (long) (seconds * 20), Math.round(seconds * 1E9),
                    projection, modelView, worldView, inverse, camera, ProjectilePhysics.launchPosition(camera),
                    aim, Vec3d.ZERO, ProjectilePhysics.Profile.THROWN);
            screen = project(forward, camera, origin);
            double decay = Math.exp(-12 * Math.max(0, seconds - 0.1));
            near(screen.x, initial.x * decay, 1E-5, "turning/charging while stationary cannot add sideways offset or restart the return timer");
            near(screen.y, initial.y * decay, 1E-5, "camera pitch cannot add vertical drift or delay idle return");
        }
        near(screen.x, 0, 1E-5, "idle return eventually reaches the exact crosshair despite continuous camera rotation");
        near(screen.y, 0, 1E-5, "idle vertical offset also returns to the exact crosshair");
    }

    private static void boundedLineStem() {
        Matrix4f forward = new Matrix4f().perspective((float) Math.toRadians(70), 16.0F / 9, 0.05F, 1024);
        Matrix4f inverse = new Matrix4f(forward).invert();
        Vec3d camera = new Vec3d(5, 65, -9);
        for (float[] tangent : new float[][]{{0, -0.2F}, {0, 0.2F}, {0.2F, 0}, {-0.2F, 0},
                {0.2F, -0.2F}, {-0.2F, 0.2F}, {0, 0}}) {
            Vec3d end = unproject(inverse, camera, 0, 0, 0.9F);
            List<Vec3d> path = List.of(unproject(inverse, camera, 0, 0, -0.95F),
                    unproject(inverse, camera, 0, 0, 0.8F), end,
                    unproject(inverse, camera, tangent[0], tangent[1], 0.95F));
            for (int count : new int[]{2, 3, 4}) {
                List<Vec3d> points = path.subList(0, count);
                Predictions.TrajectoryResult physical = new Predictions.TrajectoryResult(points, points.getLast(),
                        HitResult.Type.MISS, Direction.UP, null);
                HeldPredictionView view = new HeldPredictionView();
                update(view, 1, 0, physical);
                view.prepare(1);
                for (float[] offset : new float[][]{{0, -0.4F}, {0, 0.4F}, {-0.4F, 0}, {0.4F, 0},
                        {-0.4F, 0.4F}, {0.4F, -0.4F}, {0, 0}, {0.0001F, -0.0001F}, {1.5F, -1.5F}}) {
                    Vec3d origin = unproject(inverse, camera, offset[0], offset[1], -0.95F);
                    view.setLineOrigin(origin, forward, inverse, camera);
                    boundedStem(view, forward, camera, "opposing/degenerate projected directions, points=" + count);
                    vector(point(view, 0), origin, 0, "shape limits do not cap or damp the moving origin");
                    vector(point(view, view.size() - 1), physical.impact(), 0,
                            "shape limits do not change the physical landing");
                }
            }
        }

        // A clipped next vertex must not re-enable the old world-space hook.
        List<Vec3d> clipped = List.of(camera.add(0, 0, -0.1), camera.add(0, 0, -1),
                camera.add(0, -0.2, -2), camera.add(0, 0.2, 1));
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult physical = new Predictions.TrajectoryResult(clipped, clipped.getLast(),
                HitResult.Type.MISS, Direction.UP, null);
        update(view, 1, 0, physical);
        view.prepare(1);
        view.setLineOrigin(unproject(inverse, camera, 0, -0.4F, -0.95F), forward, inverse, camera);
        boundedStem(view, forward, camera, "next physical vertex behind the camera");

        // When the join itself is behind the camera, constrain the fallback in world space.
        List<Vec3d> behind = List.of(camera.add(0, 0, -0.1), camera.add(0, -0.2, -1),
                camera.add(0, -0.4, 1), camera.add(0, 0.2, 2));
        physical = new Predictions.TrajectoryResult(behind, behind.getLast(), HitResult.Type.MISS, Direction.UP, null);
        update(view, 2, 0, physical);
        view.prepare(1);
        Vec3d origin = unproject(inverse, camera, 0.4F, 0.4F, -0.95F);
        view.setLineOrigin(origin, forward, inverse, camera);
        boundedWorldStem(view);
        vector(point(view, view.size() - 1), physical.impact(), 0, "clipping fallback preserves the physical endpoint");
    }

    private static void boundedStem(HeldPredictionView view, Matrix4f forward, Vec3d camera, String context) {
        Vec3d origin = project(forward, camera, point(view, 0));
        Vec3d end = project(forward, camera, point(view, HeldPredictionView.STEM_SEGMENTS));
        Vec3d previous = origin;
        for (int i = 1; i <= HeldPredictionView.STEM_SEGMENTS; i++) {
            Vec3d current = project(forward, camera, point(view, i));
            boundedComponent(current.x, previous.x, origin.x, end.x, 2E-6, context + " horizontal point=" + i);
            boundedComponent(current.y, previous.y, origin.y, end.y, 2E-6, context + " vertical point=" + i);
            boundedComponent(current.z, previous.z, origin.z, end.z, 2E-6, context + " depth point=" + i);
            previous = current;
        }
    }

    private static void boundedWorldStem(HeldPredictionView view) {
        Vec3d origin = point(view, 0);
        Vec3d end = point(view, HeldPredictionView.STEM_SEGMENTS);
        Vec3d previous = origin;
        for (int i = 1; i <= HeldPredictionView.STEM_SEGMENTS; i++) {
            Vec3d current = point(view, i);
            boundedComponent(current.x, previous.x, origin.x, end.x, 1E-10, "world x point=" + i);
            boundedComponent(current.y, previous.y, origin.y, end.y, 1E-10, "world y point=" + i);
            boundedComponent(current.z, previous.z, origin.z, end.z, 1E-10, "world z point=" + i);
            previous = current;
        }
    }

    private static void boundedComponent(double value, double previous, double origin, double end,
                                         double epsilon, String context) {
        check(Double.isFinite(value) && value >= Math.min(origin, end) - epsilon
                        && value <= Math.max(origin, end) + epsilon,
                "connector never leaves its endpoint bounds: " + context + " origin=" + origin + " end=" + end + " actual=" + value);
        check((value - previous) * Math.signum(end - origin) >= -epsilon,
                "connector never doubles back or loops: " + context);
    }

    private static void coherentLineStem() {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult physical = trajectory(0, 30, 0);
        update(view, 1, 0, physical);
        view.prepare(1);
        int compatibleJoins = 0;
        for (Vec3d origin : new Vec3d[]{new Vec3d(0.1, 1.7, 0.05), new Vec3d(-0.3, 1.2, 0.05),
                new Vec3d(0.6, 1.8, 0.1), physical.path().getFirst()}) {
            view.setLineOrigin(origin);
            vector(point(view, 0), origin, 0, "the complete connector is rebuilt from the exact moving origin");
            vector(view.impact(), physical.impact(), 0, "coherent stem never moves the impact marker");
            check(view.result() == physical, "stem construction never rewrites projectile physics");
            for (int i = HeldPredictionView.STEM_TICKS; i < physical.path().size(); i++) {
                vector(point(view, arcIndex(i)), physical.path().get(i), 0,
                        "the physical arc after the connector is copied verbatim");
            }
            int join = HeldPredictionView.STEM_SEGMENTS;
            Vec3d incoming = point(view, join).subtract(point(view, join - 1)).normalize();
            Vec3d outgoing = point(view, join + 1).subtract(point(view, join)).normalize();
            // A backwards tangent cannot be matched without overshooting the fixed join.
            // Keep tangent matching where it fits; require bounded geometry in every case.
            if (tangentFits(point(view, join).subtract(origin), outgoing)) {
                compatibleJoins++;
                check(incoming.dotProduct(outgoing) > 0.9999, "compatible connector rejoins the ballistic tangent smoothly");
            }
            boundedWorldStem(view);
            for (int i = 1; i < join; i++) {
                Vec3d a = point(view, i).subtract(point(view, i - 1)).normalize();
                Vec3d b = point(view, i + 1).subtract(point(view, i)).normalize();
                check(a.dotProduct(b) > 0.98, "neighboring connector segments turn smoothly rather than leaving a sharp kink");
            }
        }
        check(compatibleJoins > 0, "world-space checks still exercise exact ballistic tangent matching");

        Predictions.TrajectoryResult shortPath = new Predictions.TrajectoryResult(
                List.of(new Vec3d(0, 1, 0), new Vec3d(0, 0.9, 0.2)), new Vec3d(0, 0.9, 0.2),
                HitResult.Type.BLOCK, Direction.UP, null);
        update(view, 2, 0, shortPath);
        Vec3d origin = new Vec3d(0.2, 1.2, 0.05);
        prepare(view, 0.5F, origin);
        Vec3d chord = shortPath.impact().subtract(origin);
        for (int i = 0; i < view.size(); i++) {
            Vec3d displacement = point(view, i).subtract(origin);
            double progress = displacement.dotProduct(chord) / chord.lengthSquared();
            check(progress >= -1E-10 && progress <= 1 + 1E-10,
                    "a one-tick collision connector never overshoots the landing point");
            vector(point(view, i), origin.add(chord.multiply(progress)), 1E-10,
                    "short collision connections stay straight and continuous");
        }
        vector(point(view, view.size() - 1), shortPath.impact(), 0, "short connector ends at the exact physical collision");
    }

    private static void projectedLineStem() {
        int compatibleJoins = 0;
        for (float fov : new float[]{30, 70, 110}) {
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), 16.0F / 9, 0.05F, 1024);
            for (float pitch : new float[]{-60, 0, 60}) {
                Matrix4f worldView = new Matrix4f().rotateX((float) Math.toRadians(pitch)).rotateY(0.4F);
                Matrix4f modelView = new Matrix4f().translate(0.03F, 0.012F, 0).rotateZ(0.015F);
                Matrix4f forward = new Matrix4f(projection).mul(modelView).mul(worldView);
                Matrix4f inverse = new Matrix4f(forward).invert();
                Vector3f look = new Matrix4f(worldView).invert().transformDirection(new Vector3f(0, 0, -1));
                for (Vec3d camera : new Vec3d[]{new Vec3d(5, 65, -9),
                        new Vec3d(29_999_000.25, 110, -29_999_000.125)}) {
                    for (ProjectilePhysics.Profile profile : new ProjectilePhysics.Profile[]{
                            ProjectilePhysics.Profile.THROWN, ProjectilePhysics.Profile.ARROW}) {
                        Vec3d launch = camera.add(0, -(double) 0.1F, 0);
                        Vec3d motion = new Vec3d(look.x, look.y, look.z).multiply(1.5);
                        List<Vec3d> path = airPath(launch, motion, profile, 30);
                        Predictions.TrajectoryResult physical = new Predictions.TrajectoryResult(path, path.getLast(),
                                HitResult.Type.MISS, Direction.UP, null);
                        HeldPredictionView view = new HeldPredictionView();
                        update(view, 1, 0, physical);
                        view.prepare(1);
                        for (float[] offset : new float[][]{{0, 0}, {0.8F, -0.4F}, {-0.8F, 0.4F},
                                {0.8F, 0.4F}, {-0.8F, -0.4F}, {0, -0.4F}, {0, 0.4F}}) {
                            Vector3f relative = inverse.transformProject(new Vector3f(offset[0], offset[1], -0.95F));
                            Vec3d origin = camera.add(relative.x, relative.y, relative.z);
                            view.setLineOrigin(origin, forward, inverse, camera);
                            Vec3d screenOrigin = project(forward, camera, point(view, 0));
                            near(screenOrigin.x, offset[0], 1E-5, "large moving-origin offsets survive real perspective/FOV/bobbing");
                            near(screenOrigin.y, offset[1], 1E-5, "vertical origin motion survives perspective and large world coordinates");
                            near(screenOrigin.z, -0.95, 1E-5, "moving origin stays in front of the near clipping plane");
                            int join = HeldPredictionView.STEM_SEGMENTS;
                            for (int i = 0; i <= join; i++) {
                                Vec3d projected = project(forward, camera, point(view, i));
                                check(Double.isFinite(projected.x) && Double.isFinite(projected.y)
                                                && projected.z >= -1 && projected.z <= 1,
                                        "all connector samples remain finite and inside the depth clip range");
                            }
                            Vec3d a = project(forward, camera, point(view, join - 1));
                            Vec3d b = project(forward, camera, point(view, join));
                            Vec3d c = project(forward, camera, point(view, join + 1));
                            Vec3d chord = new Vec3d(b.x - screenOrigin.x, b.y - screenOrigin.y, 0);
                            Vec3d tangent = new Vec3d(c.x - b.x, c.y - b.y, 0);
                            if (tangentFits(chord, tangent)) {
                                compatibleJoins++;
                                check(screenCos(b.subtract(a), tangent) > 0.99,
                                        "compatible displayed connectors meet the physical arc smoothly on screen");
                            }
                            boundedStem(view, forward, camera, "real projectile, fov=" + fov + " pitch=" + pitch);
                            for (int i = 1; i < join; i++) {
                                a = project(forward, camera, point(view, i - 1));
                                b = project(forward, camera, point(view, i));
                                c = project(forward, camera, point(view, i + 1));
                                Vec3d incoming = b.subtract(a), outgoing = c.subtract(b);
                                // Ignore subpixel differences dominated by float projection rounding.
                                if (screenLengthSquared(incoming) < 1E-12 || screenLengthSquared(outgoing) < 1E-12) continue;
                                check(screenCos(incoming, outgoing) > 0.8,
                                        "perspective does not turn a smooth world-space stem into a visible sharp hook: fov="
                                                + fov + " pitch=" + pitch + " offset=" + offset[0] + "," + offset[1] + " point=" + i
                                                + " cosine=" + screenCos(b.subtract(a), c.subtract(b)));
                            }
                            vector(view.impact(), physical.impact(), 0, "perspective smoothing never changes the physical impact");
                            for (int i = HeldPredictionView.STEM_TICKS; i < path.size(); i++) {
                                vector(point(view, arcIndex(i)), path.get(i), 0,
                                        "bounded projection preserves every vertex of the physical tail");
                            }
                        }
                    }
                }
            }
        }
        check(compatibleJoins > 0, "projection checks still exercise exact ballistic tangent matching");
    }

    private static void thirdPersonOcclusion() {
        Vec3d launch = ProjectilePhysics.launchPosition(new Vec3d(0, 1.62, 0));
        Vec3d motion = new Vec3d(0, 0, -1.5);
        List<Vec3d> path = airPath(launch, motion, ProjectilePhysics.Profile.THROWN, 30);
        Predictions.TrajectoryResult physical = new Predictions.TrajectoryResult(path, path.getLast(),
                HitResult.Type.MISS, Direction.UP, null);
        HeldPredictionView view = new HeldPredictionView();
        update(view, 1, 0, physical);
        view.prepare(1);
        view.setWorldLaunchOrigin(launch);
        check(view.size() == path.size(), "third person has no near-camera screen-space connector");
        vector(point(view, 0), launch, 0, "third-person line begins at the real player launch, not in front of the camera");
        vector(view.impact(), physical.impact(), 0, "player occlusion never changes the actual predicted impact");
        Box playerBody = new Box(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        Vec3d camera = new Vec3d(0, 0.5, 4);
        for (int i = 0; i <= 2; i++) {
            Vec3d target = point(view, i);
            Vec3d front = playerBody.raycast(camera, target).orElseThrow();
            check(front.squaredDistanceTo(camera) < target.squaredDistanceTo(camera),
                    "the line behind the player has a farther depth than the visible body/cape surface");
        }
        for (int i = HeldPredictionView.STEM_TICKS; i < path.size(); i++) {
            vector(point(view, i), path.get(i), 0, "third-person world-space anchoring leaves the ballistic tail unchanged");
        }

        Predictions.TrajectoryResult moved = new Predictions.TrajectoryResult(
                airPath(launch.add(0.3, 0, 0), motion, ProjectilePhysics.Profile.THROWN, 30),
                physical.impact().add(0.3, 0, 0), HitResult.Type.MISS, Direction.UP, null);
        update(view, 2, 0, moved);
        for (int frame = 0; frame <= 240; frame++) {
            float delta = frame / 240.0F;
            Vec3d visualLaunch = launch.add(0.3 * delta, 0, 0);
            view.prepare(delta);
            view.setWorldLaunchOrigin(visualLaunch);
            vector(point(view, 0), visualLaunch, 0, "third-person world origin follows the interpolated player smoothly");
            vector(view.impact(), physical.impact().lerp(moved.impact(), delta), 1E-10,
                    "occluding the player retains cached per-frame impact interpolation");
        }
    }

    private static void smoothMovement() {
        for (int fps : new int[]{30, 60, 144, 240}) {
            HeldPredictionView view = new HeldPredictionView();
            Predictions.TrajectoryResult old = trajectory(0, 30, 0);
            update(view, 100, 0, old);
            Predictions.TrajectoryResult current = trajectory(0.3, 30, 0);
            update(view, 101, 0, current);
            for (int frame = 0; frame <= fps; frame++) {
                float delta = (float) frame / fps;
                Vec3d launch = old.path().getFirst().lerp(current.path().getFirst(), delta);
                Vec3d anchor = launch.add(0, 0.1, 0.075);
                prepare(view, delta, anchor);
                near(view.impact().x, old.impact().x + (current.impact().x - old.impact().x) * delta,
                        1E-10, "impact interpolates per frame, not in 20 Hz steps at " + fps + " FPS");
                near(view.x(arcIndex(10)), old.path().get(10).x + 0.3 * delta, 1E-10,
                        "entire arc moves smoothly, not only its first point");
                vector(point(view, 0), anchor, 0, "start uses the exact current visual origin");
                vector(new Vec3d(view.x(view.size() - 1), view.y(view.size() - 1), view.z(view.size() - 1)),
                        view.impact(), 0, "marker and line endpoint share the same interpolated point");
            }
            // The next tick's zero-delta frame is the same position as the previous tick's final frame.
            Vec3d boundary = view.impact();
            Predictions.TrajectoryResult next = trajectory(0.6, 30, 0);
            update(view, 102, 0, next);
            prepare(view, 0, current.path().getFirst());
            vector(view.impact(), boundary, 0, "no discontinuity at a tick boundary");
        }
    }

    private static void immediateAppearanceAndAim() {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult first = trajectory(0, 30, 0);
        update(view, 1, 0, first);
        prepare(view, 0, first.path().getFirst());
        vector(view.impact(), first.impact(), 0, "first appearance is immediate, with no fade/easing");
        Predictions.TrajectoryResult aimed = trajectory(2, 30, 0);
        update(view, 1, 25, aimed);
        prepare(view, 0.1F, aimed.path().getFirst());
        vector(view.impact(), aimed.impact(), 0, "mouse aim within a tick has no smoothing delay");
        check(view.result() == aimed, "visual interpolation never rewrites the physical prediction");
        view.reset();
        update(view, 2, 25, first);
        prepare(view, 0, first.path().getFirst());
        vector(view.impact(), first.impact(), 0, "re-enabling has no animation from a stale path");

        Predictions.TrajectoryResult shortPath = new Predictions.TrajectoryResult(
                List.of(new Vec3d(0, 1, 0), new Vec3d(0, 0.9, 0.2)), new Vec3d(0, 0.9, 0.2),
                HitResult.Type.BLOCK, Direction.UP, null);
        update(view, 3, 25, shortPath);
        prepare(view, 0.5F, new Vec3d(0.2, 1.2, 0.2));
        vector(view.impact(), shortPath.impact(), 0, "visual stem never shifts a one-tick collision");
    }

    private static void discontinuities() {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult first = trajectory(0, 30, 0);
        update(view, 1, 0, first);
        Predictions.TrajectoryResult teleport = trajectory(20, 30, 0);
        update(view, 2, 0, teleport);
        prepare(view, 0, teleport.path().getFirst());
        vector(view.impact(), teleport.impact(), 0, "teleports do not draw a phantom interpolated arc");
        Predictions.TrajectoryResult otherFloor = trajectory(20.3, 30, 1);
        update(view, 3, 0, otherFloor);
        prepare(view, 0, otherFloor.path().getFirst());
        vector(view.impact(), otherFloor.impact(), 0, "marker cannot interpolate between different floor heights");
        update(view, 7, 0, first);
        prepare(view, 0, first.path().getFirst());
        vector(view.impact(), first.impact(), 0, "skipped ticks reset stale interpolation");
        view.update(new Object(), OWNER, Predictions.HeldType.SNOWBALL, 1, 0, 8, 0, 0, teleport);
        prepare(view, 0, teleport.path().getFirst());
        vector(view.impact(), teleport.impact(), 0, "world changes reset interpolation");
    }

    private static void movingWhileAiming() {
        HeldLaunchState launch = new HeldLaunchState();
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 100, 0, 0, Vec3d.ZERO, true, 1.5);
        check(!launch.isAiming(), "first appearance does not enable an expensive per-frame simulation");
        Vec3d jumping = new Vec3d(0.3, 0.42, -0.2);
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 101, 15, 0, jumping, false, 1.5);
        check(launch.isAiming(), "mouse input uses immediate smoothly sampled launch physics");
        for (int frame = 0; frame <= 240; frame++) {
            float delta = (float) frame / 240;
            vector(launch.movement(delta), jumping.multiply(delta), 1E-10,
                    "movement remains smooth while the mouse is turning");
            near(launch.speed(delta), 1.5, 0, "fixed throw speed has no fade-in ramp");
        }
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 101, 20, 0,
                new Vec3d(9, 9, 9), false, 1.5);
        vector(launch.movement(0.5F), jumping.multiply(0.5), 1E-10,
                "multiple render frames never overwrite tick-sampled movement history");
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 102, 20, 0,
                new Vec3d(0.4, -0.08, -0.2), true, 2.5);
        near(launch.movement(0.5F).y, 0.21, 1E-10,
                "jump/landing inherited vertical speed transitions smoothly");
        near(launch.speed(0.5F), 2.0, 0, "charged weapon speed changes are interpolated while aiming");
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 103, 20, 0,
                new Vec3d(0.4, -0.08, -0.2), true, 2.5);
        check(!launch.isAiming(), "steady aim returns to once-per-tick prediction caching");
        launch.reset();
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 104, 20, 0, jumping, false, 2.5);
        vector(launch.movement(0), jumping, 0, "reappearance does not ease movement from zero");
        near(launch.speed(0), 2.5, 0, "reappearance does not fade launch speed from zero");

        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult old = trajectory(0, 30, 0);
        Predictions.TrajectoryResult sampled = trajectory(0.15, 30, 0);
        update(view, 1, 0, old);
        update(view, 2, 0, sampled);
        view.useImmediateResult();
        prepare(view, 0.1F, sampled.path().getFirst());
        vector(view.impact(), sampled.impact(), 0, "already sampled physics is not interpolated a second time");
    }

    private static void steppedSurfaces() {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult lower = trajectory(0, 30, 0);
        Predictions.TrajectoryResult upper = trajectory(0.3, 30, 1);
        update(view, 100, 0, lower);
        check(view.needsSurfaceSampling(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 101, upper),
                "a stair-height transition enables precise frame sampling instead of a 20 Hz snap");
        check(!view.needsSurfaceSampling(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 101, trajectory(0.3, 30, 0)),
                "a stable surface keeps inexpensive tick-cached rendering");
        check(!view.needsSurfaceSampling(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 101, trajectory(20, 30, 1)),
                "teleports reset rather than smoothing an invalid transition");
        HeldLaunchState launch = new HeldLaunchState();
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 100, 0, 0, Vec3d.ZERO, true, 1.5);
        launch.sampleSurfaceTransition();
        check(launch.needsFrameSampling() && !launch.isAiming(), "stairs sample smoothly without requiring mouse movement");
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 101, 0, 0, new Vec3d(0.3, 0, 0), true, 1.5);
        for (int frame = 0; frame <= 240; frame++) {
            float delta = frame / 240.0F;
            vector(launch.movement(delta), new Vec3d(0.3 * delta, 0, 0), 1E-10,
                    "precise stair-transition physics uses continuous inherited motion");
        }
        check(launch.needsFrameSampling(), "surface sampling persists across a tick boundary");
        launch.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 102, 0, 0, new Vec3d(0.3, 0, 0), true, 1.5);
        check(!launch.needsFrameSampling(), "flat settled terrain returns to prediction caching");
    }

    private static void changingPathLength() {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult old = trajectory(0, 24, 0);
        Predictions.TrajectoryResult current = trajectory(0.3, 30, 0);
        update(view, 1, 0, old);
        update(view, 2, 0, current);
        for (float delta : new float[]{0, 0.1F, 0.5F, 0.9F, 1}) {
            Vec3d launch = old.path().getFirst().lerp(current.path().getFirst(), delta);
            prepare(view, delta, launch);
            check(view.size() == HeldPredictionView.STEM_SEGMENTS + 30 - HeldPredictionView.STEM_TICKS,
                    "different flight durations share a stable vertex count including the connector");
            vector(view.impact(), old.impact().lerp(current.impact(), delta), 1E-10,
                    "unequal path lengths retain an exact endpoint");
            for (int i = 0; i < view.size(); i++) {
                check(Double.isFinite(view.x(i)) && Double.isFinite(view.y(i)) && Double.isFinite(view.z(i)),
                        "resampled trajectory remains finite");
            }
        }
    }

    private static void bufferReuse() throws Exception {
        HeldPredictionView view = new HeldPredictionView();
        Predictions.TrajectoryResult first = trajectory(0, 301, 0);
        update(view, 1, 0, first);
        prepare(view, 1, first.path().getFirst());
        Field field = HeldPredictionView.class.getDeclaredField("coordinates");
        field.setAccessible(true);
        Object storage = field.get(view);
        Field physicalField = HeldPredictionView.class.getDeclaredField("physicalCoordinates");
        physicalField.setAccessible(true);
        Object physicalStorage = physicalField.get(view);
        Vec3d camera = new Vec3d(0, 1.6, 0);
        Matrix4f forward = new Matrix4f().perspective((float) Math.toRadians(70), 16.0F / 9, 0.05F, 1024)
                .rotateY((float) Math.PI);
        Matrix4f inverse = new Matrix4f(forward).invert();
        Vector3f relative = new Vector3f();
        for (int frame = 0; frame < 1000; frame++) {
            view.prepare((float) (frame % 240) / 240);
            inverse.transformProject(relative.set(frame * 0.0005F, 0, -0.95F));
            view.setLineOrigin(camera.add(relative.x, relative.y, relative.z), forward, inverse, camera);
            check(field.get(view) == storage, "rendering reuses the primitive coordinate buffer");
            check(physicalField.get(view) == physicalStorage, "stem rendering also reuses the unchanged physical-coordinate buffer");
            check(view.result() == first, "render frames do not allocate/recompute a trajectory");
        }
    }

    private static Predictions.TrajectoryResult trajectory(double x, int points, double floor) {
        List<Vec3d> path = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double progress = (double) i / (points - 1);
            path.add(new Vec3d(x + progress * 2, floor + (1 - progress) * 1.5,
                    progress * 15));
        }
        return new Predictions.TrajectoryResult(path, path.getLast(), HitResult.Type.BLOCK, Direction.UP, null);
    }

    private static List<Vec3d> airPath(Vec3d launch, Vec3d motion, ProjectilePhysics.Profile profile, int ticks) {
        List<Vec3d> path = new ArrayList<>();
        ProjectilePhysics.simulate(launch, motion, profile, profile.gravity, profile.size, profile.size,
                false, new ProjectilePhysics.Environment() {
                    @Override public BlockHitResult blockCollision(Vec3d start, Vec3d end) {
                        return BlockHitResult.createMissed(end, Direction.UP, BlockPos.ORIGIN);
                    }
                    @Override public HitResult entityCollision(Vec3d start, Vec3d end, Box bounds) { return null; }
                    @Override public ProjectilePhysics.FluidMotion fluids(Box bounds, Vec3d velocity) {
                        return new ProjectilePhysics.FluidMotion(false, velocity);
                    }
                }, path, ticks, -1000);
        return path;
    }

    private static Vec3d project(Matrix4f matrix, Vec3d camera, Vec3d point) {
        Vector3f projected = matrix.transformProject(point.subtract(camera).toVector3f());
        return new Vec3d(projected.x, projected.y, projected.z);
    }

    private static Vec3d unproject(Matrix4f inverse, Vec3d camera, float x, float y, float z) {
        Vector3f relative = inverse.transformProject(new Vector3f(x, y, z));
        return camera.add(relative.x, relative.y, relative.z);
    }

    private static double screenCos(Vec3d a, Vec3d b) {
        return (a.x * b.x + a.y * b.y) / Math.sqrt((a.x * a.x + a.y * a.y) * (b.x * b.x + b.y * b.y));
    }

    private static double screenLengthSquared(Vec3d vector) {
        return vector.x * vector.x + vector.y * vector.y;
    }

    private static boolean tangentFits(Vec3d chord, Vec3d tangent) {
        double length = tangent.length();
        if (length < 1E-7) return false;
        Vec3d handle = tangent.multiply(chord.length() / (3 * length));
        return handleFits(handle.x, chord.x) && handleFits(handle.y, chord.y) && handleFits(handle.z, chord.z);
    }

    private static boolean handleFits(double handle, double span) {
        return handle >= Math.min(0, span * 2 / 3) && handle <= Math.max(0, span * 2 / 3);
    }

    private static void update(HeldPredictionView view, long tick, float yaw, Predictions.TrajectoryResult result) {
        view.update(WORLD, OWNER, Predictions.HeldType.SNOWBALL, 1, 0, tick, yaw, 0, result);
    }

    private static void prepare(HeldPredictionView view, float delta, Vec3d origin) {
        view.prepare(delta);
        view.setLineOrigin(origin);
    }

    private static Vec3d point(HeldPredictionView view, int index) {
        return new Vec3d(view.x(index), view.y(index), view.z(index));
    }

    private static int arcIndex(int physicalIndex) {
        return HeldPredictionView.STEM_SEGMENTS + physicalIndex - HeldPredictionView.STEM_TICKS;
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
