/*
 * Includes ported logic from the "hitrange" mod by uku3lig (uku),
 * https://github.com/uku3lig/hitrange, MIT License. See per-method
 * comments below for attribution.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.hud.BatchedHudBuffer;
import vorga.phazeclient.api.system.hud.HudBuffer;
import vorga.phazeclient.implement.features.modules.hud.ReachHud;
import vorga.phazeclient.implement.features.modules.other.FakeFps;
import vorga.phazeclient.implement.features.modules.other.FastExp;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.features.modules.other.NoRender;

/**
 * Consolidated mixin for {@link MinecraftClient}, merging the previous
 * six sibling mixins (NoGlow, HitRange, FastExp, Framebuffer, DoAttack,
 * FakeFps). Each original injector is preserved with a unique
 * {@code phaze$} method name; shadow fields are combined.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    // ---------------------------------------------------------------
    // Shared shadows
    // ---------------------------------------------------------------

    @Shadow private int itemUseCooldown;

    @Shadow public HitResult crosshairTarget;

    @Shadow @Mutable private static int currentFps;

    // ---------------------------------------------------------------
    // NoGlow: cancel hasOutline when toggle is on
    // ---------------------------------------------------------------

    @Inject(method = "hasOutline(Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$skipGlowOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        NoRender mod = NoRender.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        if (mod.glowing.isValue()) {
            cir.setReturnValue(false);
        }
    }

    // ---------------------------------------------------------------
    // HitRange: per-tick nearest-player snapshot
    //
    // Ported from the "hitrange" mod by uku3lig (MIT). See file header
    // for original copyright and the modifications notice.
    // ---------------------------------------------------------------

    @Inject(method = "tick", at = @At("TAIL"))
    private void phaze$updateHitRangeNearest(CallbackInfo ci) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled() || !config.nearestOnly.isValue()) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        PlayerEntity nearest = player.getWorld().getClosestPlayer(
                player.getX(),
                player.getY(),
                player.getZ(),
                config.maxSearchDistance.getInt(),
                e -> !e.equals(player)
        );
        config.setNearest(nearest);
    }

    // ---------------------------------------------------------------
    // FastExp: zero out the item-use cooldown each input tick
    // ---------------------------------------------------------------

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void phaze$bypassExperienceBottleCooldown(CallbackInfo ci) {
        if (FastExp.shouldFastThrow()) {
            this.itemUseCooldown = 0;
        }
    }

    // ---------------------------------------------------------------
    // Framebuffer: redirect getFramebuffer during HUD batch capture
    // ---------------------------------------------------------------

    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void phaze$redirectFramebufferToBatchCapture(CallbackInfoReturnable<Framebuffer> cir) {
        if (HudBuffer.activeCaptureTarget < 0) {
            return;
        }
        SimpleFramebuffer fbo = BatchedHudBuffer.INSTANCE.getActiveCaptureFramebuffer();
        if (fbo != null) {
            cir.setReturnValue(fbo);
        }
    }

    // ---------------------------------------------------------------
    // DoAttack: notify ReachHud on air-swing
    // ---------------------------------------------------------------

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void phaze$reachHudAirSwingReset(CallbackInfoReturnable<Boolean> cir) {
        ReachHud reachHud = ReachHud.getInstance();
        if (!reachHud.isEnabled()) {
            return;
        }
        if (crosshairTarget == null || crosshairTarget.getType() != HitResult.Type.ENTITY) {
            reachHud.notifyAirSwing();
        }
    }

    // ---------------------------------------------------------------
    // FakeFps: rewrite getCurrentFps + the static field
    // ---------------------------------------------------------------

    @Inject(method = "getCurrentFps", at = @At("HEAD"), cancellable = true)
    private void phaze$fakeFps(CallbackInfoReturnable<Integer> cir) {
        FakeFps module = FakeFps.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        int fake = module.getFakeFps();
        currentFps = fake;
        cir.setReturnValue(fake);
    }

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void phaze$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Phaze Client* 1.21.4");
    }
}
