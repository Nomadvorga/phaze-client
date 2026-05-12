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
 * Modifications: ported to Phaze's mixin layout and feature module
 * gate; cancellation predicates rewritten against HitRange's setting
 * surface instead of HitRangeConfig.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.HitRange;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

/**
 * Hooks the per-entity {@code LivingEntityRenderer.render} TAIL to draw
 * the hit-range circle once vanilla has finished placing the entity.
 * The circle lives on the entity's local matrix stack so the radius
 * automatically tracks the entity's rendered position (interpolated by
 * vanilla for free).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererHitRangeMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL")
    )
    private void phaze$drawHitRange(
            LivingEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        HitRange config = HitRange.getInstance();
        if (!config.isEnabled()) {
            return;
        }

        // Only players get the circle — mirrors upstream behaviour and
        // prevents the ring from being drawn around mobs / villagers
        // where the reach metric is less meaningful.
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        // The render path for the local player runs through
        // WorldRendererHitRangeMixin's renderEntities TAIL inject (it
        // pulls the camera-relative offset itself); skip the duplicate
        // here so toggling Show Self doesn't draw two stacked rings.
        if (player.getId() == playerState.id) {
            return;
        }

        Vec3d pos = new Vec3d(state.x, state.y, state.z);
        if (!pos.isInRange(player.getPos(), config.maxDistance.getInt())) {
            return;
        }

        // Nearest-only filter: skip every player except the one the
        // per-tick mixin marked as the closest. The match is by entity
        // ID so a re-spawned player with the same name still gates
        // correctly.
        if (config.nearestOnly.isValue()) {
            if (config.getNearest() == null || config.getNearest().getId() != playerState.id) {
                return;
            }
        }

        // Hide the circle in degenerate render states where the entity
        // is itself invisible / dying / sleeping. The circle stuck on
        // a corpse or a sleeping bed cover looks wrong.
        if (playerState.deathTime > 0.0f || playerState.invisibleToPlayer || playerState.sleepingDirection != null) {
            return;
        }

        HitRangeCircleRenderer.drawCircle(matrices, vertexConsumers, playerState);
    }
}
