package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.NoRender;

/**
 * Cancels the burning-fire overlay quad drawn over the player's view
 * while {@link net.minecraft.entity.Entity#isOnFire()} is true.
 *
 * <p>In 1.21.4 the entry point is the private-static
 * {@code InGameOverlayRenderer.renderFireOverlay(MatrixStack, VertexConsumerProvider)}
 * (verified by class disassembly: the public {@code renderOverlays}
 * funnel dispatches into three private overlay-draw methods - fire,
 * underwater, in-wall - each of which is the actual quad-submission
 * site). Cancelling at HEAD skips both the texture bind and the
 * vertex submission, so no fire geometry is uploaded for those
 * frames.
 *
 * <p>Mojang dropped the {@code MinecraftClient} parameter and added
 * a {@code VertexConsumerProvider} when they migrated overlays to
 * the new buffer-builder pipeline; the previous mixin (which
 * targeted the 1.20-style {@code (MinecraftClient, MatrixStack)}
 * sig) silently failed to attach in 1.21.4 because no method with
 * that descriptor exists anymore.
 *
 * <p>The mob's fire feature on third-person entity models is
 * intentionally NOT touched: the user wants the screen overlay
 * gone (which obscures their view), not the cosmetic fire on
 * burning entities (which is information they want to see).
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererNoFireMixin {

    @Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void phaze$skipFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.fire.isValue()) {
            ci.cancel();
        }
    }
}
