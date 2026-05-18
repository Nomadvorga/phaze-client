package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.ChangeHand;

/**
 * Suppresses vanilla's camera-following hand sway when the
 * {@link ChangeHand#noHandSway} toggle is on.
 *
 * <h3>What "hand sway" means</h3>
 * Inside the second overload of
 * {@link HeldItemRenderer#renderItem(float, net.minecraft.client.util.math.MatrixStack,
 * net.minecraft.client.render.VertexConsumerProvider.Immediate,
 * net.minecraft.client.network.ClientPlayerEntity, int)}
 * vanilla issues two
 * {@code matrices.multiply(Quaternionf)} calls fed by the difference
 * between the player's previous and current yaw / pitch:
 *
 * <pre>{@code
 * matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - h) * 0.1F));
 * matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta)   - i) * 0.1F));
 * }</pre>
 *
 * That tiny rotation is what makes the held item lag behind the
 * camera when the player turns - the visual "weight" of the weapon.
 *
 * <h3>Why WrapWithCondition</h3>
 * MixinExtras's {@link WrapWithCondition} runs the wrapped call only
 * when the handler returns {@code true}. We return {@code false}
 * when the No Hand Sway toggle is on, which silently skips both
 * {@code multiply} calls (the descriptor matches both because they
 * share the same target signature). The matrix entering and leaving
 * this section is otherwise identical, so suppressing only the sway
 * rotations leaves the rest of the held-item pipeline (equip
 * offset, swing arc, use-action transforms, the
 * {@link HeldItemRendererHandTweaksMixin} translate / scale) intact.
 *
 * <h3>Why this method, not renderFirstPersonItem</h3>
 * The sway lives in the OUTER overload; the inner
 * {@code renderFirstPersonItem} path runs after the sway has already
 * been applied to the matrix stack. Hooking the outer method is
 * therefore the cleanest spot - it stops the rotation from ever
 * being pushed in the first place, so we don't have to undo it
 * later.
 *
 * <h3>Disabled-fast-path</h3>
 * When the module or the toggle is off, the wrapper returns
 * {@code true} and vanilla runs unchanged. The cost on the disabled
 * path is two getter calls + a boolean compare per held-item frame,
 * which is essentially free.
 *
 * <h3>Attribution</h3>
 * Adapted from {@code logwan.nohandsway.mixin.NoSwayMixin} by O3kar
 * (Apache License 2.0). See {@code THIRD_PARTY_LICENSES.md} at the
 * project root for the full notice.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererNoSwayMixin {

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
