package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;

/**
 * Detects "air swings" - left-clicks that don't connect with any
 * entity - so the {@link ReachHud} can reset its displayed value
 * back to {@code 0 blocks} as soon as the player misses.
 *
 * <p>The companion {@code ClientPlayerInteractionManagerMixin} only
 * fires on successful {@code attackEntity} calls, so a miss never
 * reaches it. Hooking {@code MinecraftClient#doAttack} HEAD gives us
 * a single observation point for every left-click: by inspecting the
 * client's {@code crosshairTarget} we can tell whether the click is
 * about to land on an entity or whether it'll merely produce a swing
 * animation in the air.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientDoAttackMixin {

    @Shadow
    public HitResult crosshairTarget;

    /**
     * Air-swing detector. Runs BEFORE vanilla's own attack logic, so
     * when the crosshair isn't on an entity we can clear the reach
     * value immediately - vanilla will then proceed to call
     * {@code player.swingHand} (cosmetic only) and our HUD stays
     * showing {@code 0 blocks} until the next successful hit.
     *
     * <p>For entity hits we deliberately do NOTHING here - that path
     * is handled by {@code ClientPlayerInteractionManagerMixin}'s
     * {@code attackEntity} inject, which has access to the resolved
     * {@code target} and the player and can compute the precise reach
     * distance via {@link ReachHud#recordHitDistance}.
     */
    @Inject(method = "doAttack", at = @At("HEAD"))
    private void phaze$reachHudAirSwingReset(CallbackInfoReturnable<Boolean> cir) {
        ReachHud reachHud = ReachHud.getInstance();
        if (!reachHud.isEnabled()) {
            return;
        }
        // Treat "no crosshairTarget" the same as "crosshair on air":
        // both produce a swing animation but no damage. A block hit
        // also collapses to a reset because the player is clearly not
        // attacking an entity in that frame.
        if (crosshairTarget == null || crosshairTarget.getType() != HitResult.Type.ENTITY) {
            reachHud.notifyAirSwing();
        }
    }

    /**
     * Defensive stub - kept here in case a future Mixin processor
     * complains about the file having no non-RETURN-value injects.
     * Currently unused; the actual logic lives in
     * {@link #phaze$reachHudAirSwingReset(CallbackInfoReturnable)}.
     */
    @SuppressWarnings("unused")
    private void phaze$noop(CallbackInfo ci) {
    }
}
