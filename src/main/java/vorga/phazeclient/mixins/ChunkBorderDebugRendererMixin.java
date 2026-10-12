package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.ChunkBorderDebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChunkBorders;

@Mixin(ChunkBorderDebugRenderer.class)
public abstract class ChunkBorderDebugRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;DDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$overrideChunkBorderRenderer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        ChunkBorders module = ChunkBorders.getInstance();
        if (module != null && module.isEnabled()) {
            ci.cancel();
            module.renderCustomChunkBorders(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
        }
    }
}
