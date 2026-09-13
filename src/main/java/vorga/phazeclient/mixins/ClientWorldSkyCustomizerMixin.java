package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.SkyCustomizer;

@Mixin(value = EnvironmentAttributeInterpolator.class, priority = 1300)
public abstract class ClientWorldSkyCustomizerMixin {

    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private Object phaze$tintSkyColor(Object original, EnvironmentAttribute<?> attribute, float tickProgress) {
        if (attribute != EnvironmentAttributes.SKY_COLOR_VISUAL || !(original instanceof Integer packed)) {
            return original;
        }

        SkyCustomizer module = SkyCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }

        EnvironmentAttributeInterpolator self = (EnvironmentAttributeInterpolator) (Object) this;
        Float skyBrightness = self.get(EnvironmentAttributes.SKY_LIGHT_FACTOR_VISUAL, tickProgress);

        return module.applyToSky(packed, skyBrightness == null ? 0.5F : skyBrightness);
    }
}
