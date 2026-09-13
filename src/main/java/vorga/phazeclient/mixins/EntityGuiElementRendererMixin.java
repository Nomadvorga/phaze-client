package vorga.phazeclient.mixins;

import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;

@Mixin(net.minecraft.client.gui.render.EntityGuiElementRenderer.class)
public abstract class EntityGuiElementRendererMixin {
    @Unique
    private static boolean phaze$renderingPreviewEntity = false;

    @Inject(
            method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD")
    )
    private void phaze$beginPreviewLighting(EntityGuiElementRenderState state, MatrixStack matrices, CallbackInfo ci) {
        phaze$renderingPreviewEntity = state != null
                && state.renderState() instanceof PreviewMarker marker
                && marker.phaze$previewSelection() != null;
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/DiffuseLighting;setShaderLights(Lnet/minecraft/client/render/DiffuseLighting$Type;)V"
            ),
            require = 0
    )
    private DiffuseLighting.Type phaze$worldLightingForPreview(DiffuseLighting.Type original) {
        return phaze$renderingPreviewEntity ? DiffuseLighting.Type.LEVEL : original;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("TAIL")
    )
    private void phaze$endPreviewLighting(EntityGuiElementRenderState state, MatrixStack matrices, CallbackInfo ci) {
        phaze$renderingPreviewEntity = false;
    }
}
