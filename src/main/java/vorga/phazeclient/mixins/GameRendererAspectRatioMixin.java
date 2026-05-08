package vorga.phazeclient.mixins;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.AspectRatio;

/**
 * Replaces the world projection matrix produced by
 * {@link GameRenderer#getBasicProjectionMatrix(float)} with one built
 * around the user-configured {@link AspectRatio#getRatio()}. The
 * cancellable TAIL inject runs after vanilla has already returned its
 * own matrix, then overrides it via {@link CallbackInfoReturnable#setReturnValue}
 * - the rest of the rendering pipeline (post-effects, motion blur,
 * frustum culling) consumes the substituted matrix exactly as if
 * vanilla had produced it.
 *
 * <p>Spyglass / zoom support: when {@code zoom != 1.0F} the original
 * method applies a translate-then-scale into the matrix to
 * recentre the zoomed view on {@code (zoomX, zoomY)}; we replicate
 * that step before the perspective mul so spyglass continues to work
 * under a custom aspect ratio. {@code viewDistance * 4.0F} is
 * vanilla's far-plane factor, kept verbatim so chunk culling matches.
 *
 * <p>Falls through untouched whenever the module is disabled or its
 * singleton hasn't been published yet, so vanilla's projection logic
 * is exactly preserved off-state.
 */
@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class GameRendererAspectRatioMixin {

    @Shadow private float zoom;
    @Shadow private float zoomX;
    @Shadow private float zoomY;
    @Shadow private float viewDistance;

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    private void phaze$overrideAspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio module = AspectRatio.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        MatrixStack stack = new MatrixStack();
        stack.peek().getPositionMatrix().identity();

        float ratio = module.getRatio();

        if (zoom != 1.0F) {
            stack.translate(zoomX, -zoomY, 0.0F);
            stack.scale(zoom, zoom, 1.0F);
        }

        stack.peek().getPositionMatrix().mul(
                new Matrix4f().setPerspective(
                        (float) (fovDegrees * (Math.PI / 180.0)),
                        ratio,
                        0.05F,
                        viewDistance * 4.0F
                )
        );

        cir.setReturnValue(stack.peek().getPositionMatrix());
    }
}
