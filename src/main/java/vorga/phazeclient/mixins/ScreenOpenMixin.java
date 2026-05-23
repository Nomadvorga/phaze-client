/**
 * Zoom functionality
 * Code from ok-boomer by glisco (MIT License)
 * Copyright (c) 2022 glisco
 * https://modrinth.com/mod/ok-boomer
 */

package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Zoom;

@Mixin(MinecraftClient.class)
public class ScreenOpenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onScreenOpen(Screen screen, CallbackInfo ci) {
        // Treat opening any screen (inventory / pause / our menu)
        // as if the user released the zoom bind: flip
        // {@link Zoom#zoomActive} off so the FOV animates back to
        // 1x via the normal zoom-out curve and the session does NOT
        // resume after the GUI closes. The actual unzoom animation
        // is driven by {@link GameRendererZoomMixin} reading the
        // flag every frame.
        if (screen != null && Zoom.isZoomActive()) {
            Zoom.setZoomActive(false);
        }
    }
}
