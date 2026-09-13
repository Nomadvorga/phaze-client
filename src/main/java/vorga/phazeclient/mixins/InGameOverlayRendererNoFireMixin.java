package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.NoRender;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererNoFireMixin {

    @Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void phaze$skipFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.fire.isValue()) {
            ci.cancel();
        }
    }
}
