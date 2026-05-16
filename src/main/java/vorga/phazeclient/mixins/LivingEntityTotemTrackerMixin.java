package vorga.phazeclient.mixins;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

/**
 * Forwards every {@code USE_TOTEM_OF_UNDYING} entity status (id 35)
 * dispatched to a {@link LivingEntity} into
 * {@link TotemTracker#recordTotemUse(LivingEntity)}.
 *
 * <h3>Why hook handleStatus and not the packet</h3>
 * Vanilla's network funnel is
 * {@code ClientPlayNetworkHandler.onEntityStatus} which calls the
 * world's {@code handleStatus} which then calls the entity's own
 * {@code handleStatus(byte)}. Hooking the entity-level method gives
 * us:
 * <ol>
 *   <li>An already-resolved {@link LivingEntity} instance (no entity
 *       id -&gt; entity lookup needed).</li>
 *   <li>Coverage of the singleplayer integrated server too, where
 *       the integrated server fires {@code World.sendEntityStatus}
 *       which directly invokes {@code handleStatus} on the client
 *       side without going through the packet handler.</li>
 *   <li>A trivially-stable mixin descriptor: {@code (B)V} hasn't
 *       changed since the method was introduced and isn't going to
 *       change because it's the inherited entity contract.</li>
 * </ol>
 *
 * <p>The status value is checked first thing so non-totem statuses
 * (damage flash, sleep, mob-conversion, etc.) skip the tracker call
 * entirely on the same frame they arrive.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemTrackerMixin {

    @Inject(method = "handleStatus(B)V", at = @At("HEAD"), require = 0)
    private void phaze$captureTotemPop(byte status, CallbackInfo ci) {
        if (status != TotemTracker.STATUS_USE_TOTEM) {
            return;
        }
        TotemTracker tracker = TotemTracker.getInstance();
        if (tracker == null) {
            return;
        }
        // (Object) cast is the standard Mixin idiom for "treat the
        // mixin target as its declared class" - the compiler sees us
        // as the abstract Mixin holder, but at runtime we ARE the
        // LivingEntity instance.
        tracker.recordTotemUse((LivingEntity) (Object) this);
    }
}
