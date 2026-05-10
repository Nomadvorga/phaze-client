package vorga.phazeclient.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.NoRender;

/**
 * Suppresses the entity-outline (glowing) render whenever the
 * {@link NoRender#glowing} toggle is on.
 *
 * <p>Vanilla calls {@code hasOutline(Entity)} once per entity per
 * frame to decide whether to allocate the outline-framebuffer pass
 * for it. Returning {@code false} short-circuits the entire glow
 * pipeline for that entity (silhouette buffer, post-processor, team-
 * colour lookup), so the visual disappears AND the GPU work for it
 * is skipped - cheaper than masking the colour to alpha=0.
 *
 * <p>The original vanilla decision is preserved untouched when the
 * toggle is off, so /effect minecraft:glowing still works as expected.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNoGlowMixin {

    @Inject(method = "hasOutline(Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void phaze$skipGlowOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        NoRender mod = NoRender.getInstance();
        if (mod != null && mod.isEnabled() && mod.glowing.isValue()) {
            cir.setReturnValue(false);
        }
    }
}
