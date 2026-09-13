package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.FTHelper;
import vorga.phazeclient.implement.features.modules.other.FTHelperRenderer;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.features.modules.other.HolyWorldHelper;
import vorga.phazeclient.implement.features.modules.other.HolyWorldHelperRenderer;
import vorga.phazeclient.implement.features.modules.other.Predictions;
import vorga.phazeclient.implement.features.modules.other.PredictionsRenderer;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererOverlaysMixin {

    @Unique
    private boolean phaze$overlaysPending;

    @Inject(method = "pushEntityRenders", at = @At("TAIL"), require = 0)
    private void phaze$armWorldOverlays(MatrixStack matrices,
                                        WorldRenderState worldRenderState,
                                        OrderedRenderCommandQueue queue,
                                        CallbackInfo ci) {
        phaze$overlaysPending = true;
    }

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), require = 0)
    private void phaze$drawWorldOverlays(VertexConsumerProvider.Immediate vertexConsumers,
                                         MatrixStack matrices,
                                         boolean translucent,
                                         WorldRenderState worldRenderState,
                                         CallbackInfo ci) {
        if (!phaze$overlaysPending) {
            return;
        }
        phaze$overlaysPending = false;
        FTHelper ftHelper = FTHelper.getInstance();
        HolyWorldHelper holyWorldHelper = HolyWorldHelper.getInstance();
        Predictions predictions = Predictions.getInstance();
        HitRange hitRange = HitRange.getInstance();

        boolean anyOverlay = (ftHelper != null && ftHelper.isEnabled())
                || (holyWorldHelper != null && holyWorldHelper.isEnabled())
                || (predictions != null && predictions.isEnabled());
        boolean selfRing = hitRange != null && hitRange.isEnabled() && hitRange.showSelf.isValue();
        if (!anyOverlay && !selfRing) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.gameRenderer == null) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return;
        }
        RenderTickCounter tickCounter = client.getRenderTickCounter();
        if (tickCounter == null) {
            return;
        }

        VertexConsumerProvider.Immediate immediate = vertexConsumers != null
                ? vertexConsumers
                : client.getBufferBuilders().getEntityVertexConsumers();
        immediate.draw();

        Vec3d cameraPos = worldRenderState.cameraRenderState.pos;

        if (anyOverlay) {
            FTHelperRenderer.renderHighlight(matrices, cameraPos, tickCounter);
            FTHelperRenderer.renderSnowballs(matrices, cameraPos, tickCounter);
            HolyWorldHelperRenderer.render(matrices, cameraPos, tickCounter);
            PredictionsRenderer.render(matrices, cameraPos, tickCounter);
        }

        if (selfRing) {
            phaze$drawSelfHitRange(client, camera, tickCounter, matrices, immediate);
        }
    }

    private static void phaze$drawSelfHitRange(MinecraftClient client,
                                               Camera camera,
                                               RenderTickCounter tickCounter,
                                               MatrixStack matrices,
                                               VertexConsumerProvider.Immediate immediate) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getEntityRenderDispatcher() == null) {
            return;
        }

        float tickDelta = tickCounter.getTickProgress(false);
        double px = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
        double py = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
        double pz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
        Vec3d playerPos = new Vec3d(px, py, pz).subtract(camera.getCameraPos());

        EntityRenderer<? super ClientPlayerEntity, ?> renderer =
                client.getEntityRenderDispatcher().getRenderer(player);
        if (renderer == null) {
            return;
        }
        Object state = renderer.getAndUpdateRenderState(player, tickDelta);
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        matrices.push();
        matrices.translate(playerPos.x, playerPos.y, playerPos.z);
        HitRangeCircleRenderer.drawCircle(matrices, immediate, playerState);
        matrices.pop();
    }
}
