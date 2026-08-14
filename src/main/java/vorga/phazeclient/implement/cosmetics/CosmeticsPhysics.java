package vorga.phazeclient.implement.cosmetics;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic client-side spring simulation for hierarchical back cosmetics.
 * It consumes player motion only; no packets or server state are required.
 */
final class CosmeticsPhysics {
    private static final CosmeticsPhysics INSTANCE = new CosmeticsPhysics();
    private static final long PREVIEW_EPOCH_NANOS = System.nanoTime();

    /** One tiny spring state per rendered player; never shared across entities. */
    private final Map<Integer, Simulation> simulations = new HashMap<>();
    /** Independent root-position springs for companion pets. */
    private final Map<Integer, CompanionSimulation> companionSimulations = new HashMap<>();

    private CosmeticsPhysics() {
    }

    static CosmeticsPhysics getInstance() {
        return INSTANCE;
    }

    Pose sample(PlayerEntity player, PlayerEntityRenderState state,
                boolean preview, float idleAmplitude) {
        if (preview) {
            // Keep the phase close to zero. Converting the JVM's absolute
            // nano time to float loses enough precision after a long session
            // to make this advance in visible ~5 FPS steps.
            double time = (System.nanoTime() - PREVIEW_EPOCH_NANOS) / 1_000_000_000.0D;
            return new Pose(
                    0.0F, 0.0F,
                    (float) Math.sin(time * 1.7D) * idleAmplitude * 0.38F,
                    0.0F
            );
        }

        long now = System.nanoTime();
        if (simulations.size() > 256) {
            simulations.clear();
        }
        Simulation simulation = simulations.computeIfAbsent(
                player.getId(),
                ignored -> new Simulation(state.bodyYaw, now)
        );

        float dt = simulation.lastUpdateNanos == 0L
                ? 1.0F / 60.0F
                : Math.min(0.05F, Math.max(1.0F / 1000.0F,
                (now - simulation.lastUpdateNanos) / 1_000_000_000.0F));
        simulation.lastUpdateNanos = now;

        Vec3d velocity = player.getVelocity();
        float horizontalSpeed = (float) Math.sqrt(
                velocity.x * velocity.x + velocity.z * velocity.z
        );
        float rawMovement = MathHelper.clamp(horizontalSpeed / 0.28F, 0.0F, 1.65F);
        float sprint = player.isSprinting() ? 1.0F : 0.0F;

        // bodyYaw is already interpolated for this render frame. Sampling the
        // entity's tick yaw here produced a 20 Hz staircase during fast turns.
        float yaw = state.bodyYaw;
        float yawDelta = MathHelper.wrapDegrees(yaw - simulation.lastYaw);
        simulation.lastYaw = yaw;
        float rawTurnLag = MathHelper.clamp(-yawDelta * 0.82F, -13.0F, 13.0F);

        // Velocity is still updated by game ticks. These input springs remove
        // the 20 Hz target steps before they reach the visible wing springs.
        simulation.movementInput.update(rawMovement, dt, 58.0F, 13.0F);
        simulation.verticalInput.update((float) velocity.y, dt, 62.0F, 14.0F);
        simulation.turnInput.update(rawTurnLag, dt, 74.0F, 15.0F);
        float movement = simulation.movementInput.value;
        float vertical = simulation.verticalInput.value;
        float turnLag = simulation.turnInput.value;

        float airbornePitch = 0.0F;
        float airborneFlex = 0.0F;
        if (!player.isOnGround()) {
            if (vertical > 0.0F) {
                airbornePitch = -MathHelper.clamp(vertical * 22.0F, 2.0F, 10.0F);
                airborneFlex = -MathHelper.clamp(vertical * 13.0F, 1.0F, 6.0F);
            } else {
                airbornePitch = MathHelper.clamp(-vertical * 18.0F, 2.0F, 11.0F);
                airborneFlex = MathHelper.clamp(-vertical * 12.0F, 1.0F, 7.0F);
            }
        }

        // Render-state age already contains the interpolated tick age.
        float age = state.age;
        float stepPulse = MathHelper.sin(age * 0.72F) * movement * 1.8F;
        float idle = MathHelper.sin(age * 0.12F) * idleAmplitude;

        simulation.rootPitch.update(-movement * 2.2F - sprint * 6.8F + airbornePitch, dt, 82.0F, 13.0F);
        simulation.rootYaw.update(turnLag, dt, 95.0F, 14.0F);
        simulation.segmentPitch.update(
                idle + movement * 2.4F + sprint * 4.5F + airborneFlex + stepPulse,
                dt, 72.0F, 11.0F
        );
        simulation.segmentYaw.update(turnLag * 0.82F, dt, 86.0F, 12.0F);

        return new Pose(
                simulation.rootPitch.value, simulation.rootYaw.value,
                simulation.segmentPitch.value, simulation.segmentYaw.value
        );
    }

    CompanionPose sampleCompanion(
            PlayerEntity player,
            PlayerEntityRenderState state,
            boolean preview,
            float intensity
    ) {
        double time = (System.nanoTime() - PREVIEW_EPOCH_NANOS)
                / 1_000_000_000.0D;
        if (preview) {
            return new CompanionPose(
                    0.72F,
                    -0.40F + (float) Math.sin(time * 2.1D) * 0.025F,
                    0.08F
            );
        }

        long now = System.nanoTime();
        if (companionSimulations.size() > 256) {
            companionSimulations.clear();
        }
        CompanionSimulation simulation = companionSimulations.computeIfAbsent(
                player.getId(), ignored -> new CompanionSimulation(now)
        );
        float dt = Math.min(0.05F, Math.max(
                1.0F / 1000.0F,
                (now - simulation.lastUpdateNanos) / 1_000_000_000.0F
        ));
        simulation.lastUpdateNanos = now;

        Vec3d velocity = player.getVelocity();
        float yawRadians = (float) Math.toRadians(state.bodyYaw);
        float sin = MathHelper.sin(yawRadians);
        float cos = MathHelper.cos(yawRadians);

        // Convert world velocity to the already-rotated body coordinate
        // system. The target moves opposite to velocity, so the companion
        // visibly trails behind walking, strafing and creative flight.
        float localSide = (float) velocity.x * cos
                + (float) velocity.z * sin;
        float localForward = -(float) velocity.x * sin
                + (float) velocity.z * cos;
        float strength = MathHelper.clamp(intensity, 0.0F, 1.0F);
        float targetX = 0.72F - MathHelper.clamp(
                localSide * 1.25F * strength, -0.34F, 0.34F
        );
        float targetY = -0.40F - MathHelper.clamp(
                (float) velocity.y * 0.72F * strength, -0.34F, 0.34F
        );
        // Positive body-local Z is the player's back. Forward movement must
        // therefore increase Z so the companion visibly trails behind.
        float targetZ = 0.08F + MathHelper.clamp(
                localForward * 1.55F * strength, -0.48F, 0.48F
        );

        // Slightly softer than the wing springs: this gives pets a readable
        // delayed follow without tick-rate stepping and costs three scalar
        // spring updates per rendered pet.
        float stiffness = MathHelper.lerp(strength, 55.0F, 27.0F);
        float damping = MathHelper.lerp(strength, 11.0F, 7.0F);
        simulation.x.update(targetX, dt, stiffness, damping);
        simulation.y.update(targetY, dt, stiffness * 0.88F, damping * 0.94F);
        simulation.z.update(targetZ, dt, stiffness, damping);

        return new CompanionPose(
                simulation.x.value,
                simulation.y.value
                        + (float) Math.sin(time * 2.1D) * 0.025F,
                simulation.z.value
        );
    }

    record Pose(float rootPitch, float rootYaw, float segmentPitch, float segmentYaw) {
    }

    record CompanionPose(float x, float y, float z) {
    }

    private static final class Simulation {
        private final Spring rootPitch = new Spring();
        private final Spring rootYaw = new Spring();
        private final Spring segmentPitch = new Spring();
        private final Spring segmentYaw = new Spring();
        private final Spring movementInput = new Spring();
        private final Spring verticalInput = new Spring();
        private final Spring turnInput = new Spring();
        private long lastUpdateNanos;
        private float lastYaw;

        private Simulation(float lastYaw, long lastUpdateNanos) {
            this.lastYaw = lastYaw;
            this.lastUpdateNanos = lastUpdateNanos;
        }
    }

    private static final class CompanionSimulation {
        private final Spring x = new Spring(0.72F);
        private final Spring y = new Spring(-0.40F);
        private final Spring z = new Spring(0.08F);
        private long lastUpdateNanos;

        private CompanionSimulation(long lastUpdateNanos) {
            this.lastUpdateNanos = lastUpdateNanos;
        }
    }

    private static final class Spring {
        private float value;
        private float velocity;

        private Spring() {
        }

        private Spring(float value) {
            this.value = value;
        }

        void update(float target, float dt, float stiffness, float damping) {
            velocity += (target - value) * stiffness * dt;
            velocity *= (float) Math.exp(-damping * dt);
            value += velocity * dt;
        }

        void reset() {
            value = 0.0F;
            velocity = 0.0F;
        }
    }
}
