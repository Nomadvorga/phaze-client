package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Predictions;

/**
 * Forwards every freshly-spawned {@link ProjectileEntity} to
 * {@link Predictions#trackProjectile(ProjectileEntity)} so the
 * Predictions module can paint a flight trail behind any in-flight
 * projectile (snowball, arrow, pearl, trident, splash potion, etc).
 *
 * <p>Same hook strategy as the snowball-tracker mixin: TAIL on
 * {@code onEntitySpawn}, main-thread guard via
 * {@link MinecraftClient#isOnThread()}.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerProjectileTrailMixin {

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    private void phaze$captureProjectileSpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        Predictions module = Predictions.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || !mc.isOnThread()) {
            return;
        }
        Entity entity = mc.world.getEntityById(packet.getEntityId());
        if (entity instanceof ProjectileEntity projectile) {
            module.trackProjectile(projectile);
        }
    }
}
