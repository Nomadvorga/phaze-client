package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.FTHelper;

/**
 * Forwards every freshly-spawned {@link SnowballEntity} to
 * {@link FTHelper#trackSnowball(SnowballEntity)} so the FT helper
 * can decide whether to keep watching its trajectory and paint a
 * "снежок заморозки" indicator under it.
 *
 * <h3>Why hook onEntitySpawn TAIL</h3>
 * By the time the inject fires at TAIL the entity has been
 * resolved and added to {@code ClientWorld}, so we can pull it via
 * {@code packet.getEntityId} -&gt; {@code world.getEntityById}
 * without racing the spawn pipeline. We rely on the same
 * {@code MinecraftClient.isOnThread} guard that the totem-tracker
 * mixin uses to avoid double-firing during the
 * {@code NetworkThreadUtils.forceMainThread} replay - vanilla's
 * onEntitySpawn calls forceMainThread first, so the network-thread
 * pass is aborted by an exception before TAIL runs anyway, but the
 * extra check keeps the hook robust if vanilla ever changes.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerSnowballTrackerMixin {

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    private void phaze$captureSnowballSpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        FTHelper module = FTHelper.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || !mc.isOnThread()) {
            return;
        }
        Entity entity = mc.world.getEntityById(packet.getEntityId());
        if (entity instanceof SnowballEntity snowball) {
            module.trackSnowball(snowball);
        }
    }
}
