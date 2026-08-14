/*
 * Includes ported logic from the "hitrange" mod by uku3lig (uku),
 * https://github.com/uku3lig/hitrange, MIT License. See per-method
 * comments below for attribution.
 */
package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.state.CameraRenderState;
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
import vorga.phazeclient.implement.cosmetics.CosmeticsRenderer;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;

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
 *
 * <p>1.21.11: entity rendering is now a two-phase, deferred pipeline.
 * {@code LivingEntityRenderer.render} and {@code FeatureRenderer.render} no
 * longer receive a {@link VertexConsumerProvider}; they receive an
 * {@link OrderedRenderCommandQueue} that collects draw commands, and
 * {@code LivingEntityRenderer.render} also gained a trailing
 * {@link CameraRenderState}. Both injection descriptors below were re-read
 * from the 1.21.11 bytecode; the surrounding Phaze logic is unchanged.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow
    protected abstract float getAnimationCounter(S state);

    /** Attaches Phaze cosmetics in the same already-transformed body space as the vanilla player model. */
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V", shift = At.Shift.BEFORE)
    )
    private void phaze$renderCosmeticsInBodySpace(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        if (playerState instanceof PreviewMarker marker
                && marker.phaze$previewSelection() != null) {
            CosmeticsRenderer.renderPreview(
                    marker.phaze$previewSelection(), marker.phaze$previewAlpha(),
                    marker.phaze$previewYaw(),
                    () -> phaze$renderCosmetics(playerState, state, matrices));
            return;
        }
        phaze$renderCosmetics(playerState, state, matrices);
    }

    private void phaze$renderCosmetics(
            PlayerEntityRenderState playerState,
            S state,
            MatrixStack matrices
    ) {
        boolean preview = CosmeticsRenderer.isRenderingPreview();
        VertexConsumerProvider consumers = preview
                ? CosmeticsRenderer.previewVertexConsumers()
                : MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        // getModel() is public in 1.21.11. Calling it through the target type
        // avoids a fragile @Shadow field whose descriptor changed this release.
        EntityModel<?> model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        try {
            if (model instanceof PlayerEntityModel playerModel) {
                MatrixStack.Entry stableLightingEntry = matrices.peek();
                matrices.push();
                playerModel.body.applyTransform(matrices);
                CosmeticsRenderer.renderLocalPlayer(
                        matrices, consumers, playerState, state.light, stableLightingEntry);
                vorga.phazeclient.implement.cosmetics.bridge.PhazePulseRenderer.renderBody(
                        matrices, consumers, playerState, state.light);
                matrices.pop();

                matrices.push();
                playerModel.head.applyTransform(matrices);
                CosmeticsRenderer.renderHeadCosmetic(
                        matrices, consumers, playerState, state.light, stableLightingEntry);
                vorga.phazeclient.implement.cosmetics.bridge.PhazePulseRenderer.renderHead(
                        matrices, consumers, playerState, state.light);
                matrices.pop();
            } else {
                CosmeticsRenderer.renderLocalPlayer(
                        matrices, consumers, playerState, state.light, matrices.peek());
            }
        } finally {
            if (preview) {
                // Still inside EntityGuiElementRenderer's framebuffer override:
                // cosmetics, player depth and translucent glow now compose together.
                CosmeticsRenderer.flushPreviewVertexConsumers();
            }
        }
    }

    // ---------------------------------------------------------------
    // HitColor: capture the overlay int into FeatureRenderer instances
    // that implement OverlayRendered.
    // ---------------------------------------------------------------

    @WrapOperation(
            method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V"
            )},
            require = 1
    )
    private void phaze$renderFeatureWithHitColorOverlay(
            FeatureRenderer<?, ?> featureRenderer,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            EntityRenderState entityRenderState,
            float limbAngle,
            float limbDistance,
            Operation<Void> original
    ) {
        if (HitColor.getInstance().isEnabled() && featureRenderer instanceof OverlayRendered rendered) {
            @SuppressWarnings("unchecked")
            S livingEntityRenderState = (S) entityRenderState;
            int overlay = LivingEntityRenderer.getOverlay(livingEntityRenderState, this.getAnimationCounter(livingEntityRenderState));
            rendered.setOverlay(overlay);
            OverlayReloadListener.event();
        }

        original.call(featureRenderer, matrices, queue, light, entityRenderState, limbAngle, limbDistance);
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
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL")
    )
    private void phaze$drawHitRange(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled()) {
            return;
        }

        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        // Local player goes through WorldRendererMixin's renderEntities
        // TAIL path which knows the camera-relative offset; skip here
        // so Show Self doesn't draw two stacked rings.
        if (player.getId() == playerState.id) {
            return;
        }

        // 1.21.11: Entity.getPos() -> getEntityPos().
        Vec3d pos = new Vec3d(state.x, state.y, state.z);
        if (!pos.isInRange(player.getEntityPos(), config.maxDistance.getInt())) {
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

        // 1.21.11: the renderer no longer hands us a VertexConsumerProvider -
        // the OrderedRenderCommandQueue only accepts pre-baked model/label/
        // custom commands, and HitRangeCircleRenderer emits raw POSITION_COLOR
        // geometry across three different RenderLayers, which does not map onto
        // a single submitCustom() callback. Take the same immediate provider
        // WorldRenderer itself uses for entity geometry: this mixin runs inside
        // WorldRenderer.pushEntityRenders, and WorldRenderer flushes that
        // provider (Immediate.draw()) later in the same world pass, after the
        // entity command queue is dispatched - so the ring still lands in the
        // world pass, depth-tested against terrain, as before.
        VertexConsumerProvider vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        HitRangeCircleRenderer.drawCircle(matrices, vertexConsumers, playerState);
    }
}
