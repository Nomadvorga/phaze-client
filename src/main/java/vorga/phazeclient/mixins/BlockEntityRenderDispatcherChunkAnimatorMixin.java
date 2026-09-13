package vorga.phazeclient.mixins;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderDispatcherChunkAnimatorMixin {
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void phaze$hideContainersDuringChunkAnimation(
            E blockEntity, float tickDelta, ModelCommandRenderer.CrumblingOverlayCommand overlay,
            CallbackInfoReturnable<S> cir) {
        if (phaze$shouldHideContainer(blockEntity)) {

            cir.setReturnValue(null);
        }
    }

    private static boolean phaze$shouldHideContainer(BlockEntity entity) {
        return (entity instanceof ChestBlockEntity || entity instanceof ShulkerBoxBlockEntity
                || entity instanceof BarrelBlockEntity)
                && ChunkAnimator.getInstance().isColumnAnimating(entity.getPos());
    }
}
