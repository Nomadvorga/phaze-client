package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererWorldColorMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$pushBlockWorldColor(
            BlockRenderView world,
            BakedModel model,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            Random random,
            long seed,
            int overlay,
            CallbackInfo ci
    ) {
        WorldColorCorrectionController.push(WorldColorCorrectionController.Target.BLOCKS);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$popBlockWorldColor(
            BlockRenderView world,
            BakedModel model,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            Random random,
            long seed,
            int overlay,
            CallbackInfo ci
    ) {
        WorldColorCorrectionController.pop();
    }
}
