package vorga.phazeclient.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Horizontal-resize cursor while the user is dragging a Sodium
 * slider. Sodium 0.6.x represents each slider option row as
 * {@code SliderControl$Button} (an inner class of
 * {@code SliderControl}), and routes drag motion through its
 * overridden {@code mouseDragged} method.
 *
 * <p>Hooking that method directly is more reliable than a generic
 * {@code ClickableWidget.mouseDragged} hook, since Sodium's widgets
 * don't extend vanilla {@code ClickableWidget}. The synthetic
 * horizontal-delta call to {@link CursorManager#notifyScroll(double, double)}
 * arms the manager's resize-cursor window for ~150 ms; while the
 * user keeps dragging, every {@code mouseDragged} tick re-arms the
 * window, and once they release the wheel the timer lapses on the
 * next render frame so the cursor returns to whatever the hovered
 * widget asks for.
 *
 * <p>Targets the inner class through {@code @Mixin(targets = ...)}
 * because the {@code SliderControl$Button} class name is package-
 * private inside Sodium and not directly importable from a
 * sibling-package mod. {@code remap = false} because Sodium isn't
 * Yarn-mapped, so the mixin processor must take the target string
 * literally.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl$Button", remap = false)
public abstract class SodiumSliderDragCursorMixin {

    @Inject(method = "mouseDragged", at = @At("HEAD"), require = 0)
    private void phaze$cursorOnDrag(double mouseX, double mouseY,
                                    int button, double deltaX, double deltaY,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        // Sodium sliders are horizontal: synthetic non-zero
        // horizontal delta picks the H-resize shape inside
        // CursorManager and arms the override window.
        CursorManager.notifyScroll(1.0, 0.0);
    }
}
