package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
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
 * <p>{@code InGameOverlayRenderer.renderFireOverlay(MinecraftClient, MatrixStack)}
 * is the single static entry point Minecraft calls every frame from
 * {@code GameRenderer.renderHand} (or its inlined fork) when the
 * player is on fire. Cancelling at HEAD skips both the texture bind
 * and the GUI quad submission, so no fire texture is even uploaded
 * for those frames.
 *
 * <p>The mob's fire {@code FireFeatureRenderer} on third-person
 * models is intentionally NOT touched: the user wants the screen
 * overlay gone (which obscures their view), not the cosmetic fire
 * drawn on burning entities (which is information they want to see).
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererNoFireMixin {

    @Inject(method = "renderFireOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void phaze$skipFireOverlay(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.fire.isValue()) {
            ci.cancel();
        }
    }
}
