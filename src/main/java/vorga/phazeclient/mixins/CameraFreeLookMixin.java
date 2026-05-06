package vorga.phazeclient.mixins;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.implement.features.modules.other.FreeLook;

@Mixin(Camera.class)
public class CameraFreeLookMixin {

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void phaze$modifyRotation(Args args) {
        FreeLook freeLook = FreeLook.getInstance();
        if (freeLook == null || !freeLook.isEnabled() || !freeLook.isActive()) {
            return;
        }

        args.set(0, freeLook.getCameraYaw(1.0f));
        args.set(1, freeLook.getCameraPitch(1.0f));
    }
}
