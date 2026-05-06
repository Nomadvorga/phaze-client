package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.hud.HudBuffer;

/**
 * Redirects {@link MinecraftClient#getFramebuffer()} to our HUD-batch FBO while
 * a capture is active. Without this, RenderLayers that use {@code Target.MAIN_TARGET}
 * (most GUI text, fills, item rendering) explicitly rebind {@code mc.getFramebuffer()}
 * during {@code setupState()}, bypassing our explicit FBO bind and causing
 * captured content to land in the real main framebuffer.
 *
 * <p>Mirrors Exordium's {@code Minecraft#getMainRenderTarget} HEAD-cancellable mixin.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientFramebufferMixin {

    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void phaze$redirectFramebufferToBatchCapture(CallbackInfoReturnable<Framebuffer> cir) {
        if (HudBuffer.activeCaptureTarget < 0) {
            return;
        }
        SimpleFramebuffer fbo = BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer();
        if (fbo != null) {
            cir.setReturnValue(fbo);
        }
    }
}
