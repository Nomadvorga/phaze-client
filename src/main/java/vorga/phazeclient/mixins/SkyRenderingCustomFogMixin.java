package vorga.phazeclient.mixins;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.CustomFog;

@Mixin(SkyRendering.class)
public class SkyRenderingCustomFogMixin {

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void phaze$suppressSunriseGlow(MatrixStack matrices, float tickProgress, int color, CallbackInfo ci) {
        CustomFog module = CustomFog.getInstance();
        if (module == null || !module.isEnabled() || !module.isAffectSky()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return;
        }
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS))) {
            return;
        }
        ci.cancel();
    }
}
