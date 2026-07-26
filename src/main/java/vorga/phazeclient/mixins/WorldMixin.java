package vorga.phazeclient.mixins;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;

@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow @Final public boolean isClient;
    @Shadow protected float rainGradient;
    @Shadow protected float thunderGradient;

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void getRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (!isClient) {
            return;
        }
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive()) {
            String weatherType = weatherChanger.weatherType.getSelected();
            if (weatherType.equals("Clear")) {
                cir.setReturnValue(0.0f);
            } else if (weatherType.equals("Rain") || weatherType.equals("Thunder")) {
                cir.setReturnValue(weatherChanger.getRainGradient());
            } else if (weatherType.equals("Snow")) {
                cir.setReturnValue(weatherChanger.getRainGradient());
            }
        }
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
    private void getThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (!isClient) {
            return;
        }
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive()) {
            if (weatherChanger.weatherType.getSelected().equals("Thunder")) {
                cir.setReturnValue(weatherChanger.getThunderGradient());
            } else {
                cir.setReturnValue(0.0f);
            }
        }
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void isRaining(CallbackInfoReturnable<Boolean> cir) {
        if (!isClient) {
            return;
        }
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive()) {
            String weatherType = weatherChanger.weatherType.getSelected();
            cir.setReturnValue(weatherType.equals("Rain") || weatherType.equals("Thunder") || weatherType.equals("Snow"));
            cir.cancel();
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void isThundering(CallbackInfoReturnable<Boolean> cir) {
        if (!isClient) {
            return;
        }
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (weatherChanger.isWeatherOverrideActive()) {
            cir.setReturnValue(weatherChanger.weatherType.getSelected().equals("Thunder"));
            cir.cancel();
        }
    }
}
