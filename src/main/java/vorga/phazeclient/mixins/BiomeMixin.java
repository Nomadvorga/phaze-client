package vorga.phazeclient.mixins;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;

@Mixin(Biome.class)
public class BiomeMixin {
    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void getPrecipitation(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        WeatherChanger weatherChanger = WeatherChanger.getInstance();
        if (!weatherChanger.isWeatherOverrideActive()) {
            return;
        }

        String weatherType = weatherChanger.weatherType.getSelected();
        if (weatherType.equals("Clear")) {
            cir.setReturnValue(Biome.Precipitation.NONE);
        } else if (weatherType.equals("Snow")) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        } else if (weatherType.equals("Rain") || weatherType.equals("Thunder")) {
            cir.setReturnValue(Biome.Precipitation.RAIN);
        }
    }
}
