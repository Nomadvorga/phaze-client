/*
 * MIT License
 *
 * Phaze port of the "hitrange" mod by uku3lig (uku).
 * Original source: https://github.com/uku3lig/hitrange
 *
 * Copyright (c) 2023 uku
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * Modifications: ported to Phaze's mixin layout; gate is now the
 * HitRange Module + showSelf BooleanSetting instead of HitRangeConfig.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

import java.util.List;

/**
 * Draws the hit-range circle around the local player itself. The
 * per-entity {@link LivingEntityRendererHitRangeMixin} skips the local
 * player because vanilla's entity render path does not always emit the
 * camera-attached player (e.g. in first-person view); this mixin runs
 * at {@code WorldRenderer.renderEntities} TAIL where the player's
 * lerped position is already known and the camera-relative offset is
 * trivially derivable.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererHitRangeMixin {

    @Shadow @Final private BufferBuilderStorage bufferBuilders;

    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "renderEntities", at = @At(value = "TAIL"))
    private void phaze$drawHitRangeSelf(
            MatrixStack matrices,
            VertexConsumerProvider.Immediate immediate,
            Camera camera,
            RenderTickCounter tickCounter,
            List<Entity> entities,
            CallbackInfo ci
    ) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled() || !config.showSelf.isValue()) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        // Lerp the player's position with the partial-tick delta so the
        // ring moves smoothly with the player at high FPS, rather than
        // ticking 20 times per second.
        float tickDelta = tickCounter.getTickDelta(false);
        double px = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
        double py = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
        double pz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());

        // Convert world-space player position to camera-relative space,
        // because the matrix stack we receive is rooted at the camera.
        Vec3d playerPos = new Vec3d(px, py, pz).subtract(camera.getPos());

        // Borrow the active player-renderer's render state - vanilla
        // already populated state.x/y/z/sneaking/name during the per-
        // tick render-state build, so the CircleRenderer's color and
        // height decisions stay consistent with the per-entity mixin.
        EntityRenderer<? super ClientPlayerEntity, ?> renderer = this.entityRenderDispatcher.getRenderer(player);
        PlayerEntityRenderState state = (PlayerEntityRenderState) renderer.getAndUpdateRenderState(player, tickDelta);

        matrices.push();
        matrices.translate(playerPos.x, playerPos.y, playerPos.z);
        VertexConsumerProvider vertexConsumers = this.bufferBuilders.getEntityVertexConsumers();
        HitRangeCircleRenderer.drawCircle(matrices, vertexConsumers, state);
        matrices.pop();
    }
}
