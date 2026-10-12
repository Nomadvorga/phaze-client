package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
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
import vorga.phazeclient.implement.features.modules.other.WaveCapes;
import vorga.phazeclient.implement.features.modules.other.WavyCapeRenderer;

/**
 * Renders Phaze capes and handles WaveCapes 16-segment cloth physics for
 * both custom Phaze capes and vanilla player capes.
 */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererMixin
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    @Shadow
    @Final
    private BipedEntityModel<PlayerEntityRenderState> model;

    private CapeFeatureRendererMixin(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context
    ) {
        super(context);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$renderCape(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        if (state.invisible || !state.capeVisible) return;

        WaveCapes waveCapes = WaveCapes.getInstance();
        boolean wavy = waveCapes != null && waveCapes.isEnabled();

        // 1. Phaze Custom Cape check
        String selection = CosmeticsRenderer.capeSelectionFor(state);
        if (CosmeticsState.isCape(selection)) {
            ci.cancel();
            Identifier texture = CosmeticsRenderer.capeTexture(selection);
            if (texture == null) return;

            if (wavy) {
                WavyCapeRenderer.renderWavyCape(matrices, vertexConsumers, texture, light, state);
            } else {
                matrices.push();
                VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(texture));
                getContextModel().copyTransforms(model);
                model.setAngles(state);
                model.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            return;
        }

        // 2. Vanilla / Official Cape with WavyCapes enabled
        if (wavy) {
            SkinTextures skinTextures = state.skinTextures;
            if (skinTextures != null && skinTextures.capeTexture() != null) {
                ci.cancel();
                WavyCapeRenderer.renderWavyCape(matrices, vertexConsumers, skinTextures.capeTexture(), light, state);
            }
        }
    }
}
