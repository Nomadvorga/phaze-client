package vorga.phazeclient.mixins;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChatHelper;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;
import vorga.phazeclient.implement.features.modules.other.FTHelper;
import vorga.phazeclient.implement.features.modules.other.ItemPickupLogger;
import vorga.phazeclient.implement.features.modules.other.MentionHighlight;
import vorga.phazeclient.implement.features.modules.other.Predictions;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

import java.util.Iterator;

/**
 * Consolidated mixin for {@link ClientPlayNetworkHandler}, merging the
 * previous seven sibling mixins (ChunkAnimatorReset, AntiCaps, TotemTracker,
 * SnowballTracker, ProjectileTrail, MentionSelfSkip, ItemPickup). Each
 * original injector is preserved with a unique {@code phaze$} method name.
 * Shadow fields and unique state are merged at the top of the class.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    // ---------------------------------------------------------------
    // Shared shadow / unique state
    // ---------------------------------------------------------------

    @Shadow private ClientWorld world;

    @Unique private static final long PHAZE_DUP_WINDOW_MS = 2000L;
    @Unique private final Int2LongOpenHashMap phaze$recentEntityIds = new Int2LongOpenHashMap();

    // ---------------------------------------------------------------
    // ChunkAnimatorReset
    // ---------------------------------------------------------------

    @Inject(method = "onGameJoin", at = @At("HEAD"))
    private void phaze$chunkAnimatorOnGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        ChunkAnimator.getInstance().resetTracker();
    }

    @Inject(method = "onPlayerRespawn", at = @At("HEAD"))
    private void phaze$chunkAnimatorOnPlayerRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        ChunkAnimator.getInstance().resetTracker();
    }

    // ---------------------------------------------------------------
    // AntiCaps (outgoing chat)
    // ---------------------------------------------------------------

    @ModifyVariable(
            method = "sendChatMessage(Ljava/lang/String;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String phaze$lowercaseOutgoing(String content) {
        ChatHelper helper = ChatHelper.getInstance();
        if (helper == null) {
            return content;
        }
        return helper.maybeAntiCaps(content);
    }

    // ---------------------------------------------------------------
    // MentionSelfSkip (arm latch on outgoing)
    // ---------------------------------------------------------------

    @Inject(
            method = "sendChatMessage(Ljava/lang/String;)V",
            at = @At("HEAD")
    )
    private void phaze$markOutgoingChat(String content, CallbackInfo ci) {
        MentionHighlight.getInstance().markOutgoing(content);
    }

    @Inject(
            method = "sendChatCommand(Ljava/lang/String;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void phaze$markOutgoingCommand(String command, CallbackInfo ci) {
        MentionHighlight.getInstance().markOutgoing();
    }

    // ---------------------------------------------------------------
    // TotemTracker
    // ---------------------------------------------------------------

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
        // Filter to the client thread to avoid the double-fire that
        // NetworkThreadUtils.forceMainThread induces at HEAD.
        if (!mc.isOnThread()) {
            return;
        }
        ClientWorld w = this.world;
        if (w == null) {
            return;
        }
        Entity entity = packet.getEntity(w);
        if (entity instanceof LivingEntity living) {
            tracker.recordTotemUse(living);
        }
    }

    // ---------------------------------------------------------------
    // SnowballTracker / ProjectileTrail (share onEntitySpawn TAIL)
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // ItemPickupLogger
    // ---------------------------------------------------------------

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
            return;
        }
        phaze$recentEntityIds.put(entityId, now);

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
