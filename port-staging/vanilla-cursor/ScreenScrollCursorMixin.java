package vorga.phazeclient.mixins;

import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Per-widget scroll-cursor trigger for vanilla sliders.
 * {@link SliderWidget} is the base class for {@code OptionSliderWidget}
 * (FOV, render distance, volume, brightness, GUI scale, etc.) - the
 * exact widgets the user reported as not flipping the cursor on
 * scroll. {@link net.minecraft.client.gui.widget.ClickableWidget}
 * itself does NOT override {@code mouseScrolled}, so injecting there
 * silently no-ops; targeting {@code SliderWidget} hits the actual
 * override that consumes the wheel event.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Wheel + non-zero vertical → {@code VRESIZE} ↕ ("изменение
 *       вертикальных размеров").</li>
 *   <li>Wheel + non-zero horizontal (shift+wheel / horizontal wheels)
 *       → {@code HRESIZE} ↔.</li>
 * </ul>
 *
 * <p>{@link CursorManager#notifyScroll(double, double)} arms a ~120 ms
 * window during which {@code endFrame} forces the chosen resize shape
 * regardless of what the hovered widget asked for. Once the timer
 * lapses the regular hand/beam/arrow hover priority resumes, which
 * is what makes the cursor "return to normal" after the user stops
 * scrolling - the original "анимация зависает" symptom.
 */
@Mixin(SliderWidget.class)
public abstract class ScreenScrollCursorMixin {

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"))
    private void phaze$cursorOnScroll(double mouseX, double mouseY,
                                      double horizontal, double vertical,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        CursorManager.notifyScroll(horizontal, vertical);
    }
}
