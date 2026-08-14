package vorga.phazeclient.mixins;

import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;

/** Fades and frame-limits the deferred cosmetics player preview. */
@Mixin(SpecialGuiElementRenderer.class)
public abstract class SpecialGuiElementFadeMixin {
    @Unique private float phaze$previewAlpha = 1.0F;
    @Unique private long phaze$lastPreviewRenderNanos;
    @Unique private String phaze$lastPreviewSelection;
    @Unique private float phaze$lastPreviewYaw = Float.NaN;

    @Inject(method = "shouldBypassScaling", at = @At("HEAD"), cancellable = true)
    private void phaze$reuseCosmeticPreview(SpecialGuiElementRenderState state,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!(state instanceof EntityGuiElementRenderState entity)
                || !(entity.renderState() instanceof PreviewMarker marker)) return;
        String selection = marker.phaze$previewSelection();
        if (selection == null) return;
        long now = System.nanoTime();
        float yaw = marker.phaze$previewYaw();
        boolean same = selection.equals(phaze$lastPreviewSelection);
        boolean rotating = !Float.isNaN(phaze$lastPreviewYaw)
                && Math.abs(yaw - phaze$lastPreviewYaw) > 0.001F;
        long interval = rotating ? 8_333_333L : 16_666_667L;
        if (same && now - phaze$lastPreviewRenderNanos < interval) {
            cir.setReturnValue(true);
            return;
        }
        phaze$lastPreviewSelection = selection;
        phaze$lastPreviewYaw = yaw;
        phaze$lastPreviewRenderNanos = now;
        cir.setReturnValue(false);
    }

    @Inject(method = "renderElement", at = @At("HEAD"))
    private void phaze$capturePreviewAlpha(SpecialGuiElementRenderState state,
                                           GuiRenderState guiState, CallbackInfo ci) {
        if (state instanceof EntityGuiElementRenderState entity
                && entity.renderState() instanceof PreviewMarker marker) {
            phaze$previewAlpha = marker.phaze$previewAlpha();
        } else {
            phaze$previewAlpha = 1.0F;
        }
    }

    @ModifyArg(
            method = "renderElement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;addSimpleElementToCurrentLayer(Lnet/minecraft/client/gui/render/state/TexturedQuadGuiElementRenderState;)V"),
            index = 0
    )
    private TexturedQuadGuiElementRenderState phaze$fadePreview(
            TexturedQuadGuiElementRenderState quad) {
        if (phaze$previewAlpha >= 0.999F) return quad;
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, phaze$previewAlpha)) * 255.0F);
        int color = (a << 24) | (a << 16) | (a << 8) | a;
        return new TexturedQuadGuiElementRenderState(
                quad.pipeline(), quad.textureSetup(), quad.pose(),
                quad.x1(), quad.y1(), quad.x2(), quad.y2(),
                quad.u1(), quad.u2(), quad.v1(), quad.v2(),
                color, quad.scissorArea(), quad.bounds());
    }
}
