package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Consolidated {@link Mouse} cursor-pipeline mixin: scroll-window
 * arming + drag-pin release. Both default-priority hooks for the
 * cursor pipeline; merged into one file. The unrelated
 * {@code MouseSmoothCameraMixin} (priority 1100) and
 * {@code MouseZoomMixin} (priority 500) stay in their own files
 * because Mixin needs distinct per-class priorities for ordering
 * the {@code @ModifyArg} chain that smooth-camera and zoom share
 * on {@code changeLookDirection}.
 */
@Mixin(Mouse.class)
public abstract class MouseCursorMixin {

    /** Wheel scroll inside any open screen arms the scroll-cursor
     *  override window. {@code currentScreen != null} gate keeps
     *  in-game wheel events (hotbar, Zoom mod) from tugging at the
     *  cursor while the camera owns the mouse. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), require = 1)
    private void phaze$cursorOnScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen == null) {
            return;
        }
        CursorManager.notifyScroll(horizontal, vertical);
    }

    /** Left-button release globally clears the drag-pin in
     *  {@link CursorManager}. Safety net for vanilla SliderWidget
     *  releases that get swallowed by parent components or
     *  screen-swap-mid-drag. {@code endDrag} is idempotent. */
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void phaze$cursorOnMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CursorManager.endDrag();
        }
    }
}
