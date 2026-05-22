/*
 * Includes ported logic from the "hitrange" mod by uku3lig (uku),
 * https://github.com/uku3lig/hitrange, MIT License. See per-method
 * comments below for attribution.
 */
package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.render.Render3DUtil;
import vorga.phazeclient.implement.features.modules.other.BlockOverlay;
import vorga.phazeclient.implement.features.modules.other.FTHelperRenderer;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;
import vorga.phazeclient.implement.features.modules.other.PredictionsRenderer;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

import java.util.List;

/**
 * Consolidated mixin for {@link WorldRenderer}, merging:
 * <ul>
 *   <li>WeatherChanger ({@code renderWeather} cancel)</li>
 *   <li>MotionBlur (frame-pair matrix capture on {@code render})</li>
 *   <li>HitRange (self-circle on {@code renderEntities} TAIL) -
 *       ported from <a href="https://github.com/uku3lig/hitrange">uku's
 *       hitrange</a> mod (MIT)</li>
 *   <li>FT helper / Predictions overlays ({@code renderEntities} TAIL)</li>
 *   <li>BlockOverlay (outline tint + filled face draw)</li>
 * </ul>
 * The Sodium-incompatible {@code WorldRendererChunkAnimatorMixin} stays
 * in its own file because PhazeMixinPlugin gates it on the absence of
 * Sodium; merging it here would either disable Phaze's other features
 * under Sodium or fail to apply at all.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    // ---------------------------------------------------------------
    // HitRange shadows
    // ---------------------------------------------------------------

    @Shadow @Final private BufferBuilderStorage bufferBuilders;
    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    // ---------------------------------------------------------------
    // MotionBlur unique state
    // ---------------------------------------------------------------

    @Unique private Matrix4f phaze$prevModelView = new Matrix4f();
    @Unique private Matrix4f phaze$prevProjection = new Matrix4f();
    @Unique private Vector3f phaze$prevCameraPos = new Vector3f();

    // ---------------------------------------------------------------
    // WeatherChanger: cancel weather render when Clear is forced
    // ---------------------------------------------------------------

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void phaze$cancelWeatherIfClear(FrameGraphBuilder builder, Vec3d cameraPos, float tickDelta, Fog fog, CallbackInfo ci) {
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive() && weatherChanger.weatherType.getSelected().equals("Clear")) {
            ci.cancel();
        }
    }

    // ---------------------------------------------------------------
    // MotionBlur: capture matrix pair around the render frame
    // ---------------------------------------------------------------

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$motionBlurSetMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!MotionBlur.getInstance().isEnabled()) return;
        float tickDelta = tickCounter.getTickDelta(true);
        float fov = ((GameRendererAccessor) gameRenderer).invokeGetFov(camera, tickDelta, true);
        MotionBlur.getInstance().shader.setFrameMotionBlur(positionMatrix, phaze$prevModelView,
                gameRenderer.getBasicProjectionMatrix(fov),
                phaze$prevProjection,
                new Vector3f(
                        (float) (camera.getPos().x % 30000f),
                        (float) (camera.getPos().y % 30000f),
                        (float) (camera.getPos().z % 30000f)
                ),
                phaze$prevCameraPos
        );
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$motionBlurSetOldMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!MotionBlur.getInstance().isEnabled()) return;
        phaze$prevModelView = new Matrix4f(positionMatrix);
        float tickDelta = tickCounter.getTickDelta(true);
        float fov = ((GameRendererAccessor) gameRenderer).invokeGetFov(camera, tickDelta, true);
        phaze$prevProjection = new Matrix4f(gameRenderer.getBasicProjectionMatrix(fov));
        phaze$prevCameraPos = new Vector3f(
                (float) (camera.getPos().x % 30000f),
                (float) (camera.getPos().y % 30000f),
                (float) (camera.getPos().z % 30000f)
        );
    }

    // ---------------------------------------------------------------
    // HitRange (self-circle) — ported from uku's hitrange mod (MIT).
    // ---------------------------------------------------------------

    @Inject(method = "renderEntities", at = @At(value = "TAIL"))
    private void phaze$drawHitRangeSelf(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            Camera camera,
            RenderTickCounter tickCounter,
            List<Entity> entities,
            CallbackInfo ci
    ) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled() || !config.showSelf.isValue()) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        float tickDelta = tickCounter.getTickDelta(false);
        double px = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
        double py = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
        double pz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());

        Vec3d playerPos = new Vec3d(px, py, pz).subtract(camera.getPos());

        EntityRenderer<? super ClientPlayerEntity, ?> renderer = this.entityRenderDispatcher.getRenderer(player);
        PlayerEntityRenderState state = (PlayerEntityRenderState) renderer.getAndUpdateRenderState(player, tickDelta);

        matrices.push();
        matrices.translate(playerPos.x, playerPos.y, playerPos.z);
        VertexConsumerProvider vertexConsumers = this.bufferBuilders.getEntityVertexConsumers();
        HitRangeCircleRenderer.drawCircle(matrices, vertexConsumers, state);
        matrices.pop();
    }

    // ---------------------------------------------------------------
    // FT helper / Snowball / Predictions overlay layers
    // ---------------------------------------------------------------

    @Inject(method = "renderEntities", at = @At(value = "TAIL"))
    private void phaze$drawFTLayers(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            Camera camera,
            RenderTickCounter tickCounter,
            List<Entity> entities,
            CallbackInfo ci
    ) {
        // Force-flush queued entity layers so anything we draw after
        // is guaranteed to paint on top.
        immediate.draw();

        Vec3d cameraPos = camera.getPos();
        FTHelperRenderer.renderHighlight(matrices, cameraPos, tickCounter);
        FTHelperRenderer.renderSnowballs(matrices, cameraPos);
        PredictionsRenderer.render(matrices, cameraPos, tickCounter);
    }

    // ---------------------------------------------------------------
    // BlockOverlay: outline tint + filled face draw
    // ---------------------------------------------------------------

    @ModifyArg(method = "renderTargetBlockOutline",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V"),
            index = 8)
    private int phaze$tintOutline(int original) {
        BlockOverlay module = BlockOverlay.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }
        return module.outlineColor.getColor();
    }

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"))
    private void phaze$drawFill(Camera camera,
                                VertexConsumerProvider.Immediate vertexConsumers,
                                MatrixStack matrices,
                                boolean translucent,
                                CallbackInfo ci) {
        BlockOverlay module = BlockOverlay.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (!"Filled".equalsIgnoreCase(module.style.getSelected())) return;

        if (!translucent) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos,
                net.minecraft.block.ShapeContext.of(camera.getFocusedEntity()));
        if (shape.isEmpty()) return;

        Vec3d cameraPos = camera.getPos();
        int fillColor = module.fillColor.getColor();
        float fillAlphaScale = ((fillColor >>> 24) & 0xFF) / 255.0F;
        int opaqueRgb = 0xFF000000 | (fillColor & 0x00FFFFFF);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                Render3DUtil.drawBoxFill(matrices,
                        (float) (pos.getX() + minX),
                        (float) (pos.getY() + minY),
                        (float) (pos.getZ() + minZ),
                        (float) (pos.getX() + maxX),
                        (float) (pos.getY() + maxY),
                        (float) (pos.getZ() + maxZ),
                        opaqueRgb, fillAlphaScale)
        );
        matrices.pop();
    }
}
