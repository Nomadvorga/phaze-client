package vorga.phazeclient.implement.cosmetics.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import vorga.phazeclient.implement.cosmetics.CosmeticsSyncService;
import vorga.phazeclient.implement.features.modules.client.Theme;

/** Renders shared graffiti received over the existing Phaze event stream. */
public final class PhazePulseGraffitiRenderer {
    private static final double MAX_DISTANCE_SQ = 96.0 * 96.0;
    private static final int MAX_PER_FRAME = 512;
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null
                    || !Theme.getInstance().renderOtherPlayerCosmetics.isValue()) return;
            String serverKey = SharedWorldKey.get(client);
            String dimension = SharedWorldKey.dimension(client);
            if (serverKey == null || dimension == null) return;

            var cameraPos = client.gameRenderer.getCamera().getPos();
            Camera camera = new Camera(cameraPos.x, cameraPos.y, cameraPos.z);
            int rendered = 0;
            for (CosmeticsSyncService.PulseGraffiti graffiti
                    : CosmeticsSyncService.getInstance().pulseGraffiti()) {
                if (!serverKey.equals(graffiti.serverKey())
                        || !dimension.equals(graffiti.dimension())) continue;
                BlockPos pos = graffiti.pos();
                if (pos.toCenterPos().squaredDistanceTo(client.player.getPos()) > MAX_DISTANCE_SQ) continue;
                CosmeticEntry entry = CosmeticCatalog.byId(graffiti.graffitiId());
                if (entry == null || entry.category() != CosmeticCategory.GRAFFITI) continue;
                render(entry, pos, graffiti.face(), ctx.matrixStack(), ctx.consumers(), camera);
                if (++rendered >= MAX_PER_FRAME) break;
            }
        });
    }

    private static void render(CosmeticEntry entry, BlockPos pos, Direction face,
                               MatrixStack matrices, VertexConsumerProvider providers, Camera camera) {
        Identifier texture = entry.textureId();
        if (texture == null) return;
        float aspect = entry.textureWidth() > 0 && entry.textureHeight() > 0
                ? entry.textureWidth() / (float) entry.textureHeight() : 1f;
        float width = aspect >= 1f ? 0.90f : 0.90f * aspect;
        float height = aspect >= 1f ? 0.90f / aspect : 0.90f;
        float cx = pos.getX() + 0.5f, cy = pos.getY() + 0.5f, cz = pos.getZ() + 0.5f;
        float offset = 0.01f;
        float[] u = new float[3], v = new float[]{0f, 1f, 0f};
        switch (face) {
            case NORTH -> { cz -= 0.5f + offset; u[0] = 1f; }
            case SOUTH -> { cz += 0.5f + offset; u[0] = -1f; }
            case EAST -> { cx += 0.5f + offset; u[2] = -1f; }
            case WEST -> { cx -= 0.5f + offset; u[2] = 1f; }
            case UP -> { cy += 0.5f + offset; u[0] = 1f; v = new float[]{0f, 0f, -1f}; }
            case DOWN -> { cy -= 0.5f + offset; u[0] = 1f; v = new float[]{0f, 0f, 1f}; }
        }
        float hu = width / 2f, hv = height / 2f;
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = providers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        RenderSystem.disableCull();
        vertex(consumer, matrix, cx-u[0]*hu-v[0]*hv, cy-u[1]*hu-v[1]*hv, cz-u[2]*hu-v[2]*hv, 0, 1);
        vertex(consumer, matrix, cx+u[0]*hu-v[0]*hv, cy+u[1]*hu-v[1]*hv, cz+u[2]*hu-v[2]*hv, 1, 1);
        vertex(consumer, matrix, cx+u[0]*hu+v[0]*hv, cy+u[1]*hu+v[1]*hv, cz+u[2]*hu+v[2]*hv, 1, 0);
        vertex(consumer, matrix, cx-u[0]*hu+v[0]*hv, cy-u[1]*hu+v[1]*hv, cz-u[2]*hu+v[2]*hv, 0, 0);
        RenderSystem.enableCull();
        matrices.pop();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float x, float y, float z, float u, float v) {
        consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
    }

    private record Camera(double x, double y, double z) { }
    private PhazePulseGraffitiRenderer() { }
}
