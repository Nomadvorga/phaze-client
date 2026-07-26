package vorga.phazeclient.mixins;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.WeatherChanger;

@Mixin(TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> cir) {
        if (user instanceof PlayerEntity) {
            WeatherChanger weatherChanger = WeatherChanger.getInstance();
            if (weatherChanger.isWeatherOverrideActive()) {
                String weatherType = weatherChanger.weatherType.getSelected();
                if (weatherType.equals("Thunder") || weatherType.equals("Rain")) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
