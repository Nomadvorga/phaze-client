package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.Bright;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerBrightMixin {

    @ModifyExpressionValue(
            method = "update(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"
            )
    )
    private Object phaze$injectFullBright(Object original) {
        Bright bright = Bright.getInstance();
        if (bright == null || !bright.isState()) {
            return original;
        }
        if (!(original instanceof Double vanilla)) {
            return original;
        }
        return Math.max(vanilla, bright.brightness.getValue() * 10.0);
    }
}
