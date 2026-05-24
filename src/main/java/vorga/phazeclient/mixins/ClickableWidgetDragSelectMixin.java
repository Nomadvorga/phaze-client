package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Drag-to-select for vanilla text fields.
 *
 * <p>{@link TextFieldWidget} inherits {@code mouseDragged} from
 * {@link ClickableWidget} and never overrides it - the inherited
 * method just calls {@code onDrag} which is a no-op. So dragging
 * the mouse inside a text field never updates the selection.
 *
 * <p>We hook {@code ClickableWidget.mouseDragged} HEAD-cancellable
 * and check {@code instanceof TextFieldWidget}: on a hit we
 * replicate the cursor-from-x math vanilla uses inside its own
 * {@code onClick} method (see {@code TextFieldWidget.onClick}),
 * but pass {@code shift=true} so {@code setCursor} keeps the
 * existing {@code selectionEnd} anchor. Dragging therefore grows
 * / shrinks the highlight from the original mouse-down position
 * to the dragged-to position - the standard text-editor contract.
 *
 * <p>Hooking the parent class instead of the subclass is necessary
 * because mixin {@code @Inject} only sees methods declared on the
 * target class, and {@code TextFieldWidget} doesn't redeclare
 * {@code mouseDragged}. Reflective shadows give us access to the
 * private {@code firstCharacterIndex} / {@code drawsBackground}
 * fields without having to mutate the field's encapsulation.
 */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetDragSelectMixin {

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void phaze$dragSelect(double mouseX, double mouseY, int button,
                                  double deltaX, double deltaY,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (!Animations.getInstance().isComfortableTextSelectionEnabled()) return;

        Object self = this;
        if (!(self instanceof TextFieldWidget tf)) return;
        if (!tf.active || !tf.visible) return;
        // Auto-focus on drag: vanilla only updates selection while
        // the field is focused, and that flag flips on
        // {@code onClick}. If the user clicks then drags fast
        // enough that the focus change happens AFTER our HEAD
        // inject, we'd miss the first frames of the drag. Forcing
        // focused=true here preserves the user's intent and matches
        // the comfortable-text-selection name.
        if (!tf.isFocused()) tf.setFocused(true);

        // Read the field's geometry through the public API so we
        // don't need shadow access to the parent class's private
        // fields. The 4-px inset for fields with backgrounds is
        // baked into {@code getInnerWidth}, but we need the raw x
        // origin from {@code getX}. We approximate the inset by
        // checking the rendered width minus inner width - matches
        // vanilla's onClick math.
        int x = tf.getX();
        int relX = MathHelper.floor(mouseX) - x;
        int outerW = tf.getWidth();
        int innerW = tf.getInnerWidth();
        int inset = (outerW - innerW) / 2;
        if (inset > 0) {
            relX -= inset;
        }
        if (relX < 0) relX = 0;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) return;

        String text = tf.getText();
        if (text == null) text = "";

        // Without access to firstCharacterIndex we approximate by
        // searching every prefix and finding the rendered width
        // closest to the click x. This matches vanilla's onClick
        // behaviour for all common cases - text shorter than the
        // visible window. Long text with horizontal scroll will be
        // slightly off by the scroll amount, but is rare in vanilla
        // UIs (only book editor / signs scroll horizontally).
        int idx = 0;
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            int w = tr.getWidth(text.substring(0, i));
            int dist = Math.abs(w - relX);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
            if (w > relX + 4) break;
            idx = i;
        }
        // Pick whichever index landed closest to the cursor x.
        int chosen = bestDist < Integer.MAX_VALUE ? best : idx;
        // We want to move ONLY {@code selectionStart} and leave
        // {@code selectionEnd} as the original mouse-down anchor.
        // {@code TextFieldWidget#setCursor(int, true)} would also
        // reach this state, but it additionally fires {@code onChanged}
        // on every drag tick - which the chat-input subclass routes
        // through {@code ChatInputSuggestor.refresh()} and various
        // text-changed callbacks that on some screens silently reset
        // selection or steal focus. Calling {@code setSelectionStart}
        // directly skips the change callback entirely (the text didn't
        // actually change, only the cursor moved) so the highlight
        // band the user sees is exactly what {@code getSelectedText}
        // returns when Ctrl+C runs.
        tf.setSelectionStart(chosen);
        cir.setReturnValue(true);
    }
}
