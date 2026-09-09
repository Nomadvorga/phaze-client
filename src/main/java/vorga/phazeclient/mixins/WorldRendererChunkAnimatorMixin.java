package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Bolts the {@link ChunkAnimator} drop animation onto the vanilla
 * chunk render path, one {@link ChunkBuilder.BuiltChunk} at a time,
 * on the GPU - without touching geometry, vertex buffers, or the
 * chunk builder thread.
 *
 * <h3>1.21.11 port note - why this moved off {@code GlUniform}</h3>
 * Through 1.21.4 the hook was {@code WorldRenderer.renderLayer},
 * which set a {@code modelOffset} uniform per visible section via
 * {@code GlUniform.set(FFF)} as (originX - cameraX, originY -
 * cameraY, originZ - cameraZ); we {@code @ModifyArgs}-ed that call
 * so the offset stayed a float and the landing eased smoothly
 * instead of snapping in 1-block increments.
 *
 * <p>1.21.11 deleted both halves of that. {@code renderLayer} is
 * gone (terrain submission is now
 * {@code renderBlockLayers(Matrix4fc, double, double, double)},
 * which builds a {@code SectionRenderState} of deferred
 * {@code RenderPass.RenderObject}s), and {@code GlUniform} was
 * reduced to a bare {@code AutoCloseable} - the per-section
 * transform now travels in a std140 UBO slice built from
 * {@code DynamicUniforms.ChunkSectionsValue(Matrix4fc modelView,
 * int x, int y, int z, float visibility, int atlasW, int atlasH)},
 * one instance per BuiltChunk, uploaded in bulk by
 * {@code DynamicUniforms.writeChunkSections}.
 *
 * <p>The section origin in that record is packed as an
 * <b>ivec3</b>, so offsetting {@code x/y/z} would reintroduce
 * exactly the 1-block quantisation the old float path avoided.
 * The {@code modelView} matrix is the only float-precision channel
 * left, and vanilla builds a <em>fresh</em> {@code Matrix4f} copy
 * for every BuiltChunk, so translating our own copy of it shifts
 * that one section and nothing else. {@code M.translate(v)} yields
 * {@code M * T(v)}, i.e. every vertex of the section is moved by
 * {@code +v} in world space - numerically the same displacement
 * the old {@code modelOffset} addition produced.
 *
 * <p>{@code @Local BuiltChunk} still supplies the section identity
 * the animator needs to look up the per-section timestamp; the
 * value is live at the call site (vanilla reads its origin two
 * instructions earlier to fill the ivec3). There is exactly one
 * {@code ChunkSectionsValue} construction in the method, so no
 * {@code ordinal} pin is needed any more.
 *
 * <p><b>Sodium compatibility:</b> Sodium's {@code LevelRendererMixin}
 * {@code @Overwrite}s the vanilla terrain path, completely replacing the
 * vanilla {@code modelOffset} uniform path with its own region
 * renderer - the {@code ChunkSectionsValue} INVOKE target we hook
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
     * caller; {@code @ModifyArg} fires synchronously per section so a
     * shared scratch is safe and avoids one float[3] allocation per
     * BuiltChunk per frame.
     */
    private static final float[] PHAZE$DIR = new float[3];

    @ModifyArg(
            method = "renderBlockLayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/DynamicUniforms$ChunkSectionsValue;"
                            + "<init>(Lorg/joml/Matrix4fc;IIIFII)V"
            ),
            index = 0
    )
    private Matrix4fc phaze$chunkAnimatorModelOffset(Matrix4fc modelView,
                                                     @Local ChunkBuilder.BuiltChunk builtChunk) {
        ChunkAnimator animator = ChunkAnimator.getInstance();
        if (animator == null || !animator.isEnabled() || builtChunk == null) {
            return modelView;
        }

        if (animator.isScaleMode()) {
            float scale = animator.getScale(builtChunk.getOrigin());
            if (scale >= 1.0F) {
                return modelView;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.gameRenderer == null
                    || client.gameRenderer.getCamera() == null) {
                return modelView;
            }
            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            float pivotX = (float) (builtChunk.getOrigin().getX() + 8.0 - camera.x);
            float pivotY = (float) (builtChunk.getOrigin().getY() + 8.0 - camera.y);
            float pivotZ = (float) (builtChunk.getOrigin().getZ() + 8.0 - camera.z);

            // ModelViewMat transforms the already camera-relative terrain
            // position. Conjugating the scale around that section centre
            // keeps the centre fixed while vertices grow out from it.
            return new Matrix4f(modelView)
                    .translate(pivotX, pivotY, pivotZ)
                    .scale(scale)
                    .translate(-pivotX, -pivotY, -pivotZ);
        }

        float magnitude = animator.getYOffset(builtChunk.getOrigin());
        if (magnitude == 0.0F) {
            // Not animating: hand vanilla's own matrix straight back so
            // the steady state costs zero allocations and stays
            // bit-identical to unmodded rendering.
            return modelView;
        }
        animator.writeAnimationDirection(PHAZE$DIR);
        // `magnitude * direction[axis]` on each axis, so a single
        // positive distance setting drives the slide-in along whatever
        // axis the user picked. Float precision flows all the way
        // through the matrix, so the tail of the easing (magnitude ~= 0)
        // lerps continuously to 0 instead of snapping in the 1-block
        // increments the ivec3 section origin would force.
        //
        // Copy rather than mutate: vanilla's Matrix4f is freshly built
        // per section today, but a copy keeps us correct even if some
        // other mod hands a shared/cached matrix through this arg.
        return new Matrix4f(modelView).translate(
                magnitude * PHAZE$DIR[0],
                magnitude * PHAZE$DIR[1],
                magnitude * PHAZE$DIR[2]);
    }
}
