package vorga.phazeclient.mixins.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorShaderHelper;

@Mixin(targets = "net.irisshaders.iris.gl.program.Program", remap = false)
public abstract class IrisProgramWorldColorMixin {
    @Unique
    private boolean phaze$worldColorProgramRegistered;

    @Inject(method = "use", at = @At("TAIL"), require = 0, remap = false)
    private void phaze$uploadWorldColorUniforms(CallbackInfo ci) {
        if (!phaze$worldColorProgramRegistered) {
            WorldColorShaderHelper.registerCurrentProgramInstance();
            phaze$worldColorProgramRegistered = true;
        }
        WorldColorShaderHelper.uploadTerrainUniformsToCurrentGlProgram();
    }
}
