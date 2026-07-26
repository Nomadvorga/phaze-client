package vorga.phazeclient.mixins;

import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorShaderHelper;

@Mixin(ShaderProgram.class)
public abstract class ShaderProgramWorldColorMixin {
    @Unique
    private boolean phaze$worldColorProgramRegistered;

    @Inject(method = "bind", at = @At("TAIL"), require = 0)
    private void phaze$uploadTerrainUniformsOnBind(CallbackInfo ci) {
        if (!phaze$worldColorProgramRegistered) {
            WorldColorShaderHelper.registerCurrentProgramInstance();
            phaze$worldColorProgramRegistered = true;
        }
        WorldColorShaderHelper.uploadTerrainUniformsToCurrentGlProgram();
    }
}
