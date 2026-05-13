package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Clears {@link ChunkAnimator}'s {@code firstSeenMs} tracker on
 * the two events that actually change which {@link net.minecraft.world.World}
 * instance the client is rendering:
 *
 * <ul>
 *   <li>{@code onGameJoin} - initial server join, a fresh
 *       {@code ClientWorld} is constructed.</li>
 *   <li>{@code onPlayerRespawn} - respawn after death AND
 *       dimension change (the same S2C packet drives both). A new
 *       {@code ClientWorld} is created for the new dimension.</li>
 * </ul>
 *
 * <p>Why <em>not</em> hook {@code WorldRenderer.reload()} (the
 * obvious F3+A path): Iris fires that method in bursts on every
 * pipeline rebuild - shader pack load, shader settings change,
 * sun angle update, even some camera transitions. The user
 * observed up to ~10 reloads in a few seconds when enabling a
 * shader pack, which made every newly-animating chunk snap back to
 * full distance and replay (because {@code firstSeenMs.clear()}
 * removed its first-seen timestamp). The packet-level hook fires
 * exactly once per real world swap and is invisible to Iris's
 * render pipeline, so the per-section animation runs to completion
 * uninterrupted.
 *
 * <p>F3+A no longer re-triggers animations - that's an accepted
 * regression. F3+A is a debug rebuild path, not a "replay
 * animation" feature, and trying to detect it specifically would
 * require yet another hook on {@code Keyboard#processF3} that
 * distinguishes user-initiated reloads from Iris-initiated ones.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerChunkAnimatorResetMixin {

    @Inject(method = "onGameJoin", at = @At("HEAD"))
    private void phaze$chunkAnimatorOnGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        ChunkAnimator.getInstance().resetTracker();
    }

    @Inject(method = "onPlayerRespawn", at = @At("HEAD"))
    private void phaze$chunkAnimatorOnPlayerRespawn(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        ChunkAnimator.getInstance().resetTracker();
    }
}
