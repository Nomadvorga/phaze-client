package vorga.phazeclient.mixins;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.base.util.RemoteRulesService;
import vorga.phazeclient.base.util.ServerUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.hud.ComboCounterHud;
import vorga.phazeclient.implement.features.modules.other.AutoNear;
import vorga.phazeclient.implement.features.modules.other.AutoReissue;
import vorga.phazeclient.implement.features.modules.other.BattleInfo;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.LockSlot;
import vorga.phazeclient.implement.features.modules.other.MouseClicker;
import vorga.phazeclient.implement.features.modules.other.PotionAuto;
import vorga.phazeclient.implement.features.modules.other.ShiftTap;
import vorga.phazeclient.implement.features.modules.other.TotemTracker;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    /**
     * Last server host observed on the client tick. {@code null} means
     * "not yet sampled" - distinct from {@code ""} which is the legal
     * value returned by {@link ServerUtil#getCurrentServerHost()} when
     * the player is in singleplayer / on the main menu. We use {@code
     * null} as the initial sentinel so the first tick after startup
     * always registers as a "change" and we kick off the rules refresh
     * for the actual current host, even if that host is empty.
     */
    private static String phaze$lastObservedHost = null;

    @Inject(method = "requestRespawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        ComboCounterHud.getInstance().onWorldJoin();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        phaze$detectHostChange();
        phaze$enforceServerLocks();
        ShiftTap.getInstance().onTick();
        AutoNear.getInstance().tick();
        PotionAuto.getInstance().tick();
        FreeLook.getInstance().tick();
        AutoReissue.getInstance().tick();
        MouseClicker.getInstance().onTick();
        // Battle Info samples per-tick metrics (ping / saturation /
        // combo timeout) - cheap enough to run unconditionally; the
        // module returns immediately when disabled, see
        // {@code BattleInfo.tick}.
        BattleInfo.getInstance().tick();
        // Totem Tracker prunes stale per-player counters once per
        // tick. Also a no-op when disabled or when the user-set
        // reset cooldown is 0 (the default).
        TotemTracker.getInstance().pruneStaleEntries();
    }

    /**
     * Out-of-band trigger for {@link RemoteRulesService#requestRefresh()}.
     *
     * <p>{@link RemoteRulesService} runs its own scheduler at a 60-second
     * cadence ({@code HEARTBEAT_SECONDS=60}) - that's the cap on how
     * often we'll hit the rules API for a player who stays on the same
     * server. But if the player swaps servers between heartbeats, we
     * don't want them to keep applying the OLD server's lock list for
     * up to a minute on the new server (or, worse, NO lock list if
     * they came from singleplayer). Watching the host string on every
     * client tick is essentially free (~20Hz string compare) and lets
     * us shove a refresh request straight into the rules-service queue
     * the moment a transition is detected.
     *
     * <p>{@link RemoteRulesService#requestRefresh()} itself is rate-
     * limited / coalesced internally, so spamming this from a hostile
     * client mod cannot blow up our API budget - it'll just collapse
     * down to one in-flight request per scheduler turn.
     */
    private static void phaze$detectHostChange() {
        String current = ServerUtil.getCurrentServerHost();
        if (current == null) {
            current = "";
        }
        if (!current.equals(phaze$lastObservedHost)) {
            phaze$lastObservedHost = current;
            RemoteRulesService.getInstance().requestRefresh();
        }
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void phaze$onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LockSlot lockSlot = LockSlot.getInstance();
        if (lockSlot == null || !lockSlot.isEnabled()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        PlayerInventory inventory = self.getInventory();
        if (lockSlot.isHotbarSlotLocked(inventory.selectedSlot)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    private static void phaze$enforceServerLocks() {
        Main main = Main.getInstance();
        if (main == null || main.getModuleProvider() == null) {
            return;
        }

        for (Module module : main.getModuleProvider().getModules()) {
            if (module.isShowEnable() && module.isState() && module.isServerLocked()) {
                module.setState(false);
            }
        }
    }
}
