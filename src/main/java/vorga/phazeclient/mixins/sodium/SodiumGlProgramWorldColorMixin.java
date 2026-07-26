package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorShaderHelper;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlProgram", remap = false)
public abstract class SodiumGlProgramWorldColorMixin {
    @Unique
    private boolean phaze$worldColorProgramRegistered;

    @Inject(method = "bind", at = @At("TAIL"), remap = false)
    private void phaze$uploadWorldColorUniforms(CallbackInfo ci) {
        if (!phaze$worldColorProgramRegistered) {
            WorldColorShaderHelper.registerCurrentProgramInstance();
            phaze$worldColorProgramRegistered = true;
        }
        WorldColorShaderHelper.uploadTerrainUniformsToCurrentGlProgram();
    }
}
