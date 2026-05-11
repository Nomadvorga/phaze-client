package vorga.phazeclient.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;

/**
 * Applies the user-configured X/Y/Z offset and uniform scale from the
 * {@link ChangeHand} module to the first-person held-item matrix.
 *
 * <p>The injection point is right after vanilla's own
 * {@code matrices.push()} at the top of {@code renderFirstPersonItem}.
 * That spot is chosen deliberately:
 *
 * <ul>
 *   <li>The push has already happened, so any matrix mutations we do
 *       here are still scoped to this hand's render and get popped
 *       cleanly at the matching {@code matrices.pop()} - no risk of
 *       leaking the transform into world geometry.</li>
 *   <li>We run BEFORE the vanilla item-specific transforms
 *       (equip offset, swing, eat/drink/block animations, crossbow
 *       charging, ...). Vanilla's transforms therefore operate in the
 *       scaled / translated coordinate space we set up, which is what
 *       lets a single uniform scale shrink the entire held-item
 *       presentation - arm model, item model, AND swing distances -
 *       proportionally. Injecting AFTER those transforms would only
 *       scale the item geometry while leaving the swing arc and equip
 *       offset at vanilla magnitudes, which looks broken.</li>
 *   <li>Spyglass first-person rendering bails out before the push
 *       (see {@code if (!player.isUsingSpyglass())} guard around the
 *       whole body), so our inject never runs while scoping with the
 *       spyglass and can't disturb that specialised pipeline.</li>
 * </ul>
 *
 * <p>We do nothing when the module is disabled or all the relevant
 * sliders are at their default values - this is the common path on
 * every frame for every player, so the early return matters.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererHandTweaksMixin {

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                    shift = At.Shift.AFTER
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

        boolean isMain = hand == Hand.MAIN_HAND;
        if (isMain) {
            if (!mod.hasMainHandTransform()) return;
            matrices.translate(
                    mod.mainHandX.getValue(),
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
                    mod.offHandX.getValue(),
                    mod.offHandY.getValue(),
                    mod.offHandZ.getValue()
            );
            float scale = mod.offHandScale.getValue();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
        }
    }
}
