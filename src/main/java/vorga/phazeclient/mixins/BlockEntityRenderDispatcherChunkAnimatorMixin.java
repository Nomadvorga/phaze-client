package vorga.phazeclient.mixins;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Chests, shulker boxes and barrels are block entities, rendered in a separate
 * pass from their chunk mesh. While World Animator moves/fades/scales a chunk,
 * these containers otherwise remain at their final coordinates. Hide them for
 * the short animation window so they appear together with their terrain.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherChunkAnimatorMixin {
    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <E extends BlockEntity> void phaze$hideContainersDuringChunkAnimation(
            E blockEntity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (phaze$shouldHideContainer(blockEntity)) {
            ci.cancel();
        }
    }

    // Some renderer paths invoke the dispatcher helper directly. Cover it as
    // well, so the rule works with vanilla, Sodium and Iris render pipelines.
    @Inject(
            method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static <E extends BlockEntity> void phaze$hideContainersDuringDirectRender(
            BlockEntityRenderer<E> renderer,
            E blockEntity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (phaze$shouldHideContainer(blockEntity)) {
            ci.cancel();
        }
    }

    private static boolean phaze$shouldHideContainer(BlockEntity blockEntity) {
        if (!(blockEntity instanceof ChestBlockEntity
                || blockEntity instanceof ShulkerBoxBlockEntity
                || blockEntity instanceof BarrelBlockEntity)) {
            return false;
        }
        return ChunkAnimator.getInstance().isColumnAnimating(blockEntity.getPos());
    }
}
