package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.CosmeticsState;

/** Replaces the vanilla cape feature with the selected Phaze cape. */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    @Shadow @Final private BipedEntityModel<PlayerEntityRenderState> model;

    private CapeFeatureRendererMixin(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$renderCustomCape(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        String selection = CosmeticsRenderer.capeSelectionFor(state);
        if (!CosmeticsState.isCape(selection)) return;
        ci.cancel();
        if (state.invisible || !state.capeVisible) return;
        Identifier texture = CosmeticsRenderer.capeTexture(selection);
        if (texture == null) return;

        matrices.push();
        queue.submitModel(model, state, matrices, RenderLayers.entitySolid(texture),
                light, OverlayTexture.DEFAULT_UV, state.outlineColor, null);
        matrices.pop();
    }
}
