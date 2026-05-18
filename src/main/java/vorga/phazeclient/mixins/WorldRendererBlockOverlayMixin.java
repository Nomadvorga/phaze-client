package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
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
 * Two-part hook for {@link BlockOverlay}:
 * <ol>
 *   <li>{@link #phaze$tintOutline} - {@code @ModifyArg} on the
 *       outline color argument passed to
 *       {@code WorldRenderer.drawBlockOutline}. Vanilla calls this
 *       once for the regular outline pass; we just substitute the
 *       user's chosen ARGB. Pixel-perfect result because vanilla's
 *       {@code RenderLayer.getLines()} pipeline still draws the
 *       lines.</li>
 *   <li>{@link #phaze$drawFill} - HEAD inject on
 *       {@code renderTargetBlockOutline}. When the user picks the
 *       Filled style we draw a translucent face fill BEFORE
 *       vanilla draws the outline, so the outline reads on top of
 *       the fill.</li>
 * </ol>
 *
 * <h3>Why we don't tint the high-contrast outline</h3>
 * Vanilla draws a SECONDARY high-contrast outline (cyan / black
 * pair) when the accessibility option is enabled. We deliberately
 * skip that path - tinting it would defeat the high-contrast
 * intent. Users who enable accessibility get vanilla's output, the
 * rest get our color.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererBlockOverlayMixin {

    @ModifyArg(method = "renderTargetBlockOutline",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V"),
            index = 8)
    private int phaze$tintOutline(int original) {
        BlockOverlay module = BlockOverlay.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }
        // The color int passed to drawBlockOutline is ARGB. Vanilla
        // splits the components inside the line-vertex emitter, so
        // the alpha component DOES affect transparency - we forward
        // the user's full ARGB without any masking.
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

        // renderTargetBlockOutline is called twice per frame
        // (opaque + translucent pass). Drawing the fill on BOTH
        // passes would double its alpha; gate on the translucent
        // pass which is the one users see (the opaque pass would
        // be hidden behind transparent terrain like glass panes).
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
        // Extract alpha as a 0..1 scale we feed into drawBox's
        // fillAlphaScale parameter; with thickness 0 we get only
        // the filled faces, no outline (vanilla still draws its
        // own outline a moment later).
        float fillAlphaScale = ((fillColor >>> 24) & 0xFF) / 255.0F;
        // Force opaque RGB in the color int we hand drawBox - the
        // helper multiplies its own fillAlphaScale onto the alpha,
        // so feeding it a pre-faded ARGB would double the fade.
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
