package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;

/**
 * Consolidated {@link HeldItemRenderer} mixin: hand tweaks (offset
 * + scale on first-person items) and No-Hand-Sway (suppresses the
 * camera-following rotation). Different injection points / methods,
 * but same target class — merged for fewer mixin files.
 *
 * <h3>Hand tweaks</h3>
 * Inject at the {@code INVOKE} of {@code renderItem(...)} inside
 * {@code renderFirstPersonItem}, BEFORE shift. By that point vanilla
 * has already pushed its per-item transforms (equip offset, swing,
 * use-action) so {@code matrices.translate + scale} only mutates
 * the item mesh, not its anchor position. X is sign-corrected by
 * the active main arm so a left-handed flip mirrors the user-
 * configured offset symmetrically.
 *
 * <h3>No hand sway</h3>
 * MixinExtras' {@link WrapWithCondition} on the
 * {@code matrices.multiply(Quaternionf)} calls inside the outer
 * {@code renderItem(F,MatrixStack,VCPI,CPE,I)V} overload. Returning
 * {@code false} suppresses both quaternion multiplies (the pitch
 * and yaw lag rotations); returning {@code true} lets vanilla run
 * unchanged.
 *
 * <h3>Attribution</h3>
 * The no-sway part is adapted from {@code logwan.nohandsway.mixin.NoSwayMixin}
 * by O3kar (Apache License 2.0). See {@code THIRD_PARTY_LICENSES.md}
 * at the project root.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            )
    )
    private void phaze$applyHandTweaks(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }

        // Mirror X when player's main arm has been flipped from
        // vanilla default (Right). Vanilla's per-item translates
        // already mirror via (arm == Right ? 1 : -1); without this
        // sign-match the user's X slider would point the wrong way
        // after a ChangeHand flip.
        float xSign = (player.getMainArm() == Arm.LEFT) ? -1.0f : 1.0f;

        boolean isMain = hand == Hand.MAIN_HAND;
        if (isMain) {
            if (!mod.hasMainHandTransform()) return;
            matrices.translate(
                    mod.mainHandX.getValue() * xSign,
                    mod.mainHandY.getValue(),
                    mod.mainHandZ.getValue()
            );
            float scale = mod.mainHandScale.getValue();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
        } else {
            if (!mod.hasOffHandTransform()) return;
            matrices.translate(
                    mod.offHandX.getValue() * xSign,
                    mod.offHandY.getValue(),
                    mod.offHandZ.getValue()
            );
            float scale = mod.offHandScale.getValue();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
        }
    }

    @WrapWithCondition(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"
            )
    )
    private boolean phaze$skipHandSway(MatrixStack instance, Quaternionf rotation) {
        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled() || !mod.noHandSway.isValue()) {
            return true;
        }
        return false;
    }
}
