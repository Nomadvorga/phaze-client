package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.NoRender;

/**
 * Suppresses the entity-outline (glowing) render whenever the
 * {@link NoRender#glowing} toggle is on.
 *
 * <p>Mojang refactored 1.21.4 so the outline decision lives on
 * {@code MinecraftClient.hasOutline(Entity)} - it's the single
 * predicate {@code WorldRenderer} consults inside the per-entity
 * draw loop before binding the outline framebuffer's vertex consumer
 * (verified in the disassembly: {@code WorldRenderer.render} calls
 * {@code MinecraftClient.hasOutline} immediately before fetching the
 * {@code OutlineVertexConsumerProvider}). The earlier
 * {@code EntityRenderer.hasOutline} method that this codebase
 * previously targeted no longer exists in 1.21.4, hence this rewrite.
 *
 * <p>Returning {@code false} short-circuits the outline framebuffer
 * allocation AND the team-colour lookup, so the visual disappears
 * and the GPU work is skipped - cheaper than masking the colour to
 * alpha=0 downstream.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientNoGlowMixin {

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
}
