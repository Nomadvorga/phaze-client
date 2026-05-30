package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.menu.MenuUiSettings;

@Mixin(Screen.class)
public abstract class ScreenPanoramaPresetMixin {
    @Shadow protected int width;
    @Shadow protected int height;

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void phaze$renderCustomPanorama(DrawContext context, float delta, CallbackInfo ci) {
        if (MenuUiSettings.getInstance().getSelectedPanoramaPreset() == MenuUiSettings.PanoramaPreset.VANILLA) {
            return;
        }
        MenuUiSettings.getInstance().getSelectedPanoramaPreset().getRenderer().render(context, this.width, this.height, 1.0F);
        ci.cancel();
    }
}
