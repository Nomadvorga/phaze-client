package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.render.Render3DUtil;
import vorga.phazeclient.implement.features.modules.other.BlockOverlay;

/**
 * Block Overlay: recolours the targeted block's outline and, in "Filled"
 * style, draws a tinted box over its shape.
 *
 * <h3>1.21.11 port</h3>
 *
 * <p>The 1.21.4 version of this lived in the monolithic {@code WorldRendererMixin},
 * which is still unported. Both hooks survived the version bump, but their
 * signatures changed:
 *
 * <ul>
 *   <li>{@code renderTargetBlockOutline(Camera, Immediate, MatrixStack, boolean)}
 *       became {@code renderTargetBlockOutline(Immediate, MatrixStack, boolean, WorldRenderState)} -
 *       the camera is no longer passed, it is reached through the render
 *       state (or, as here, through the client).</li>
 *   <li>{@code drawBlockOutline} dropped its {@code Entity}, {@code BlockPos}
 *       and {@code BlockState} parameters in favour of a single
 *       {@link net.minecraft.client.render.state.OutlineRenderState}, which
 *       moved the colour argument from index 8 to index 6 and appended a
 *       trailing float.</li>
 * </ul>
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererBlockOutlineMixin {

    /**
     * Recolour the outline.
     *
     * <p>Deliberately not pinned with {@code ordinal}: 1.21.11 draws the
     * outline twice in high-contrast mode (a secondary pass through
     * {@code RenderLayers.secondaryBlockOutline()}), and tinting only one of
     * them would leave a vanilla-coloured ghost behind the custom one.
     */
    @ModifyArg(
            method = "renderTargetBlockOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V"
            ),
            index = 6,
            require = 0
    )
    private int phaze$tintOutline(int original) {
        BlockOverlay module = BlockOverlay.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }
        return module.outlineColor.getColor();
    }

    /**
     * Draw the filled box for the "Filled" style.
     *
     * <p>Runs at HEAD so the fill lands under vanilla's outline pass rather
     * than over it, matching 1.21.4. The {@code translucent} guard keeps this
     * to the single pass that is actually blended - without it the box would
     * be emitted twice per frame and read double-strength.
     */
    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), require = 0)
    private void phaze$drawFill(VertexConsumerProvider.Immediate vertexConsumers,
                                MatrixStack matrices,
                                boolean translucent,
                                WorldRenderState worldRenderState,
                                CallbackInfo ci) {
        BlockOverlay module = BlockOverlay.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (!"Filled".equalsIgnoreCase(module.style.getSelected())) return;
        if (!translucent) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.gameRenderer == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos, ShapeContext.of(camera.getFocusedEntity()));
        if (shape.isEmpty()) return;

        // 1.21.11 renamed Camera.getPos() to getCameraPos().
        Vec3d cameraPos = camera.getCameraPos();
        int fillColor = module.fillColor.getColor();
        float fillAlphaScale = ((fillColor >>> 24) & 0xFF) / 255.0F;
        int opaqueRgb = 0xFF000000 | (fillColor & 0x00FFFFFF);

        // Subtract the camera while still in double precision, then emit small
        // camera-relative float vertices. Casting absolute world coordinates
        // first loses precision and breaks the fill far from spawn.
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                Render3DUtil.drawBoxFill(matrices,
                        (float) (pos.getX() + minX - cameraPos.x),
                        (float) (pos.getY() + minY - cameraPos.y),
                        (float) (pos.getZ() + minZ - cameraPos.z),
                        (float) (pos.getX() + maxX - cameraPos.x),
                        (float) (pos.getY() + maxY - cameraPos.y),
                        (float) (pos.getZ() + maxZ - cameraPos.z),
                        opaqueRgb, fillAlphaScale)
        );
    }
}
