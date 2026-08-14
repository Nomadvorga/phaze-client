package vorga.phazeclient.mixins;

import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorRenderHelper;

/** Adds a shader-only player/entity tag to deferred model colours. */
@Mixin(BatchingRenderCommandQueue.class)
public abstract class BatchingRenderCommandQueueWorldColorMixin {
    @ModifyVariable(
            method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private int phaze$tagDeferredModelColor(int color) {
        return WorldColorRenderHelper.tagDeferredEntityColor(color);
    }

    @ModifyVariable(
            method = "submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;ZZILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private int phaze$tagDeferredModelPartColor(int color) {
        return WorldColorRenderHelper.tagDeferredEntityColor(color);
    }
}
