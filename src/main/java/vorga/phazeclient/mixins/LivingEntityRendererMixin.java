/*
 * Includes ported logic from the "hitrange" mod by uku3lig (uku),
 * https://github.com/uku3lig/hitrange, MIT License. See per-method
 * comments below for attribution.
 */
package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.hud.NametagHud;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;
import vorga.phazeclient.implement.hitcolor.OverlayReloadListener;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

/**
 * Consolidated mixin for {@link LivingEntityRenderer}, merging:
 * <ul>
 *   <li>HitColor overlay-renderer hook (existing class generics
 *       preserved).</li>
 *   <li>NametagHud self-label visibility override.</li>
 *   <li>HitRange per-player circle (ported from
 *       <a href="https://github.com/uku3lig/hitrange">uku's hitrange</a>,
 *       MIT). See per-method comment.</li>
 * </ul>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends net.minecraft.client.model.Model> {

    @Shadow
    protected abstract float getAnimationCounter(S state);

    // ---------------------------------------------------------------
    // HitColor: capture the overlay int into FeatureRenderer instances
    // that implement OverlayRendered.
    // ---------------------------------------------------------------

    @Inject(
            method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V",
                    ordinal = 0
            )}
    )
    private void phaze$captureHitColorOverlay(S livingEntityRenderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci, @Local FeatureRenderer<?, ?> featureRenderer) {
        if (HitColor.getInstance().isEnabled() && featureRenderer instanceof OverlayRendered rendered) {
            int overlay = LivingEntityRenderer.getOverlay(livingEntityRenderState, this.getAnimationCounter(livingEntityRenderState));
            rendered.setOverlay(overlay);
            OverlayReloadListener.event();
        }
    }

    // ---------------------------------------------------------------
    // NametagHud: force own-nametag visibility under HUD-hidden /
    // perspective constraints.
    // ---------------------------------------------------------------

    @Inject(
            method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void phaze$forceOwnNametagVisibility(LivingEntity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        NametagHud module = NametagHud.getInstance();
        if (!module.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options == null) {
            return;
        }

        boolean isSelf = entity == client.player || entity == client.getCameraEntity();
        if (!isSelf) {
            return;
        }

        if (module.hideInF1.isValue() && client.options.hudHidden) {
            cir.setReturnValue(false);
            return;
        }

        if (!module.thirdPersonNametag.isValue() && !client.options.getPerspective().isFirstPerson()) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    // ---------------------------------------------------------------
    // HitRange (per-player circle) — ported from uku's hitrange (MIT).
    // ---------------------------------------------------------------

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL")
    )
    private void phaze$drawHitRange(
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled()) {
            return;
        }

        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        // Local player goes through WorldRendererMixin's renderEntities
        // TAIL path which knows the camera-relative offset; skip here
        // so Show Self doesn't draw two stacked rings.
        if (player.getId() == playerState.id) {
            return;
        }

        Vec3d pos = new Vec3d(state.x, state.y, state.z);
        if (!pos.isInRange(player.getPos(), config.maxDistance.getInt())) {
            return;
        }

        if (config.nearestOnly.isValue()) {
            if (config.getNearest() == null || config.getNearest().getId() != playerState.id) {
                return;
            }
        }

        if (playerState.deathTime > 0.0f || playerState.invisibleToPlayer || playerState.sleepingDirection != null) {
            return;
        }

        HitRangeCircleRenderer.drawCircle(matrices, vertexConsumers, playerState);
    }
}
