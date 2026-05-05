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
        // Auto-unzoom when entering GUI
        if (screen != null && Zoom.isZoomActive()) {
            Zoom.setZoomActive(false);
        }
    }
}
