/**
 * Based on HitboxPlus by PingIsFun (https://github.com/PingIsFun/hitboxplus)
 * Licensed under MIT License
 * Original Copyright (c) 2022 PingIsFun
 * Modified for Phaze Client - simplified version for Minecraft 1.21.4
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.HitboxCustomizer;
import vorga.phazeclient.util.HitboxRenderUtil;

/**
 * Mixin to WorldRenderer.renderHitbox() to replace vanilla hitbox rendering with custom colors
 * This is the exact same approach as HitboxPlus mod
 */
@Mixin(WorldRenderer.class)
public class EntityRendererHitboxMixin {

    /**
     * Inject at HEAD of renderHitbox() and cancel vanilla rendering if module is enabled
     * This is exactly how HitboxPlus does it
     * Using intermediary mapping since Yarn mapping might be different
     */
    @Inject(
        method = "method_3956",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void phaze$renderCustomHitbox(MatrixStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green, float blue, CallbackInfo ci) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        
        // If module is enabled, render custom hitbox and cancel vanilla rendering
        if (module.isEnabled()) {
            HitboxRenderUtil.drawBox(matrices, vertices, entity, tickDelta);
            ci.cancel();
        }
    }
}
