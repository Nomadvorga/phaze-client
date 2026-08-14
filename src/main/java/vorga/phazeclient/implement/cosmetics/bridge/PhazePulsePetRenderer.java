package vorga.phazeclient.implement.cosmetics.bridge;

import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseGeoRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.implement.cosmetics.CosmeticsSyncService;
import vorga.phazeclient.implement.features.modules.client.Theme;

/** Camera-relative world pass for remote Pulse pets. */
public final class PhazePulsePetRenderer {
    private static final long START_NANOS = System.nanoTime();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null
                    || !Theme.getInstance().renderOtherPlayerCosmetics.isValue()) return;
            for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                if (player != client.player) render(ctx, client, player);
            }
        });
    }

    private static void render(WorldRenderContext ctx,
                               MinecraftClient client, AbstractClientPlayerEntity player) {
        CosmeticEntry entry = CosmeticsSyncService.getInstance()
                .pulseEntryFor(player.getUuid(), CosmeticCategory.PET);
        if (entry == null || entry.kind() != CosmeticKind.GEOMETRY) return;
        ParsedCosmeticModel model = CosmeticModelLoader.get(entry);
        if (model == null) return;
        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        Vec3d pos = player.getLerpedPos(tickDelta);
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = ctx.matrices();
        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getLerpedYaw(tickDelta)));
        matrices.translate(model.globalOffset.x(), player.getHeight() - model.globalOffset.y(), model.globalOffset.z());
        if (entry.id() == 132) matrices.translate(0.0, -2.0, 0.0);
        PulseGeoRenderer.renderPet(entry, matrices, ctx.consumers(), 0xF000F0,
                player.isGliding(), player.isSwimming(), player.isSneaking(),
                PetMovementTracker.isMoving(player),
                (float) ((System.nanoTime() - START_NANOS) / 1_000_000_000.0));
        matrices.pop();
    }

    private PhazePulsePetRenderer() { }
}
