package vorga.phazeclient.mixins;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ItemPickupLogger;

import java.util.Iterator;

/**
 * Hooks the vanilla packet that informs the client an item entity has been
 * collected by some living entity (typically the local player or another
 * mob). When the collector is the local player and the picked-up entity is
 * still resolvable as an {@link ItemEntity} (it gets removed inside this
 * vanilla method, so we run at HEAD), we forward the stack and pickup amount
 * to {@link ItemPickupLogger}.
 *
 * <p>We deduplicate by item-entity ID inside a 2 second sliding window. The
 * server occasionally sends the pickup animation packet more than once for
 * the same entity (especially on integrated servers where local and remote
 * pickup paths can both fire), and the underlying vanilla method tolerates
 * that gracefully by no-oping the second call - but our chat log would
 * happily print the message twice. Tracking the entityId we've already
 * logged collapses these duplicates without affecting genuinely repeated
 * pickups (those have unique item-entity IDs since each ItemEntity is its
 * own entity in the world).
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerItemPickupMixin {

    @Unique private static final long PHAZE_DUP_WINDOW_MS = 2000L;
    @Unique private final Int2LongOpenHashMap phaze$recentEntityIds = new Int2LongOpenHashMap();

    @Inject(method = "onItemPickupAnimation", at = @At("HEAD"))
    private void phaze$logPickup(ItemPickupAnimationS2CPacket packet, CallbackInfo ci) {
        ItemPickupLogger logger = ItemPickupLogger.getInstance();
        if (logger == null || !logger.isEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        if (packet.getCollectorEntityId() != mc.player.getId()) {
            return;
        }

        int entityId = packet.getEntityId();
        long now = System.currentTimeMillis();
        long lastSeen = phaze$recentEntityIds.getOrDefault(entityId, 0L);
        if (lastSeen != 0L && now - lastSeen < PHAZE_DUP_WINDOW_MS) {
            // Same item entity reported again within the dedupe window -
            // either a re-sent packet or a second hook firing. Skip.
            return;
        }
        phaze$recentEntityIds.put(entityId, now);

        // Cheap eviction: drop expired entries opportunistically when the
        // map grows past a threshold. Keeps memory bounded without needing
        // a scheduled cleanup task.
        if (phaze$recentEntityIds.size() > 64) {
            Iterator<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> it = phaze$recentEntityIds.int2LongEntrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getLongValue() > PHAZE_DUP_WINDOW_MS) {
                    it.remove();
                }
            }
        }

        Entity entity = mc.world.getEntityById(entityId);
        if (entity instanceof ItemEntity itemEntity) {
            logger.onPickup(itemEntity.getStack(), packet.getStackAmount());
        }
    }
}
