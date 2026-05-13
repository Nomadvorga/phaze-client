package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Bolts the {@link ChunkAnimator} drop animation onto the vanilla
 * chunk render path. {@code WorldRenderer.renderLayer} sets a
 * {@code modelOffset} shader uniform once per visible
 * {@link ChunkBuilder.BuiltChunk} as (originX - cameraX,
 * originY - cameraY, originZ - cameraZ); intercepting just the Y
 * component of that uniform's input is the surgical spot where we
 * can shift the chunk on the GPU without touching geometry, vertex
 * buffers, or the chunk builder thread.
 *
 * <p>We {@link ModifyArgs}-inject directly at the
 * {@code glUniform.set(FFF)} call site so the offset is applied
 * as a float to the middle (Y) argument, bypassing the int
 * quantisation we'd get from modifying {@code BlockPos.getY()}.
 * This is what makes the landing smooth instead of snapping in
 * 1-block increments at the tail of the animation.
 *
 * <p>The same method also resets the uniform to (0, 0, 0) once at
 * the bottom of {@code renderLayer} outside the loop; {@code
 * ordinal = 0} on the {@code @At} pins our handler to the first
 * call (inside the while-loop) so we never touch the reset, and
 * {@code @Local BuiltChunk} supplies the section identity the
 * animator needs to look up the per-section timestamp.
 *
 * <p><b>Sodium compatibility:</b> Sodium's {@code LevelRendererMixin}
 * {@code @Overwrite}s {@code renderLayer}, completely replacing the
 * vanilla {@code modelOffset} uniform path with its own region
 * renderer - the {@code GlUniform.set(FFF)V} INVOKE target we hook
 * no longer exists in the merged bytecode, and Mixin rejects our
 * injection at the prepare stage with a same-priority conflict
 * error regardless of {@code require = 0}. This entire mixin is
 * therefore gated off when Sodium is loaded (see
 * {@link PhazeMixinPlugin}) and replaced by
 * {@link vorga.phazeclient.mixins.sodium.DefaultChunkRendererChunkAnimatorMixin}
 * which hooks Sodium's {@code setRegionOffset} path instead. The
 * World-switch / dimension-change tracker reset lives in the
 * separate {@link ClientPlayNetworkHandlerChunkAnimatorResetMixin}
 * so it stays active under both renderers and - critically -
 * doesn't fire on Iris's frequent pipeline rebuilds, which would
 * otherwise visibly restart in-flight animations.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererChunkAnimatorMixin {

    /**
     * Reusable 3-element direction buffer. Render thread is the only
     * caller; {@code @ModifyArgs} fires synchronously per draw so a
     * shared scratch is safe and avoids one float[3] allocation per
     * BuiltChunk per frame.
     */
    private static final float[] PHAZE$DIR = new float[3];

    @ModifyArgs(
            method = "renderLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/GlUniform;set(FFF)V",
                    ordinal = 0
            )
    )
    private void phaze$chunkAnimatorModelOffset(Args args, @Local ChunkBuilder.BuiltChunk builtChunk) {
        ChunkAnimator animator = ChunkAnimator.getInstance();
        if (animator == null || !animator.isEnabled() || builtChunk == null) {
            return;
        }
        float magnitude = animator.getYOffset(builtChunk.getOrigin());
        if (magnitude == 0.0F) {
            return;
        }
        animator.writeAnimationDirection(PHAZE$DIR);
        // Args indices mirror the vanilla call site:
        //   glUniform.set((float)(blockPos.getX() - cameraX),
        //                 (float)(blockPos.getY() - cameraY),
        //                 (float)(blockPos.getZ() - cameraZ));
        // We add `magnitude * direction[axis]` to each component so a
        // single positive distance setting drives the slide-in along
        // whatever axis the user picked. Float precision flows all
        // the way through, so the tail of the easing (magnitude ~= 0)
        // lerps continuously to 0 instead of snapping in integer
        // increments.
        if (PHAZE$DIR[0] != 0.0F) args.set(0, args.<Float>get(0) + magnitude * PHAZE$DIR[0]);
        if (PHAZE$DIR[1] != 0.0F) args.set(1, args.<Float>get(1) + magnitude * PHAZE$DIR[1]);
        if (PHAZE$DIR[2] != 0.0F) args.set(2, args.<Float>get(2) + magnitude * PHAZE$DIR[2]);
    }
}
