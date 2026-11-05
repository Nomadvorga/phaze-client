/*
 * Includes ported logic from the "hitrange" mod by uku3lig (uku),
 * https://github.com/uku3lig/hitrange, MIT License. See per-method
 * comments below for attribution.
 */
package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.Handle;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.render.Render3DUtil;
import vorga.phazeclient.api.system.render.WorldOverlayRenderer;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.other.BlockOverlay;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;
import vorga.phazeclient.implement.features.modules.other.NoRender;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;

/**
 * Consolidated mixin for {@link WorldRenderer}, merging:
 * <ul>
 *   <li>WeatherChanger ({@code renderWeather} cancel)</li>
 *   <li>MotionBlur (frame-pair matrix capture on {@code render})</li>
 *   <li>HitRange (self-circle on {@code renderEntities} TAIL) -
 *       ported from <a href="https://github.com/uku3lig/hitrange">uku's
 *       hitrange</a> mod (MIT)</li>
 *   <li>World-overlay frame capture (drawn after the world pass)</li>
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

    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    // ---------------------------------------------------------------
    // MotionBlur unique state
    // ---------------------------------------------------------------

    @Unique private final Matrix4f phaze$prevModelView = new Matrix4f();
    @Unique private final Matrix4f phaze$prevProjection = new Matrix4f();
    @Unique private final Matrix4f phaze$currentProjection = new Matrix4f();
    @Unique private final Vector3f phaze$prevCameraPos = new Vector3f();
    @Unique private final Vector3f phaze$currentCameraPos = new Vector3f();
    @Unique private boolean phaze$motionHistoryValid;
    @Unique private Object phaze$lastScreen;

    // ---------------------------------------------------------------
    // WeatherChanger: cancel weather render when Clear is forced
    // ---------------------------------------------------------------

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void phaze$cancelWeatherIfClear(FrameGraphBuilder builder, Vec3d cameraPos, float tickDelta, Fog fog, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isEnabled() && noRender.rain.isValue()) {
            ci.cancel();
            return;
        }
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive() && weatherChanger.weatherType.getSelected().equals("Clear")) {
            ci.cancel();
        }
    }

    // ---------------------------------------------------------------
    // MotionBlur: capture matrix pair around the render frame
    // ---------------------------------------------------------------

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void phaze$motionBlurSetMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        WorldOverlayRenderer.beginFrame(positionMatrix, projectionMatrix, camera, tickCounter);
        MinecraftClient client = MinecraftClient.getInstance();
        Object currentScreen = client == null ? null : client.currentScreen;
        if (currentScreen != phaze$lastScreen) {
            // Opening/closing a Screen can change the camera/FOV and the
            // framebuffer path without rendering a comparable world frame.
            // Do not use the pre-GUI matrix pair for the first frame after the
            // transition: that produces a full-screen motion streak over
            // world-space text and HUD-adjacent overlays until F3+T rebuilds
            // the post-effect state.
            phaze$lastScreen = currentScreen;
            phaze$motionHistoryValid = false;
        }
        if (!MotionBlur.getInstance().isEnabled()) {
            phaze$motionHistoryValid = false;
            return;
        }
        float tickDelta = tickCounter.getTickDelta(true);
        float fov = ((GameRendererAccessor) gameRenderer).invokeGetFov(camera, tickDelta, true);
        phaze$currentProjection.set(gameRenderer.getBasicProjectionMatrix(fov));
        Vec3d cameraPosition = camera.getPos();
        phaze$currentCameraPos.set(
                (float) (cameraPosition.x % 30000f),
                (float) (cameraPosition.y % 30000f),
                (float) (cameraPosition.z % 30000f)
        );
        if (!phaze$motionHistoryValid) {
            phaze$prevModelView.set(positionMatrix);
            phaze$prevProjection.set(phaze$currentProjection);
            phaze$prevCameraPos.set(phaze$currentCameraPos);
            phaze$motionHistoryValid = true;
        }
        MotionBlur.getInstance().shader.setFrameMotionBlur(positionMatrix, phaze$prevModelView,
                phaze$currentProjection,
                phaze$prevProjection,
                phaze$currentCameraPos,
                phaze$prevCameraPos
        );
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$motionBlurSetOldMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!MotionBlur.getInstance().isEnabled()) return;
        phaze$prevModelView.set(positionMatrix);
        phaze$prevProjection.set(phaze$currentProjection);
        phaze$prevCameraPos.set(phaze$currentCameraPos);
    }

    // ---------------------------------------------------------------
    // HitRange (self-circle) — ported from uku's hitrange mod (MIT).
    // ---------------------------------------------------------------

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void phaze$prepareNametagBlurFrame(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            Camera camera,
            RenderTickCounter tickCounter,
            List<Entity> entities,
            CallbackInfo ci
    ) {
        NametagHud module = NametagHud.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hidden = client != null
                && client.options != null
                && module.hideInF1.isValue()
                && client.options.hudHidden;
        boolean capture = !entities.isEmpty()
                && module.isEnabled()
                && module.background.isValue()
                && module.backgroundBlurRadius.getValue() > 0.0f
                && !hidden;
        Blur.INSTANCE.beginWorldSpaceFrame(capture);
    }

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

        matrices.push();
        try {
            Vec3d cameraPos = camera.getPos();
            matrices.translate(px - cameraPos.x, py - cameraPos.y, pz - cameraPos.z);
            HitRangeCircleRenderer.drawCircle(matrices, px, py, pz, player.getId(),
                    player.getGameProfile().getName(), player.isSneaky());
        } finally {
            matrices.pop();
        }
    }

    // ---------------------------------------------------------------
    // FT helper / Snowball / Predictions overlay layers
    // ---------------------------------------------------------------

    @Redirect(
            method = {
                    "getEntityOutlinesFramebuffer",
                    "getTranslucentFramebuffer",
                    "getEntityFramebuffer",
                    "getParticlesFramebuffer",
                    "getWeatherFramebuffer",
                    "getCloudsFramebuffer"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/Handle;get()Ljava/lang/Object;"
            ),
            require = 0
    )
    private Object phaze$safeFabulousFramebufferHandle(Handle<?> handle) {
        try {
            return handle.get();
        } catch (NullPointerException ignored) {
            // A deferred third-party buffer may outlive its FrameGraph pass.
            // Falling back to the currently bound target is safe and matches
            // the null-handle behavior used by vanilla's render phases.
            return null;
        }
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

    @WrapOperation(method = "renderTargetBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V"), require = 1)
    private void phaze$bufferBlockOutline(WorldRenderer renderer, MatrixStack matrices, VertexConsumer vanilla,
                                         Entity entity, double x, double y, double z, BlockPos pos, BlockState state,
                                         int color, Operation<Void> original) {
        BlockOverlay module = BlockOverlay.getInstance();
        VertexConsumer output = module != null && module.isEnabled()
                ? WorldOverlayRenderer.buffer(VertexFormat.DrawMode.LINES, VertexFormats.LINES, 1, true) : vanilla;
        original.call(renderer, matrices, output, entity, x, y, z, pos, state, color);
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

        // Match Predictions: subtract the camera while still in double
        // precision, then emit small camera-relative float vertices.
        // Casting absolute world coordinates first breaks the fill far
        // from spawn.
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                Render3DUtil.vertexBoxFill(matrices,
                        WorldOverlayRenderer.buffer(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR, 1, true),
                        new Box(pos.getX() + minX - cameraPos.x,
                                pos.getY() + minY - cameraPos.y,
                                pos.getZ() + minZ - cameraPos.z,
                                pos.getX() + maxX - cameraPos.x,
                                pos.getY() + maxY - cameraPos.y,
                                pos.getZ() + maxZ - cameraPos.z),
                        opaqueRgb, fillAlphaScale)
        );
    }
}
