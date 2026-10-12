package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

/**
 * Vanilla held-item features write their own vertex colors and therefore
 * ignore the shader alpha used by the cosmetics player preview. Wrapping only
 * that feature's consumer keeps the item on the exact same fade curve as the
 * player without touching normal world rendering.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererPreviewAlphaMixin {
    @ModifyVariable(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private VertexConsumerProvider phaze$fadePreviewHeldItem(VertexConsumerProvider original) {
        if (!CosmeticsRenderer.isRenderingPreview()) return original;
        float alpha = CosmeticsRenderer.previewAlpha();
        if (alpha >= 0.999F) return original;
        return layer -> new AlphaVertexConsumer(original.getBuffer(layer), alpha);
    }

    @Inject(
            method = "renderItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"
            )
    )
    private void phaze$alignSwordBlockingThirdPerson(
            ArmedEntityRenderState entityState,
            ItemRenderState itemState,
            Arm arm,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (entityState instanceof PlayerEntityRenderState playerState) {
            OldAnimations mod = OldAnimations.getInstance();
            if (mod != null && mod.isEnabled() && mod.swordBlocking.isValue()) {
                boolean isMainArm = (arm == playerState.mainArm);
                boolean isBlockingArm = isMainArm && (playerState.rightArmPose == BipedEntityModel.ArmPose.BLOCK
                        || playerState.leftArmPose == BipedEntityModel.ArmPose.BLOCK);
                if (isBlockingArm) {
                    float sign = (arm == Arm.LEFT) ? -1.0F : 1.0F;
                    float rx = mod.f5RotX.getValue();
                    float ry = mod.f5RotY.getValue();
                    float rz = mod.f5RotZ.getValue();
                    if (rx != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sign * rx));
                    if (ry != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * ry));
                    if (rz != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * rz));
                }
            }
        }
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        private AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            int sourceAlpha = argb >>> 24;
            int fadedAlpha = Math.round(sourceAlpha * alpha);
            delegate.color((argb & 0x00FFFFFF) | (fadedAlpha << 24));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }
}
