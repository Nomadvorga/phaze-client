package vorga.phazeclient.mixins;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ColorCorrection;

@Mixin(value = GameRenderer.class, priority = 1100)
public class GameRendererColorCorrectionMixin {

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void afterRenderHand(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!ColorCorrection.getInstance().isEnabled()) return;
        if (ColorCorrection.getInstance().shader == null) return;
        ColorCorrection.getInstance().shader.apply();
    }
}
