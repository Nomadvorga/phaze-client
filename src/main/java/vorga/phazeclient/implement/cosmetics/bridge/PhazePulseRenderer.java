package vorga.phazeclient.implement.cosmetics.bridge;

import vorga.phazeclient.implement.cosmetics.bridge.pulserender.PulseGeoRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import vorga.phazeclient.implement.cosmetics.CosmeticsSyncService;
import vorga.phazeclient.implement.features.modules.client.Theme;

/** Renders remote Pulse states inside Phaze's existing live player matrices. */
public final class PhazePulseRenderer {
    private static final long START_NANOS = System.nanoTime();

    public static void renderBody(MatrixStack matrices, VertexConsumerProvider providers,
                                  PlayerEntityRenderState state, int light) {
        AbstractClientPlayerEntity player = remotePlayer(state.id);
        if (player == null || state.invisible || state.spectator) return;
        float time = time();
        boolean moving = player.getVelocity().horizontalLengthSquared() > 0.0015;

        renderGeometry(entry(player, CosmeticCategory.BODYWEAR), CosmeticCategory.BODYWEAR,
                matrices, providers, light, state, moving, time);
        renderGeometry(entry(player, CosmeticCategory.WINGS), CosmeticCategory.WINGS,
                matrices, providers, light, state, moving, time);
    }

    public static void renderHead(MatrixStack matrices, VertexConsumerProvider providers,
                                  PlayerEntityRenderState state, int light) {
        AbstractClientPlayerEntity player = remotePlayer(state.id);
        if (player == null || state.invisible || state.spectator) return;
        CosmeticEntry entry = entry(player, CosmeticCategory.HAT);
        if (entry == null || entry.kind() != CosmeticKind.GEOMETRY) return;
        matrices.push();
        switch (entry.id()) {
            case 85, 88, 92, 96 -> { }
            case 48, 49, 50, 53, 57 -> matrices.translate(0.0, -0.24, 0.0);
            case 80 -> matrices.translate(0.0, 0.06, 0.0);
            default -> matrices.translate(0.0, -0.15, 0.0);
        }
        PulseGeoRenderer.render(entry, matrices, providers, light,
                state.isGliding, state.isSwimming, state.isInSneakingPose,
                player.getVelocity().horizontalLengthSquared() > 0.0015, time());
        matrices.pop();
    }

    private static void renderGeometry(CosmeticEntry entry, CosmeticCategory category,
                                       MatrixStack matrices, VertexConsumerProvider providers,
                                       int light, PlayerEntityRenderState state,
                                       boolean moving, float time) {
        if (entry == null || entry.kind() != CosmeticKind.GEOMETRY) return;
        matrices.push();
        if (category == CosmeticCategory.BODYWEAR) matrices.translate(0.0, 0.25, 0.0);
        else if (category == CosmeticCategory.WINGS) matrices.translate(0.0, 0.27, 0.0);
        PulseGeoRenderer.render(entry, matrices, providers, light,
                state.isGliding, state.isSwimming, state.isInSneakingPose, moving, time);
        matrices.pop();
    }

    private static CosmeticEntry entry(AbstractClientPlayerEntity player, CosmeticCategory category) {
        return CosmeticsSyncService.getInstance().pulseEntryFor(player.getUuid(), category);
    }

    private static AbstractClientPlayerEntity remotePlayer(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null
                || !Theme.getInstance().renderOtherPlayerCosmetics.isValue()) return null;
        Entity entity = client.world.getEntityById(entityId);
        if (!(entity instanceof AbstractClientPlayerEntity player) || player == client.player) return null;
        return player;
    }

    private static float time() {
        return (float) ((System.nanoTime() - START_NANOS) / 1_000_000_000.0);
    }

    private PhazePulseRenderer() { }
}
