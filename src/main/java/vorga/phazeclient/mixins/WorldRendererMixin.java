package vorga.phazeclient.mixins;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.Fog;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void renderWeather(FrameGraphBuilder builder, Vec3d cameraPos, float tickDelta, Fog fog, CallbackInfo ci) {
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive() && weatherChanger.weatherType.getSelected().equals("Clear")) {
            ci.cancel(); // Skip rendering if weather is set to Clear
        }
    }
}
