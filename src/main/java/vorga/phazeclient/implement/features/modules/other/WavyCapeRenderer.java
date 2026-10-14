package vorga.phazeclient.implement.features.modules.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 1:1 Clean-room continuous cloth ribbon renderer based on WaveyCapes (tr7zw).
 * Replaces stacked box approximations with a smooth continuous mesh ribbon
 * where vertices between adjacent segments share exact transformation matrices.
 */
public final class WavyCapeRenderer {
    public static final int PART_COUNT = CapePhysicsSimulation.PART_COUNT; // 16
    private static final float CAPE_WIDTH = 10.0f / 16.0f; // 0.625 blocks
    private static final float CAPE_HEIGHT = 1.0f;         // 1.0 block
    private static final float CAPE_DEPTH = 1.0f / 16.0f;  // 0.0625 blocks

    // Reusable matrices & vector scratch to guarantee zero allocations during render loop
    private static final Matrix4f[] POSITION_MATRICES = new Matrix4f[PART_COUNT];
    private static final Vector4f V_A = new Vector4f();
    private static final Vector4f V_B = new Vector4f();
    private static final Vector4f V_C = new Vector4f();

    private static final Int2ObjectOpenHashMap<CapePhysicsSimulation> SIMULATIONS = new Int2ObjectOpenHashMap<>();
    private static long lastPurge = 0;

    static {
        for (int i = 0; i < PART_COUNT; i++) {
            POSITION_MATRICES[i] = new Matrix4f();
        }
    }

    private WavyCapeRenderer() {}

    public static void renderWavyCape(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier texture,
            int light,
            PlayerEntityRenderState state
    ) {
        WaveCapes module = WaveCapes.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (state.invisible || !state.capeVisible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPurge > 10000) {
            lastPurge = now;
            if (SIMULATIONS.size() > 64) {
                SIMULATIONS.clear();
            }
        }

        Entity entity = client.world.getEntityById(state.id);
        boolean underwater = entity != null && entity.isSubmergedInWater();
        float delta = client.getRenderTickCounter().getTickDelta(false);

        boolean isPhysics3D = module.mode.isSelected("Physics 3D");

        if (isPhysics3D) {
            CapePhysicsSimulation sim = SIMULATIONS.computeIfAbsent(state.id, id -> new CapePhysicsSimulation());

            float forwardDelta = 0.0f;
            float verticalDelta = 0.0f;
            float strafeDelta = 0.0f;

            if (entity != null) {
                float yawRad = (float) Math.toRadians(-entity.getYaw());
                float dx = (float) (entity.getX() - entity.lastRenderX);
                float dy = (float) (entity.getY() - entity.lastRenderY);
                float dz = (float) (entity.getZ() - entity.lastRenderZ);
                strafeDelta = MathHelper.cos(yawRad) * dx - MathHelper.sin(yawRad) * dz;
                verticalDelta = dy;
                forwardDelta = (float) Math.sqrt(dx * dx + dz * dz) * 10.0f;
            }

            sim.update(
                    forwardDelta,
                    verticalDelta,
                    strafeDelta,
                    module.gravity.getValue(),
                    module.heightMultiplier.getValue(),
                    module.strafeMultiplier.getValue(),
                    state.sneaking,
                    underwater
            );

            // Compute independent pose matrix for each segment
            for (int part = 0; part < PART_COUNT; part++) {
                matrices.push();
                matrices.translate(0.0D, 0.0D, 0.125D);

                float firstX = sim.getLerpX(0, delta);
                float currX = sim.getLerpX(part, delta);
                float x = currX - firstX;
                if (x > 0) x = 0;
                float y = sim.getLerpY(0, delta) - part - sim.getLerpY(part, delta);
                float z = sim.getLerpZ(0, delta) - sim.getLerpZ(part, delta);
                float partRotation = sim.getSegmentRotation(part, delta);

                float naturalWind = 0.0f;
                if (module.windEffect.isValue()) {
                    long waveTime = (now / (underwater ? 9L : 3L)) % 360L;
                    float partFraction = (float) (part + 1) / PART_COUNT;
                    float wavePhase = (float) Math.toRadians(partFraction * 360.0f - waveTime * module.waveSpeed.getValue());
                    naturalWind = (float) (Math.sin(wavePhase) * 3.0f * module.waveStrength.getValue());
                }

                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f + naturalWind));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));

                matrices.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT);
                matrices.translate(0.0f, 0.48f / 16.0f, -0.48f / 16.0f);
                matrices.translate(0.0f, part * 1.0f / PART_COUNT, 0.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-partRotation));
                matrices.translate(0.0f, -part * 1.0f / PART_COUNT, 0.0f);
                matrices.translate(0.0f, -0.48f / 16.0f, 0.48f / 16.0f);

                POSITION_MATRICES[part].set(matrices.peek().getPositionMatrix());
                matrices.pop();
            }
        } else {
            // Smooth Waves Mode (Vanilla curve easing + travelling cloth ripples)
            for (int part = 0; part < PART_COUNT; part++) {
                matrices.push();
                matrices.translate(0.0D, 0.0D, 0.125D);

                float partFraction = (float) (part + 1) / (float) PART_COUNT;
                float ease = (float) Math.sin((partFraction * Math.PI) / 2.0);

                float swing = state.field_53537 * ease;
                swing = MathHelper.clamp(swing, 0.0f, 150.0f * ease);
                float sideways = MathHelper.clamp(state.field_53538 * ease, -20.0f, 20.0f);

                float naturalWind = 0.0f;
                if (module.windEffect.isValue()) {
                    long waveTime = (now / (underwater ? 9L : 3L)) % 360L;
                    float wavePhase = (float) Math.toRadians(partFraction * 360.0f - waveTime * module.waveSpeed.getValue());
                    naturalWind = (float) (Math.sin(wavePhase) * 3.0f * module.waveStrength.getValue());
                }

                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f + swing / 2.0f + state.field_53536 + naturalWind));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sideways / 2.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - sideways / 2.0f));

                POSITION_MATRICES[part].set(matrices.peek().getPositionMatrix());
                matrices.pop();
            }
        }

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        drawSmoothMesh(consumer, light);
    }

    private static void drawSmoothMesh(VertexConsumer consumer, int light) {
        float texelU = 1.0f / 64.0f;
        float texelV = 1.0f / 32.0f;
        float halfW = CAPE_WIDTH / 2.0f;

        for (int part = 0; part < PART_COUNT; part++) {
            int prev = Math.max(part - 1, 0);
            Matrix4f matPrev = POSITION_MATRICES[prev];
            Matrix4f matCurr = POSITION_MATRICES[part];

            float yTop = part * (CAPE_HEIGHT / PART_COUNT);
            float yBot = (part + 1) * (CAPE_HEIGHT / PART_COUNT);

            // 1. Top End Cap (only on part 0)
            if (part == 0) {
                float minU = 1.0f * texelU;
                float maxU = 11.0f * texelU;
                float v0 = 0.0f;
                float v1 = texelV;
                drawQuad(consumer, light,
                        matPrev,  halfW, 0.0f, 0.0f, maxU, v1,
                        matPrev, -halfW, 0.0f, 0.0f, minU, v1,
                        matPrev, -halfW, 0.0f, -CAPE_DEPTH, minU, v0,
                        matPrev,  halfW, 0.0f, -CAPE_DEPTH, maxU, v0,
                        false);
            }

            // 2. Bottom End Cap (only on last segment)
            if (part == PART_COUNT - 1) {
                float minU = 11.0f * texelU;
                float maxU = 21.0f * texelU;
                float v0 = 0.0f;
                float v1 = texelV;
                drawQuad(consumer, light,
                        matCurr,  halfW, CAPE_HEIGHT, -CAPE_DEPTH, maxU, v0,
                        matCurr, -halfW, CAPE_HEIGHT, -CAPE_DEPTH, minU, v0,
                        matCurr, -halfW, CAPE_HEIGHT, 0.0f, minU, v1,
                        matCurr,  halfW, CAPE_HEIGHT, 0.0f, maxU, v1,
                        false);
            }

            // 3. Left Edge (u: 0..1, v: row part + 1)
            float minU = 0.0f;
            float maxU = 1.0f * texelU;
            float minV = texelV * (part + 1);
            float maxV = minV + texelV;
            drawQuad(consumer, light,
                    matCurr, -halfW, yBot, 0.0f, minU, maxV,
                    matCurr, -halfW, yBot, -CAPE_DEPTH, maxU, maxV,
                    matPrev, -halfW, yTop, -CAPE_DEPTH, maxU, minV,
                    matPrev, -halfW, yTop, 0.0f, minU, minV,
                    false);

            // 4. Right Edge (u: 11..12, v: row part + 1)
            minU = 11.0f * texelU;
            maxU = 12.0f * texelU;
            drawQuad(consumer, light,
                    matCurr, halfW, yBot, -CAPE_DEPTH, minU, maxV,
                    matCurr, halfW, yBot, 0.0f, maxU, maxV,
                    matPrev, halfW, yTop, 0.0f, maxU, minV,
                    matPrev, halfW, yTop, -CAPE_DEPTH, minU, minV,
                    false);

            // 5. Front Face (facing player's body at z = -CAPE_DEPTH)
            minU = 1.0f * texelU;
            maxU = 11.0f * texelU;
            drawQuad(consumer, light,
                    matPrev,  halfW, yTop, -CAPE_DEPTH, maxU, minV,
                    matPrev, -halfW, yTop, -CAPE_DEPTH, minU, minV,
                    matCurr, -halfW, yBot, -CAPE_DEPTH, minU, maxV,
                    matCurr,  halfW, yBot, -CAPE_DEPTH, maxU, maxV,
                    false);

            // 6. Back Face (outer visible cape pattern at z = 0)
            minU = 12.0f * texelU;
            maxU = 22.0f * texelU;
            drawQuad(consumer, light,
                    matPrev,  halfW, yTop, 0.0f, minU, minV,
                    matPrev, -halfW, yTop, 0.0f, maxU, minV,
                    matCurr, -halfW, yBot, 0.0f, maxU, maxV,
                    matCurr,  halfW, yBot, 0.0f, minU, maxV,
                    true);
        }
    }

    private static void drawQuad(
            VertexConsumer consumer,
            int light,
            Matrix4f matA, float ax, float ay, float az, float au, float av,
            Matrix4f matB, float bx, float by, float bz, float bu, float bv,
            Matrix4f matC, float cx, float cy, float cz, float cu, float cv,
            Matrix4f matD, float dx, float dy, float dz, float du, float dv,
            boolean invertNormal
    ) {
        matA.transform(ax, ay, az, 1.0f, V_A);
        matB.transform(bx, by, bz, 1.0f, V_B);
        matC.transform(cx, cy, cz, 1.0f, V_C);

        float uX = V_B.x - V_A.x;
        float uY = V_B.y - V_A.y;
        float uZ = V_B.z - V_A.z;

        float wX = V_C.x - V_A.x;
        float wY = V_C.y - V_A.y;
        float wZ = V_C.z - V_A.z;

        float nx = uY * wZ - uZ * wY;
        float ny = uZ * wX - uX * wZ;
        float nz = uX * wY - uY * wX;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-4f) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0f;
            ny = 0.0f;
            nz = 1.0f;
        }

        if (invertNormal) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }

        consumer.vertex(matA, ax, ay, az).color(0xFFFFFFFF).texture(au, av).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        consumer.vertex(matB, bx, by, bz).color(0xFFFFFFFF).texture(bu, bv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        consumer.vertex(matC, cx, cy, cz).color(0xFFFFFFFF).texture(cu, cv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
        consumer.vertex(matD, dx, dy, dz).color(0xFFFFFFFF).texture(du, dv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx, ny, nz);
    }
}
