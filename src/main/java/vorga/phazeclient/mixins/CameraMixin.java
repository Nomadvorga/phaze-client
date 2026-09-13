package vorga.phazeclient.mixins;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.FreeLook;
import vorga.phazeclient.implement.features.modules.other.NoFluid;
import vorga.phazeclient.implement.features.modules.other.SmoothCamera;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @org.spongepowered.asm.mixin.Unique private static float phaze$smoothCamYaw = Float.NaN;
    @org.spongepowered.asm.mixin.Unique private static float phaze$smoothCamPitch = Float.NaN;
    @org.spongepowered.asm.mixin.Unique private static long phaze$smoothCamLastNanos = 0L;
    @org.spongepowered.asm.mixin.Unique private static net.minecraft.client.option.Perspective phaze$smoothCamLastPerspective = null;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void phaze$smoothCameraRotation(Args args) {
        SmoothCamera module = SmoothCamera.getInstance();
        if (module == null || !module.isEnabled()) {
            phaze$smoothCamYaw = Float.NaN;
            phaze$smoothCamPitch = Float.NaN;
            phaze$smoothCamLastNanos = 0L;
            phaze$smoothCamLastPerspective = null;
            return;
        }

        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook != null && freeLook.isEnabled() && freeLook.isActive()) {
            return;
        }

        float targetYaw = args.<Float>get(0);
        float targetPitch = args.<Float>get(1);

        float smoothness = module.getSmoothness();
        if (smoothness < 0.0F) smoothness = 0.0F;
        if (smoothness > 0.99F) smoothness = 0.99F;
        float halfLife = smoothness * 0.15F;

        long now = System.nanoTime();
        float dtSeconds;
        if (phaze$smoothCamLastNanos == 0L) {
            dtSeconds = 1.0F / 60.0F;
        } else {
            dtSeconds = (now - phaze$smoothCamLastNanos) / 1_000_000_000.0F;
            if (dtSeconds > 0.25F) dtSeconds = 0.25F;
            if (dtSeconds < 0.0F) dtSeconds = 0.0F;
        }
        phaze$smoothCamLastNanos = now;

        float factor;
        if (halfLife <= 0.0001F) {
            factor = 1.0F;
        } else {
            factor = 1.0F - (float) Math.pow(0.5, dtSeconds / halfLife);
        }
        if (factor < 0.0F) factor = 0.0F;
        if (factor > 1.0F) factor = 1.0F;

        net.minecraft.client.option.Perspective currentPerspective = phaze$currentPerspective();

        if (Float.isNaN(phaze$smoothCamYaw) || Float.isNaN(phaze$smoothCamPitch)) {
            phaze$smoothCamYaw = targetYaw;
            phaze$smoothCamPitch = targetPitch;
            phaze$smoothCamLastPerspective = currentPerspective;
            return;
        }

        if (currentPerspective != phaze$smoothCamLastPerspective) {
            phaze$smoothCamYaw = targetYaw;
            phaze$smoothCamPitch = targetPitch;
            phaze$smoothCamLastPerspective = currentPerspective;
            return;
        }

        float deltaYaw = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - phaze$smoothCamYaw);
        float deltaPitch = targetPitch - phaze$smoothCamPitch;

        if (Math.abs(deltaYaw) > 170.0F || Math.abs(deltaPitch) > 80.0F) {
            return;
        }

        phaze$smoothCamYaw += deltaYaw * factor;
        phaze$smoothCamPitch += deltaPitch * factor;

        if (phaze$smoothCamPitch > 90.0F) phaze$smoothCamPitch = 90.0F;
        if (phaze$smoothCamPitch < -90.0F) phaze$smoothCamPitch = -90.0F;

        args.set(0, phaze$smoothCamYaw);
        args.set(1, phaze$smoothCamPitch);
    }

    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.client.option.Perspective phaze$currentPerspective() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return null;
        return mc.options.getPerspective();
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void phaze$freeLookRotation(Args args) {
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook == null || !freeLook.isEnabled() || !freeLook.isActive()) {
            return;
        }
        args.set(0, freeLook.getCameraYaw(1.0f));
        args.set(1, freeLook.getCameraPitch(1.0f));
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void phaze$tickSmoothF5(World area, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Animations animations = Animations.getInstance();
        if (mc == null || mc.options == null || animations == null) {
            return;
        }
        animations.tickSmoothF5(mc.options.getPerspective());
    }

    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean phaze$forceThirdPersonDuringSlide(boolean thirdPerson) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isSmoothF5Enabled()) {
            return thirdPerson;
        }
        return thirdPerson || animations.isF5AnimationActive();
    }

    @ModifyArg(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float phaze$smoothCameraDistance(float distance) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isSmoothF5Enabled()) {
            return distance;
        }
        return animations.currentF5Distance();
    }

    @Inject(method = "getSubmersionType", at = @At("RETURN"), cancellable = true, require = 0)
    private void phaze$rewriteSubmersionType(CallbackInfoReturnable<CameraSubmersionType> cir) {
        NoFluid mod = NoFluid.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        CameraSubmersionType type = cir.getReturnValue();
        if (type == CameraSubmersionType.WATER && mod.shouldHideWater()) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        } else if (type == CameraSubmersionType.LAVA && mod.shouldHideLava()) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        }
    }
}
