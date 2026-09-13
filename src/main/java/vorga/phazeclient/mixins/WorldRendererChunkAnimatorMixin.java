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

@Mixin(WorldRenderer.class)
public abstract class WorldRendererChunkAnimatorMixin {

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

            return new Matrix4f(modelView)
                    .translate(pivotX, pivotY, pivotZ)
                    .scale(scale)
                    .translate(-pivotX, -pivotY, -pivotZ);
        }

        float magnitude = animator.getYOffset(builtChunk.getOrigin());
        if (magnitude == 0.0F) {

            return modelView;
        }
        animator.writeAnimationDirection(PHAZE$DIR);

        return new Matrix4f(modelView).translate(
                magnitude * PHAZE$DIR[0],
                magnitude * PHAZE$DIR[1],
                magnitude * PHAZE$DIR[2]);
    }
}
