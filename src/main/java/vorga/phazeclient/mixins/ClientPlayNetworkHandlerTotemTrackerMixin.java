package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

/**
 * Forwards every {@code USE_TOTEM_OF_UNDYING} entity status (id 35)
 * arriving in {@link EntityStatusS2CPacket} into
 * {@link TotemTracker#recordTotemUse(LivingEntity)}.
 *
 * <h3>Why hook the network handler instead of {@code LivingEntity.handleStatus}</h3>
 * In 1.21.4 {@code ClientPlayNetworkHandler.onEntityStatus} owns the
 * switch over the incoming status byte and only falls through to
 * {@code entity.handleStatus(status)} in the {@code default:} branch.
 * Status 35 (totem pop) has its own explicit case there - it spawns
 * the totem particle emitter and plays the totem-use sound directly
 * on the network handler and never invokes {@code handleStatus}
 * on the entity. A {@code @Inject(method = "handleStatus", ...)} on
 * {@link LivingEntity} therefore never fires for totems on a real
 * server, which is why the previous tracker mixin missed every pop.
 *
 * <p>Hooking {@link ClientPlayNetworkHandler#onEntityStatus} at HEAD
 * gives us:
 * <ol>
 *   <li>Coverage of every status the server actually sends, including
 *       the ones the network handler short-circuits before reaching
 *       the entity.</li>
 *   <li>The same {@link Entity} instance vanilla resolves a few lines
 *       later via {@code packet.getEntity(world)}; we resolve it
 *       ourselves once so the tracker call doesn't depend on vanilla
 *       executing first.</li>
 *   <li>A stable injection point: the method signature
 *       {@code onEntityStatus(EntityStatusS2CPacket)} is mandated by
 *       the {@code ClientPlayPacketListener} interface and cannot
 *       silently change between minor releases.</li>
 * </ol>
 *
 * <p>The status byte is checked first so non-totem statuses (damage
 * flash, sleep, mob-conversion, etc.) skip the tracker call entirely
 * on the same frame they arrive.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerTotemTrackerMixin {

    // ClientPlayNetworkHandler stores its ClientWorld locally; the
    // MinecraftClient reference lives on the parent class
    // ClientCommonNetworkHandler, so we pull it from
    // MinecraftClient.getInstance() instead of @Shadow-ing across
    // the inheritance chain (matches the pattern used by
    // ClientPlayNetworkHandlerItemPickupMixin in this project).
    @Shadow private ClientWorld world;

    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void phaze$captureTotemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (packet.getStatus() != TotemTracker.STATUS_USE_TOTEM) {
            return;
        }
        TotemTracker tracker = TotemTracker.getInstance();
        if (tracker == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        // Vanilla onEntityStatus starts with NetworkThreadUtils.forceMainThread,
        // which - when invoked from the network thread - schedules the listener
        // to be re-run on the client thread and aborts the current invocation
        // by throwing. An @Inject(at = HEAD) therefore fires twice for every
        // packet: once on the network thread (before forceMainThread throws)
        // and once on the client thread after the re-dispatch. Counting both
        // would inflate the loss counter by 2 per real pop, which is exactly
        // what the user reported. Filtering to the client thread leaves a
        // single firing per packet without us having to dedupe by entity id.
        if (!mc.isOnThread()) {
            return;
        }
        // The packet handler runs on the network thread; the world
        // reference can momentarily be null between disconnect and
        // the handler's own null-checks fire. Mirror vanilla's guard
        // exactly so we don't NPE before the tracker call.
        ClientWorld w = this.world;
        if (w == null) {
            return;
        }
        Entity entity = packet.getEntity(w);
        if (entity instanceof LivingEntity living) {
            tracker.recordTotemUse(living);
        }
    }
}
