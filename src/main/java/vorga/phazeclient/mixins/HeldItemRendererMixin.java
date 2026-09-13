package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"
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
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {
        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }

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
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"
            )
    )
    private boolean phaze$skipHandSway(MatrixStack instance, Quaternionfc rotation) {
        ChangeHand mod = ChangeHand.getInstance();
        if (mod == null || !mod.isEnabled() || !mod.noHandSway.isValue()) {
            return true;
        }
        return false;
    }
}
