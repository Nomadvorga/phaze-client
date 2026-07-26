package vorga.phazeclient.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererWorldColorMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$pushFluidWorldColor(
            BlockRenderView world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        WorldColorCorrectionController.push(WorldColorCorrectionController.Target.FLUIDS);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$popFluidWorldColor(
            BlockRenderView world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        WorldColorCorrectionController.pop();
    }
}
