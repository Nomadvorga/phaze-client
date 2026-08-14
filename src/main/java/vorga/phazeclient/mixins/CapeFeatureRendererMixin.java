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

/**
 * Renders a selected Phaze cape through Minecraft's PlayerCapeModel directly.
 * This does not depend on the account owning an official Mojang cape and
 * intentionally replaces the vanilla cape texture when both are present.
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
    private void phaze$renderCustomCape(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci

        ) {
        String selection = CosmeticsRenderer.capeSelectionFor(state);
        if (!CosmeticsState.isCape(selection)) return;

        // A Phaze cape fully owns this feature pass, including the case where
        // the account also has a vanilla cape or wears an elytra.
        ci.cancel();
        if (state.invisible || !state.capeVisible) return;
        Identifier texture = CosmeticsRenderer.capeTexture(selection);
        if (texture == null) return;

        matrices.push();
        VertexConsumer vertices =
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(texture));
        getContextModel().copyTransforms(model);
        model.setAngles(state);
        model.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
