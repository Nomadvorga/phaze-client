package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;
import vorga.phazeclient.implement.hitcolor.OverlayReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends net.minecraft.entity.LivingEntity, S extends LivingEntityRenderState, M extends net.minecraft.client.model.Model> {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract float getAnimationCounter(S state);

    @Inject(
        method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"},
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V",
            ordinal = 0
        )}
    )
    private void render(S livingEntityRenderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci, @Local FeatureRenderer<?, ?> featureRenderer) {
        if (HitColor.getInstance().isEnabled() && featureRenderer instanceof OverlayRendered rendered) {
            int overlay = LivingEntityRenderer.getOverlay(livingEntityRenderState, this.getAnimationCounter(livingEntityRenderState));
            rendered.setOverlay(overlay);
            OverlayReloadListener.event();
        }
    }
}
