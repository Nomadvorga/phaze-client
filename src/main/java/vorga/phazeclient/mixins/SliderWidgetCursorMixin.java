package vorga.phazeclient.mixins;

import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Pins the horizontal-resize cursor while the user is actively
 * dragging a {@link SliderWidget}. Slider widgets in vanilla
 * (FOV, render distance, volume, brightness, GUI scale, etc.) move
 * along the X axis, so the ↔ "изменение горизонтальных размеров"
 * shape is the right affordance for the entire press → drag → release
 * arc, instead of the hand pointer the parent
 * {@link ClickableWidgetCursorMixin} would otherwise emit.
 *
 * <p>Trigger pattern: {@code onClick} arms the drag pin in
 * {@link CursorManager}, {@code onRelease} releases it. The pin has
 * no expiry timer, so the resize shape stays glued for the entire
 * hold even when the user pauses without moving the mouse - which
 * happens constantly while picking an exact value. A wheel spin over
 * a slider is also routed to the H shape (sliders move along X, so
 * vertical wheel input still maps to a horizontal value change in
 * vanilla and the cursor should reflect that).
 */
@Mixin(SliderWidget.class)
public abstract class SliderWidgetCursorMixin {

    @Inject(method = "onClick(DD)V", at = @At("HEAD"), require = 0)
    private void phaze$cursorOnClick(double mouseX, double mouseY, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        CursorManager.beginDrag(CursorManager.SHAPE_HRESIZE);
    }

    @Inject(method = "onRelease(DD)V", at = @At("HEAD"), require = 0)
    private void phaze$cursorOnRelease(double mouseX, double mouseY, CallbackInfo ci) {
        // No dynamic-cursor gate - if a drag is somehow in progress we
        // want endDrag to clear it cleanly even after the user toggled
        // the feature off mid-drag, so the cursor doesn't get stuck.
        CursorManager.endDrag();
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), require = 0)
    private void phaze$cursorOnScroll(double mouseX, double mouseY,
                                      double horizontal, double vertical,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        // Slider is a horizontal control - even a vertical wheel tick
        // should show the ↔ shape because the value moves along X.
        // Pretend the delta was horizontal so notifyScroll picks the
        // right shape regardless of which axis the user spun.
        CursorManager.notifyScroll(horizontal != 0.0 ? horizontal : vertical, 0.0);
    }
}
