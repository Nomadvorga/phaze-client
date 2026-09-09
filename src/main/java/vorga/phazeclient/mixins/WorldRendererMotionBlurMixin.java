package vorga.phazeclient.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.MotionBlur;

/**
 * Captures the camera history used by the motion-blur reprojection pass.
 *
 * <p>This is deliberately separate from the terrain mixins: it is renderer
 * agnostic and therefore remains valid with Sodium.  The original 1.21.4
 * implementation was missing from the 1.21.11 port, leaving the post shader
 * with zero matrices; dividing by their zero W component produced a black
 * frame.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMotionBlurMixin {
    @Shadow @Final private MinecraftClient client;

    @Unique private final Matrix4f phaze$previousModelView = new Matrix4f();
    @Unique private final Matrix4f phaze$previousProjection = new Matrix4f();
    @Unique private final Matrix4f phaze$currentProjection = new Matrix4f();
    @Unique private final Vector3f phaze$previousCameraPosition = new Vector3f();
    @Unique private final Vector3f phaze$currentCameraPosition = new Vector3f();
    @Unique private boolean phaze$motionHistoryValid;

    @Inject(method = "render", at = @At("HEAD"))
    private void phaze$captureMotionBlurFrame(
            ObjectAllocator allocator, RenderTickCounter tickCounter,
            boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix,
            Matrix4f projectionMatrix, Matrix4f frustumMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
            CallbackInfo ci) {
        MotionBlur module = MotionBlur.getInstance();
        if (module == null || !module.isEnabled() || module.shader == null) {
            phaze$motionHistoryValid = false;
            return;
        }

        float tickDelta = tickCounter.getTickProgress(true);
        float fov = ((GameRendererAccessor) client.gameRenderer)
                .invokeGetFov(camera, tickDelta, true);
        phaze$currentProjection.set(client.gameRenderer.getBasicProjectionMatrix(fov));

        Vec3d cameraPosition = camera.getCameraPos();
        // Keep the values close to zero exactly as the 1.21.4 implementation
        // did; this preserves floating-point precision far from spawn.
        phaze$currentCameraPosition.set(
                (float) (cameraPosition.x % 30000.0),
                (float) (cameraPosition.y % 30000.0),
                (float) (cameraPosition.z % 30000.0)
        );

        if (!phaze$motionHistoryValid) {
            phaze$previousModelView.set(positionMatrix);
            phaze$previousProjection.set(phaze$currentProjection);
            phaze$previousCameraPosition.set(phaze$currentCameraPosition);
            phaze$motionHistoryValid = true;
        }

        module.shader.setFrameMotionBlur(
                positionMatrix, phaze$previousModelView,
                phaze$currentProjection, phaze$previousProjection,
                phaze$currentCameraPosition, phaze$previousCameraPosition
        );
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void phaze$advanceMotionBlurFrame(
            ObjectAllocator allocator, RenderTickCounter tickCounter,
            boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix,
            Matrix4f projectionMatrix, Matrix4f frustumMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
            CallbackInfo ci) {
        MotionBlur module = MotionBlur.getInstance();
        if (module == null || !module.isEnabled() || !phaze$motionHistoryValid) {
            return;
        }
        phaze$previousModelView.set(positionMatrix);
        phaze$previousProjection.set(phaze$currentProjection);
        phaze$previousCameraPosition.set(phaze$currentCameraPosition);
    }
}
