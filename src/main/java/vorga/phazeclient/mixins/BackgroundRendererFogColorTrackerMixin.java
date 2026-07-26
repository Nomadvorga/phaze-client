package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.base.util.render.FogColorTracker;

@Mixin(value = BackgroundRenderer.class, priority = 1)
public abstract class BackgroundRendererFogColorTrackerMixin {
    @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
    private static Vector4f phaze$trackFogColor(Vector4f color, Camera camera, float tickDelta, ClientWorld world,
                                                int viewDistance, float skyDarkness) {
        FogColorTracker.update(color);
        return color;
    }
}
