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
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * 1:1 Clean-room WaveyCapes cloth renderer.
 * Renders 16 interconnected segments driven by Verlet physics simulation.
 */
public final class WavyCapeRenderer {
    private static final int SEGMENTS = CapePhysicsSimulation.PART_COUNT;
    private static final float CAPE_WIDTH = 10.0f / 16.0f; // 0.625 blocks
    private static final float CAPE_DEPTH = 0.02f; // thin cloth sheet
    private static final float SEGMENT_HEIGHT = CapePhysicsSimulation.SEGMENT_LENGTH;

    private static final Int2ObjectOpenHashMap<CapePhysicsSimulation> SIMULATIONS = new Int2ObjectOpenHashMap<>();
    private static long lastPurge = 0;

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

        CapePhysicsSimulation sim = SIMULATIONS.computeIfAbsent(state.id, id -> new CapePhysicsSimulation());

        // Derive movement velocity from entity if available
        float forwardVel = 0.0f;
        float verticalVel = 0.0f;
        float strafeVel = 0.0f;
        boolean underwater = false;

        Entity entity = client.world.getEntityById(state.id);
        if (entity != null) {
            forwardVel = (float) entity.getVelocity().horizontalLength();
            verticalVel = (float) entity.getVelocity().y;
            underwater = entity.isSubmergedInWater();
        }

        sim.update(
                forwardVel,
                verticalVel,
                strafeVel,
                module.gravity.getValue(),
                module.waveSpeed.getValue(),
                module.waveStrength.getValue(),
                state.sneaking,
                underwater
        );

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(texture));

        matrices.push();
        // Vanilla collar attachment:
        matrices.translate(0.0D, 0.0D, 0.125D);
        if (state.sneaking) {
            matrices.translate(0.0F, 0.1F, 0.05F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20.0F));
        }

        float halfW = CAPE_WIDTH / 2.0f;
        float halfD = CAPE_DEPTH / 2.0f;
        float h = SEGMENT_HEIGHT;
        float delta = client.getRenderTickCounter().getTickDelta(false);

        int pushed = 0;
        try {
            for (int i = 0; i < SEGMENTS; i++) {
                float segPitch = sim.getSegmentPitch(i, delta);
                float segRoll = sim.getSegmentRoll(i, delta);

                matrices.push();
                pushed++;

                // Apply segment position and rotational bending
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(segPitch));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(segRoll));

                drawSegment(matrices, consumer, light, i, halfW, halfD, h);

                matrices.translate(0.0F, h, 0.0F);
            }
        } finally {
            while (pushed > 0) {
                matrices.pop();
                pushed--;
            }
            matrices.pop(); // pop collar
        }
    }

    private static void drawSegment(
            MatrixStack matrices,
            VertexConsumer consumer,
            int light,
            int segmentIndex,
            float halfW,
            float halfD,
            float h
    ) {
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float x0 = -halfW;
        float x1 = halfW;
        float y0 = 0.0f;
        float y1 = h;
        float z0 = -halfD;
        float z1 = halfD;

        float invTexW = 1.0f / 64.0f;
        float invTexH = 1.0f / 32.0f;

        float v0 = (1.0f + segmentIndex) * invTexH;
        float v1 = (1.0f + segmentIndex + 1.0f) * invTexH;

        // Front face (inward towards player body, u: 1..11)
        float uFront0 = 1.0f * invTexW;
        float uFront1 = 11.0f * invTexW;
        drawQuad(consumer, mat, matrices, light,
                x0, y0, z1, uFront1, v0,
                x1, y0, z1, uFront0, v0,
                x1, y1, z1, uFront0, v1,
                x0, y1, z1, uFront1, v1,
                0, 0, 1);

        // Back face (outward facing cape pattern, u: 12..22)
        float uBack0 = 12.0f * invTexW;
        float uBack1 = 22.0f * invTexW;
        drawQuad(consumer, mat, matrices, light,
                x1, y0, z0, uBack1, v0,
                x0, y0, z0, uBack0, v0,
                x0, y1, z0, uBack0, v1,
                x1, y1, z0, uBack1, v1,
                0, 0, -1);

        // Left edge (u: 0..1)
        float uLeft0 = 0.0f * invTexW;
        float uLeft1 = 1.0f * invTexW;
        drawQuad(consumer, mat, matrices, light,
                x0, y0, z0, uLeft0, v0,
                x0, y0, z1, uLeft1, v0,
                x0, y1, z1, uLeft1, v1,
                x0, y1, z0, uLeft0, v1,
                -1, 0, 0);

        // Right edge (u: 11..12)
        float uRight0 = 11.0f * invTexW;
        float uRight1 = 12.0f * invTexW;
        drawQuad(consumer, mat, matrices, light,
                x1, y0, z1, uRight0, v0,
                x1, y0, z0, uRight1, v0,
                x1, y1, z0, uRight1, v1,
                x1, y1, z1, uRight0, v1,
                1, 0, 0);

        // Top face (only segment 0: u: 1..11, v: 0..1)
        if (segmentIndex == 0) {
            float uTop0 = 1.0f * invTexW;
            float uTop1 = 11.0f * invTexW;
            float vTop0 = 0.0f * invTexH;
            float vTop1 = 1.0f * invTexH;
            drawQuad(consumer, mat, matrices, light,
                    x0, y0, z0, uTop0, vTop0,
                    x1, y0, z0, uTop1, vTop0,
                    x1, y0, z1, uTop1, vTop1,
                    x0, y0, z1, uTop0, vTop1,
                    0, -1, 0);
        }

        // Bottom face (only last segment: u: 11..21, v: 0..1)
        if (segmentIndex == SEGMENTS - 1) {
            float uBot0 = 11.0f * invTexW;
            float uBot1 = 21.0f * invTexW;
            float vBot0 = 0.0f * invTexH;
            float vBot1 = 1.0f * invTexH;
            drawQuad(consumer, mat, matrices, light,
                    x0, y1, z1, uBot0, vBot0,
                    x1, y1, z1, uBot1, vBot0,
                    x1, y1, z0, uBot1, vBot1,
                    x0, y1, z0, uBot0, vBot1,
                    0, 1, 0);
        }
    }

    private static void drawQuad(
            VertexConsumer consumer,
            Matrix4f mat,
            MatrixStack matrices,
            int light,
            float ax, float ay, float az, float au, float av,
            float bx, float by, float bz, float bu, float bv,
            float cx, float cy, float cz, float cu, float cv,
            float dx, float dy, float dz, float du, float dv,
            float nx, float ny, float nz
    ) {
        vertex(consumer, mat, matrices, ax, ay, az, au, av, light, nx, ny, nz);
        vertex(consumer, mat, matrices, bx, by, bz, bu, bv, light, nx, ny, nz);
        vertex(consumer, mat, matrices, cx, cy, cz, cu, cv, light, nx, ny, nz);
        vertex(consumer, mat, matrices, dx, dy, dz, du, dv, light, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f mat,
            MatrixStack matrices,
            float x, float y, float z,
            float u, float v,
            int light,
            float nx, float ny, float nz
    ) {
        consumer.vertex(mat, x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(matrices.peek(), nx, ny, nz);
    }
}
