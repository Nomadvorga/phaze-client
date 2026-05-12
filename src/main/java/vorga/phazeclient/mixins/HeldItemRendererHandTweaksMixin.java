package vorga.phazeclient.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
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
 * <p>The injection point is the {@code INVOKE} of
 * {@code this.renderItem(entity, stack, mode, leftHanded, ...)} inside
 * {@code renderFirstPersonItem}, with the default {@code BEFORE} shift.
 * That spot is chosen deliberately and matters a lot for the visual
 * result of the {@code Scale} slider:
 *
 * <ul>
 *   <li>By the time we reach this INVOKE, vanilla has finished its
 *       per-item positioning: {@code applyEquipOffset}, {@code swingArm}
 *       / {@code applySwingOffset}, the use-action specific transforms
 *       (eat / drink / block / bow / spear / brush) and the crossbow
 *       charging math have all been pushed onto the matrix stack.
 *       Our {@code matrices.scale(s, s, s)} therefore scales the item
 *       MODEL only - the held-item ends up at vanilla's intended
 *       camera-space position but with vertex distances multiplied
 *       by {@code s}, which is the intuitive "shrink / enlarge the
 *       sword in my hand" behaviour.</li>
 *   <li>The previous implementation injected right after
 *       {@code matrices.push()} at the top of the method - that ran
 *       BEFORE the vanilla translates, so a scale of 0.1 also shrank
 *       the equip offset translate from ~0.72 m forward down to 7 cm.
 *       Perspective then projected the now-tiny mesh from inches
 *       in front of the lens onto the entire screen, producing the
 *       "my sword is huge and pixelated" report (see issue: user
 *       screenshot with Main Scale = 0.1 painting the sword across
 *       half the viewport). Moving the inject past vanilla's translates
 *       fixes that: scale only ever multiplies the item vertices, never
 *       the distance to the camera.</li>
 *   <li>Both call sites of {@code renderItem} inside
 *       {@code renderFirstPersonItem} (the crossbow branch and the
 *       general item branch) match this INVOKE target, so the inject
 *       fires once per branch automatically without needing per-branch
 *       redirects.</li>
 *   <li>Empty hand ({@code renderArmHoldingItem}) and map renders
 *       ({@code renderMapInOneHand} / {@code renderMapInBothHands})
 *       are intentionally NOT scaled - there's no item mesh in the
 *       empty-hand case, and scaling a held map is rarely what users
 *       want. Adding those would only multiply the injection points
 *       without a clear use-case win.</li>
 * </ul>
 *
 * <p>We do nothing when the module is disabled or all the relevant
 * sliders are at their default values - this is the common path on
 * every frame for every player, so the early return matters.
 *
 * <p>Order of operations inside the inject body is {@code translate}
 * BEFORE {@code scale} so the offset slider reads in unscaled units.
 * In OpenGL post-multiplication terms: {@code M = M_vanilla * T_user *
 * S}, applied to vertex {@code v} gives {@code T_vanilla(T_user(S*v))}.
 * Vertex {@code (0,0,0)} ends up at vanilla position + user offset;
 * non-zero vertices spread by {@code s} around that anchor.
 *
 * <p><b>X mirroring when the main arm is flipped from vanilla default:</b>
 * vanilla's per-branch transforms (equip offset, swing arc, eat/drink,
 * crossbow tilt, ...) all multiply their X component by
 * {@code (arm == Arm.RIGHT ? 1 : -1)} so the whole presentation mirrors
 * across the screen centre when the player swaps to a left main arm.
 * Our user-X slider was tuned in that mirrored coordinate space, so on
 * the flipped side a raw {@code translate(+0.33, 0, 0)} now points the
 * opposite way relative to the body (it used to nudge the sword outward,
 * after the flip it nudges it inward across the chest). Multiplying
 * user X by the same {@code +/-1} sign as vanilla restores the
 * "outward / inward" intent across both arm sides without forcing the
 * user to retype their slider every time they hit Bind / Upon Impact.
 * Y and Z aren't mirrored because vanilla doesn't mirror them either,
 * and Scale stays untouched because a uniform scale is sign-invariant.
 * The GUI value isn't changed - the slider still reads {@code 0.33},
 * we just consume the value sign-corrected on the render side.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererHandTweaksMixin {

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

        // Mirror X when the player's main arm has been flipped off the
        // vanilla default (Right). Vanilla already mirrors its own
        // translates via the (arm == Right ? 1 : -1) sign, so without
        // matching that sign on the user-X slider the tuned offset
        // would point the wrong way after a ChangeHand flip.
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
}
