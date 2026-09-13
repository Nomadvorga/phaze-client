package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
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
import vorga.phazeclient.api.system.itemphysics.PhazeItemEntityRenderState;
import vorga.phazeclient.implement.features.modules.other.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererPhysicsMixin {

    @Unique
    private static final float PHAZE_DEPTH_THRESHOLD = 0.0625f;

    @Shadow
    @Final
    private Random random;

    @Unique
    private boolean phaze$currentIsBlock = false;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void captureEntityId(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (ItemPhysics.getInstance().isEnabled()) {
            phaze$currentIsBlock = !state.itemRenderState.isEmpty() && phaze$hasDepth(state.itemRenderState);
            ItemPhysicsManager.getInstance().updateRotation(entity, phaze$currentIsBlock);
            ((PhazeItemEntityRenderState) (Object) state).phaze$entityId(entity.getId());
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void applyPhysics(ItemEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!ItemPhysics.getInstance().isEnabled()) return;
        if (state.itemRenderState.isEmpty()) return;

        ItemPhysicsManager.ItemPhysicsData data = ItemPhysicsManager.getInstance()
                .getItemData(((PhazeItemEntityRenderState) (Object) state).phaze$entityId());
        if (data == null) return;

        final int light = state.light;
        final int outlineColor = state.outlineColor;

        final Box box = state.itemRenderState.getModelBoundingBox();
        final float depth = (float) box.getLengthZ();

        matrixStack.push();

        random.setSeed(state.seed);
        boolean isBlock = depth > PHAZE_DEPTH_THRESHOLD;

        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));

        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(data.yRot));

        float scaleY = (float) box.getLengthY();

        if (isBlock) {

            matrixStack.translate(0.0f, -0.2f, -0.08f);
            matrixStack.translate(0.0f, scaleY, 0.0f);
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(data.xRot));
            matrixStack.translate(0.0f, -scaleY, 0.0f);
        } else {

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

        float stackStep = depth * 1.5f;

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
            state.itemRenderState.render(matrixStack, queue, light, OverlayTexture.DEFAULT_UV, outlineColor);
            matrixStack.pop();
            if (!isBlock) {
                matrixStack.translate(0.0f, 0.0f, stackStep);
            }
        }

        matrixStack.pop();

        ci.cancel();
    }

    @Unique
    private static boolean phaze$hasDepth(ItemRenderState renderState) {
        return renderState.getModelBoundingBox().getLengthZ() > PHAZE_DEPTH_THRESHOLD;
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
