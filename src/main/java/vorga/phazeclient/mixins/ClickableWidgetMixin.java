package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Consolidated {@link ClickableWidget} mixin merging the cursor-
 * shape hover handler and the slider-drag detector. Both target
 * {@code ClickableWidget} so co-locating them is a pure
 * source-organisation change.
 *
 * <h3>Hover hand cursor</h3>
 * On TAIL of {@code render}, request the hand pointer if the live
 * mouse position falls inside the widget bbox. Subclasses of
 * {@link TextFieldWidget} get the I-beam shape instead (we duplicate
 * the check here as a fallback for subclasses that override
 * {@code renderWidget} without calling super, which is rare but
 * happens in modded inputs).
 *
 * <h3>Drag-slider detection</h3>
 * On HEAD of {@code mouseDragged}, if the dragged widget's class
 * name looks slider-shaped (contains "slider" / "range" / "bar")
 * and isn't the vanilla {@link SliderWidget} (which has its own
 * mixin pinning the H-resize cursor on press), arm the
 * {@link CursorManager} scroll-override window so the cursor flips
 * to ↔ for the duration of the drag.
 */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$cursorRequestHand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        ClickableWidget self = (ClickableWidget) (Object) this;
        if (!self.visible || !self.active) {
            return;
        }
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (!over) {
            return;
        }
        if (self instanceof TextFieldWidget) {
            CursorManager.requestBeam();
            return;
        }
        CursorManager.requestHand();
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void phaze$cursorOnDrag(double mouseX, double mouseY,
                                    int button, double deltaX, double deltaY,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        ClickableWidget self = (ClickableWidget) (Object) this;
        // Vanilla SliderWidget has its own onClick → beginDrag pin
        // via SliderWidgetCursorMixin; skip it here to keep the
        // priority chain clean.
        if (self instanceof SliderWidget) {
            return;
        }
        if (!phaze$looksLikeSlider(self)) {
            return;
        }
        // Re-arm the scroll-override window every drag tick. The
        // 150 ms self-expiry inside CursorManager keeps the resize
        // cursor stable across the entire drag.
        CursorManager.notifyScroll(1.0, 0.0);
    }

    /**
     * Class-name match for "this looks like a slider/range control".
     * Sodium uses {@code FlatSliderWidget}, Iris uses
     * {@code IrisOptionSliderWidget}, generic mods often use
     * {@code Slider}/{@code Bar}/{@code Range} in the type name.
     */
    private static boolean phaze$looksLikeSlider(ClickableWidget w) {
        String n = w.getClass().getSimpleName();
        if (n == null || n.isEmpty()) {
            return false;
        }
        String lc = n.toLowerCase(java.util.Locale.ROOT);
        return lc.contains("slider")
                || lc.contains("range")
                || lc.contains("bar");
    }
}
