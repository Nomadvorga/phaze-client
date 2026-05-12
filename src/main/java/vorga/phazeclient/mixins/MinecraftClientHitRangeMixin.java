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
 * Modifications: ported to Phaze's mixin layout; nearest-player
 * snapshot now lives on the HitRange module instance instead of a
 * static field on the upstream HitRange ClientModInitializer.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.HitRange;

/**
 * Per-client-tick refresh of the nearest player snapshot consumed by
 * {@link LivingEntityRendererHitRangeMixin}. Runs only while the
 * Hit Range module is enabled AND its Nearest Only toggle is on; in
 * every other state it bails out before touching the world entity
 * list so the tick cost stays at "one boolean read".
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientHitRangeMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void phaze$updateHitRangeNearest(CallbackInfo ci) {
        HitRange config = HitRange.getInstance();
        // Two early-outs keep this injection essentially free during
        // normal play - both Module.isEnabled() and BooleanSetting.isValue()
        // are unsynchronised volatile-like reads.
        if (!config.isEnabled() || !config.nearestOnly.isValue()) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        // World#getClosestPlayer walks the entity list; the filter
        // (e -> !e.equals(player)) excludes the local player so the
        // circle never targets ourselves when Nearest Only is on.
        PlayerEntity nearest = player.getWorld().getClosestPlayer(
                player.getX(),
                player.getY(),
                player.getZ(),
                config.maxSearchDistance.getInt(),
                e -> !e.equals(player)
        );
        config.setNearest(nearest);
    }
}
