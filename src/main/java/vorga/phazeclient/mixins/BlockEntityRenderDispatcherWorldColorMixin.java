package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;
import vorga.phazeclient.api.system.colorcorrection.WorldColorRenderHelper;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherWorldColorMixin {
    @ModifyVariable(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4,
            require = 0
    )
    private VertexConsumerProvider phaze$wrapBlockEntityProvider(VertexConsumerProvider original) {
        return WorldColorRenderHelper.tintProvider(original, WorldColorCorrectionController.Target.BLOCKS);
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4,
            require = 0
    )
    private static VertexConsumerProvider phaze$wrapStaticBlockEntityProvider(VertexConsumerProvider original) {
        return WorldColorRenderHelper.tintProvider(original, WorldColorCorrectionController.Target.BLOCKS);
    }
}
