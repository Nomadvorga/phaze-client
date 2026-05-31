/**
 * Zoom functionality
 * Code from ok-boomer by glisco (MIT License)
 * Copyright (c) 2022 glisco
 * https://modrinth.com/mod/ok-boomer
 */

package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Zoom;
import vorga.phazeclient.implement.menu.MainMenuScreen;

@Mixin(MinecraftClient.class)
public class ScreenOpenMixin {
    @Unique
    private boolean phaze$redirectingTitleScreen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen phaze$replaceTitleScreenEarly(Screen screen) {
        if (screen instanceof TitleScreen
                && !(screen instanceof MainMenuScreen)) {
            return new MainMenuScreen();
        }
        return screen;
    }

    @ModifyVariable(method = "setScreen", at = @At(value = "STORE"), ordinal = 0)
    private Screen phaze$replaceGeneratedTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen
                && !(screen instanceof MainMenuScreen)) {
            return new MainMenuScreen();
        }
        return screen;
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void phaze$replaceCurrentTitleScreenAfterSet(Screen screen, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (this.phaze$redirectingTitleScreen
                || !(client.currentScreen instanceof TitleScreen)
                || client.currentScreen instanceof MainMenuScreen) {
            return;
        }

        this.phaze$redirectingTitleScreen = true;
        try {
            client.setScreen(new MainMenuScreen());
        } finally {
            this.phaze$redirectingTitleScreen = false;
        }
    }

    @Inject(method = "setOverlay", at = @At("TAIL"))
    private void phaze$replaceLingeringTitleScreenAfterOverlayClose(Overlay overlay, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (overlay != null) {
            return;
        }

        if (client.currentScreen == null && client.world == null) {
            client.setScreen(new MainMenuScreen());
            return;
        }

        if (client.currentScreen instanceof TitleScreen
                && !(client.currentScreen instanceof MainMenuScreen)) {
            client.setScreen(new MainMenuScreen());
        }
    }

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
