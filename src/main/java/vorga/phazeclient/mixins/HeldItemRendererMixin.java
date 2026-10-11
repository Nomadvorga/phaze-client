package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;
import vorga.phazeclient.implement.features.modules.other.OldAnimations;

/**
 * Consolidated {@link HeldItemRenderer} mixin:
 * <ul>
 *   <li><b>Hand tweaks:</b> offset + scale on first-person items (ChangeHand).</li>
 *   <li><b>No hand sway:</b> camera-following rotation suppression (ChangeHand).</li>
 *   <li><b>Old Animations:</b> 1.7 BlockHit, 1.7 Sword Blocking, 1.7/1.8 Swing,
 *       No Re-Equip, 1.7 Eating, and offhand shield lowering.</li>
 * </ul>
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
        float xSign = (player.getMainArm() == Arm.LEFT) ? -1.0f : 1.0f;
        boolean isMain = (hand == Hand.MAIN_HAND);

        // --- Old Animations extras (Lower Shield, 1.7 Rod, 1.7 Bow) ---
        OldAnimations oldAnim = OldAnimations.getInstance();
        if (oldAnim != null && oldAnim.isEnabled()) {
            if (!isMain && item.getItem() instanceof ShieldItem && oldAnim.lowerShield.isValue()) {
                matrices.translate(0.0F, -0.2F, 0.0F);
                matrices.scale(0.85F, 0.85F, 0.85F);
            }
            if (isMain && item.getItem() instanceof FishingRodItem && oldAnim.oldRod.isValue()) {
                matrices.translate(xSign * -0.05F, 0.05F, -0.05F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(xSign * 8.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(8.0F));
            }
            if (oldAnim.oldBow.isValue() && item.isOf(Items.BOW) && player.isUsingItem()) {
                // In vanilla 1.21.4, a negative 45 deg Y rotation is added at the end, twisting the bow inward.
                // Rotating positive Y turns the bow outward to the right, matching 1.7/1.8 (Photo 2).
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(xSign * 45.0F));
                matrices.translate(xSign * 0.05F, 0.0F, 0.0F);
            }
        }

        // --- Change Hand Transforms ---
        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }

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

    @WrapOperation(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z"
            )
    )
    private boolean phaze$suppressShieldUsing(
            AbstractClientPlayerEntity player,
            Operation<Boolean> original,
            @Local(argsOnly = true) Hand hand,
            @Local(argsOnly = true) ItemStack item
    ) {
        if (hand == Hand.OFF_HAND && item.getItem() instanceof ShieldItem) {
            if (OldAnimations.isSwordBlockingActive(player)) {
                return false;
            }
        }
        return original.call(player);
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

    // =========================================================================
    // Old Animations: 1.7 BlockHit, Sword Blocking, 1.7 Swing
    // =========================================================================

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void phaze$handleOldSwingAndBlockHit(
            float swingProgress,
            float equipProgress,
            MatrixStack matrices,
            int armSign,
            Arm arm,
            CallbackInfo ci
    ) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }

        boolean isMainHand = (arm == mc.player.getMainArm());
        boolean isBlocking = isMainHand && OldAnimations.isSwordBlockingActive(mc.player);

        if (isBlocking) {
            if (swingProgress > 0.0F && mod.blockHit.isValue()) {
                ci.cancel();
                OldAnimations.renderBlockHit(
                        matrices,
                        arm,
                        armSign,
                        swingProgress,
                        equipProgress,
                        mod.blockHitStyle.getSelected()
                );
                return;
            } else if (mod.swordBlocking.isValue()) {
                ci.cancel();
                OldAnimations.renderSwordBlock(matrices, arm, armSign, equipProgress);
                return;
            }
        }

        if (mod.oldSwing.isValue() && swingProgress > 0.0F) {
            ci.cancel();
            OldAnimations.renderOldSwing(matrices, arm, armSign, swingProgress, equipProgress);
        }
    }

    // =========================================================================
    // Old Animations: No Re-Equip
    // =========================================================================

    @Inject(method = "shouldSkipHandAnimationOnSwap", at = @At("HEAD"), cancellable = true)
    private void phaze$noReEquipOnSwap(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod != null && mod.isEnabled() && mod.noReEquip.isValue()) {
            if (from.isOf(to.getItem())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Redirect(
            method = "updateHeldItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttackCooldownProgress(F)F"
            )
    )
    private float phaze$noReEquipOnAttackCooldown(ClientPlayerEntity player, float baseTime) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod != null && mod.isEnabled() && mod.noReEquip.isValue()) {
            return 1.0F;
        }
        return player.getAttackCooldownProgress(baseTime);
    }

    // =========================================================================
    // Old Animations: 1.7 Eating
    // =========================================================================

    @Inject(method = "applyEatOrDrinkTransformation", at = @At("HEAD"), cancellable = true)
    private void phaze$oldEatingTransform(
            MatrixStack matrices,
            float tickDelta,
            Arm arm,
            ItemStack stack,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        OldAnimations mod = OldAnimations.getInstance();
        if (mod == null || !mod.isEnabled() || !mod.oldEating.isValue()) {
            return;
        }
        ci.cancel();
        OldAnimations.renderOldEating(matrices, tickDelta, arm, stack, player);
    }
}
