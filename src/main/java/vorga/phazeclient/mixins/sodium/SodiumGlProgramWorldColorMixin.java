package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.colorcorrection.WorldColorShaderHelper;

/** Uploads correction uniforms whenever Sodium binds a chunk program. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlProgram", remap = false)
public abstract class SodiumGlProgramWorldColorMixin {
    @Unique
    private boolean phaze$worldColorProgramRegistered;

    @Inject(method = "bind", at = @At("TAIL"), remap = false, require = 1)
    private void phaze$uploadWorldColorUniforms(CallbackInfo ci) {
        if (!phaze$worldColorProgramRegistered) {
            WorldColorShaderHelper.registerCurrentProgramInstance();
            phaze$worldColorProgramRegistered = true;
        }
        // Sodium's patched shader carries both target pairs and selects the
        // fluid pair from the per-vertex marker, so the terrain fallback pair
        // is irrelevant here.
        WorldColorShaderHelper.uploadTerrainUniformsToCurrentGlProgram();
    }
}
