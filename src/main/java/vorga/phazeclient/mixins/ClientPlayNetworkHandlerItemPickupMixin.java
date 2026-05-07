package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ItemPickupLogger;

/**
 * Hooks the vanilla packet that informs the client an item entity has been
 * collected by some living entity (typically the local player or another
 * mob). When the collector is the local player and the picked-up entity is
 * still resolvable as an {@link ItemEntity} (it gets removed inside this
 * vanilla method, so we run at HEAD), we forward the stack and pickup amount
 * to {@link ItemPickupLogger}.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerItemPickupMixin {

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

        Entity entity = mc.world.getEntityById(packet.getEntityId());
        if (entity instanceof ItemEntity itemEntity) {
            logger.onPickup(itemEntity.getStack(), packet.getStackAmount());
        }
    }
}
