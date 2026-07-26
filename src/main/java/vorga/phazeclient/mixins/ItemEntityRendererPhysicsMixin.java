package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.itemphysics.ItemPhysicsManager;
import vorga.phazeclient.implement.features.modules.other.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererPhysicsMixin {

    @Shadow
    @Final
    private Random random;

    @Unique
    private int phaze$currentEntityId = -1;

    @Unique
    private boolean phaze$currentIsBlock = false;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void captureEntityId(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (ItemPhysics.getInstance().isEnabled()) {
            phaze$currentEntityId = entity.getId();
            phaze$currentIsBlock = !state.itemRenderState.isEmpty() && state.itemRenderState.hasDepth();
            ItemPhysicsManager.getInstance().updateRotation(entity, phaze$currentIsBlock);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void applyPhysics(ItemEntityRenderState state, MatrixStack matrixStack, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!ItemPhysics.getInstance().isEnabled()) return;
        if (phaze$currentEntityId < 0) return;
        if (state.itemRenderState.isEmpty()) return;

        ItemPhysicsManager.ItemPhysicsData data = ItemPhysicsManager.getInstance().getItemData(phaze$currentEntityId);
        if (data == null) return;

        matrixStack.push();

        random.setSeed(state.seed);
        boolean isBlock = state.itemRenderState.hasDepth();

        // Lay flat: rotate 90 degrees on X axis
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        // Apply Y rotation (entity yaw)
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(data.yRot));

        float scaleY = state.itemRenderState.getTransformation().scale.y();

        if (isBlock) {
            // Block items: offset down and apply rotation around center
            matrixStack.translate(0.0f, -0.2f, -0.08f);
            matrixStack.translate(0.0f, scaleY, 0.0f);
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(data.xRot));
            matrixStack.translate(0.0f, -scaleY, 0.0f);
        } else {
            // Flat items: small offset
            matrixStack.translate(0.0f, 0.0f, -0.04f);
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(data.xRot));
        }

        int renderedAmount = state.renderedAmount;

        if (!isBlock) {
            float f7 = -0.0f * (renderedAmount - 1) * 0.5f;
            float f8 = -0.0f * (renderedAmount - 1) * 0.5f;
            float f9 = -0.09375f * (renderedAmount - 1) * 0.5f;
            matrixStack.translate(f7, f8, f9);
        }

        float sx = state.itemRenderState.getTransformation().scale.x();
        float sy = state.itemRenderState.getTransformation().scale.y();
        float sz = state.itemRenderState.getTransformation().scale.z();

        for (int k = 0; k < renderedAmount; k++) {
            matrixStack.push();
            if (k > 0) {
                if (isBlock) {
                    float rx = (random.nextFloat() * 2.0f - 1.0f) * 0.15f;
                    float ry = (random.nextFloat() * 2.0f - 1.0f) * 0.15f;
                    float rz = (random.nextFloat() * 2.0f - 1.0f) * 0.15f;
                    matrixStack.translate(rx, ry, rz);
                } else {
                    float rx = (random.nextFloat() * 2.0f - 1.0f) * 0.15f * 0.5f;
                    float ry = (random.nextFloat() * 2.0f - 1.0f) * 0.15f * 0.5f;
                    matrixStack.translate(rx, ry, 0.0f);
                }
            }
            state.itemRenderState.render(matrixStack, vcp, light, OverlayTexture.DEFAULT_UV);
            matrixStack.pop();
            if (!isBlock) {
                matrixStack.translate(0.0f * sx, 0.0f * sy, 0.09375f * sz);
            }
        }

        matrixStack.pop();

        ci.cancel();
    }

    @Unique
    private static int phaze$getRenderedAmount(int count) {
        if (count > 48) return 5;
        if (count > 32) return 4;
        if (count > 16) return 3;
        if (count > 1) return 2;
        return 1;
    }
}
