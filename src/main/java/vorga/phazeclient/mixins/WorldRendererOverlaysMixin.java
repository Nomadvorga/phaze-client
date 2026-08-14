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

/**
 * Drives the world-space overlays: FT Helper, HolyWorld Helper, Predictions,
 * and Hit Range on the local player.
 *
 * <h3>1.21.11 port</h3>
 *
 * <p>All of these hung off the monolithic {@code WorldRendererMixin}, which is
 * still unported - so although each renderer itself survived the port, nothing
 * called any of them and every one of these features was simply dead.
 *
 * <p>The 1.21.4 hook was {@code renderEntities(MatrixStack, Immediate, Camera,
 * RenderTickCounter, List<Entity>)}. 1.21.11 replaced it with
 * {@code pushEntityRenders(MatrixStack, WorldRenderState, OrderedRenderCommandQueue)},
 * which no longer receives the camera, the tick counter or the immediate
 * provider: entities are queued as render commands now. The three missing
 * values are read off the client instead - same objects vanilla itself
 * passes, just fetched rather than handed over.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererOverlaysMixin {

    /**
     * Set once per frame, consumed by the draw below.
     *
     * <p>{@code pushEntityRenders} runs exactly once per frame, whereas
     * {@code renderTargetBlockOutline} is called more than once; arming here
     * and disarming there keeps the overlays to a single draw per frame without
     * needing a frame counter.
     */
    @Unique
    private boolean phaze$overlaysPending;

    @Inject(method = "pushEntityRenders", at = @At("TAIL"), require = 0)
    private void phaze$armWorldOverlays(MatrixStack matrices,
                                        WorldRenderState worldRenderState,
                                        OrderedRenderCommandQueue queue,
                                        CallbackInfo ci) {
        phaze$overlaysPending = true;
    }

    /**
     * Draws the overlays after entities.
     *
     * <p>They used to run at the TAIL of {@code pushEntityRenders}, which in
     * 1.21.11 only ENQUEUES entity render commands - the entities themselves
     * are drawn later, so anything emitted there ended up underneath them and
     * a prediction marker was hidden behind the mob it pointed at.
     *
     * <p>{@code renderTargetBlockOutline} sits after that dispatch in
     * {@code renderMain}, which is exactly why vanilla's own block outline
     * appears over entities. Riding the same point puts the overlays in the
     * 1.21.4 order again: entities first, overlays on top.
     */
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

        // Drain anything still buffered in the shared immediate provider before
        // the overlays go out, so nothing queued earlier lands on top of them.
        // vertexConsumers is that same provider, passed in by the target.
        VertexConsumerProvider.Immediate immediate = vertexConsumers != null
                ? vertexConsumers
                : client.getBufferBuilders().getEntityVertexConsumers();
        immediate.draw();

        // renderTargetBlockOutline itself uses this immutable camera
        // snapshot. Reading the live Camera object here can be one
        // update ahead of the render state during lateral movement,
        // which makes camera-relative overlays slide sideways.
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

    /**
     * Hit Range on the local player.
     *
     * <p>Kept separate from {@code LivingEntityRendererMixin}'s path, which
     * skips the local player precisely so the two do not stack: that mixin has
     * no camera-relative offset to work with, whereas here the world matrix
     * stack is live and the player's interpolated position can be translated
     * into it directly.
     */
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
