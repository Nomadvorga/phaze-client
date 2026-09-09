package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.menu.ItemPickerScreen;
import vorga.phazeclient.implement.menu.MenuScreen;

/**
 * Drives {@link CursorManager}'s per-frame request lifecycle only for
 * Phaze's menu, item picker, and HUD editing performed through chat. Phaze
 * components call {@link CursorManager#requestHand()} /
 * {@link CursorManager#requestBeam()} between {@link #beginFrame}
 * and {@link #endFrame}; the manager picks the highest-priority
 * request and pushes it to GLFW.
 *
 * <p>HEAD clears the per-frame state, TAIL commits. This way even
 * a screen that reads zero hover requests still resets the OS
 * cursor back to the arrow when the user moves off a button - no
 * "cursor stuck on hand after navigating away" bug.
 *
 * <p>Vanilla widgets and third-party screens have no cursor hooks. Chat opens
 * the lifecycle solely so {@code HudCursorRelay} can apply move/resize shapes
 * over Phaze HUD elements; ordinary chat controls keep the system cursor.
 */
@Mixin(Screen.class)
public abstract class ScreenCursorMixin {

    /**
     * Targets {@code renderWithTooltip} (which is {@code final} on
     * the Screen base class) instead of {@code render}, because lots
     * of vanilla and modded screens override {@code render} without
     * calling {@code super.render(...)} - GameOptionsScreen,
     * SoundOptionsScreen, ControlsOptionsScreen, modded config
     * screens all do this. Injecting into the override-able render
     * method meant our begin/end frame never fired for those screens
     * and the OS cursor stayed stuck on whatever shape the previous
     * screen requested. {@code renderWithTooltip} runs unconditionally
     * for every screen the client opens, so begin/end frame are now
     * always paired.
     */
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void phaze$cursorBeginFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof MenuScreen || (Object) this instanceof ItemPickerScreen
                || (Object) this instanceof ChatScreen) {
            CursorManager.beginFrame();
        }
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void phaze$cursorEndFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof MenuScreen || (Object) this instanceof ItemPickerScreen
                || (Object) this instanceof ChatScreen) {
            CursorManager.endFrame(true);
        }
    }

    /**
     * When a screen closes mid-game (Esc out of menu, F3 close, etc.)
     * Minecraft hides the cursor and re-grabs the mouse for camera
     * control. Force-resetting to the arrow here means the next time
     * a screen opens it starts from a clean default - belt and braces
     * because endFrame already runs every render frame, but on rare
     * paths (e.g. screen swap before render) the reset would otherwise
     * be skipped.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void phaze$cursorOnRemoved(CallbackInfo ci) {
        if ((Object) this instanceof MenuScreen || (Object) this instanceof ItemPickerScreen
                || (Object) this instanceof ChatScreen) {
            CursorManager.forceArrow();
        }
    }
}
