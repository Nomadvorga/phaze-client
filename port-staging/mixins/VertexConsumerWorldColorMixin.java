package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.colorcorrection.WorldColorCorrectionController;

@Mixin(VertexConsumer.class)
public interface VertexConsumerWorldColorMixin {

    @Inject(method = "color(I)Lnet/minecraft/client/render/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void phaze$applyWorldColorToArgb(int argb, CallbackInfoReturnable<VertexConsumer> cir) {
        int transformed = WorldColorCorrectionController.applyToArgb(argb);
        if (transformed == argb) {
            return;
        }

        VertexConsumer self = (VertexConsumer) this;
        cir.setReturnValue(self.color(
                (transformed >>> 16) & 0xFF,
                (transformed >>> 8) & 0xFF,
                transformed & 0xFF,
                (transformed >>> 24) & 0xFF
        ));
    }

    @Inject(method = "color(FFFF)Lnet/minecraft/client/render/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void phaze$applyWorldColorToFloats(float red, float green, float blue, float alpha, CallbackInfoReturnable<VertexConsumer> cir) {
        int argb = ((int) (alpha * 255.0F) & 0xFF) << 24
                | ((int) (red * 255.0F) & 0xFF) << 16
                | ((int) (green * 255.0F) & 0xFF) << 8
                | ((int) (blue * 255.0F) & 0xFF);
        int transformed = WorldColorCorrectionController.applyToArgb(argb);
        if (transformed == argb) {
            return;
        }

        VertexConsumer self = (VertexConsumer) this;
        cir.setReturnValue(self.color(
                (transformed >>> 16) & 0xFF,
                (transformed >>> 8) & 0xFF,
                transformed & 0xFF,
                (transformed >>> 24) & 0xFF
        ));
    }
}
