package vorga.phazeclient.mixins;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Drag-to-select bridge for the chat input.
 *
 * <p>{@link ChatScreen} overrides {@code mouseClicked} and routes
 * the click to {@link ChatScreen#chatField} directly, bypassing
 * the {@code ParentElement.mouseClicked} default that would
 * normally call {@code setFocused} + {@code setDragging(true)} as
 * a side effect. As a result the screen's drag flag is never
 * armed, so the inherited {@code Screen.mouseDragged} short-
 * circuits and the chat field never receives drag events.
 *
 * <p>Fix: at the TAIL of {@code ChatScreen.mouseClicked}, if the
 * click landed inside the chat field's bbox, mark the screen
 * dragging and re-focus the field. The next {@code mouseDragged}
 * dispatch then reaches the chat field, our generic
 * {@link ClickableWidgetDragSelectMixin} catches it, and the
 * highlight grows with the mouse.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenDragSelectMixin {

    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void phaze$armChatDrag(double mouseX, double mouseY, int button,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (!Animations.getInstance().isComfortableTextSelectionEnabled()) return;
        if (chatField == null) return;

        // Only arm drag when the user actually clicked INSIDE the
        // input strip. Without the bbox check, clicking on a chat
        // message line would also flip dragging on, then the next
        // mouse-down-and-drag anywhere would extend selection -
        // which the user explicitly didn't want.
        int x = chatField.getX();
        int y = chatField.getY();
        int w = chatField.getWidth();
        int h = chatField.getHeight();
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return;
        }

        // Once drag is armed, the inherited mouseDragged path keeps
        // delivering events to chatField even after the cursor
        // leaves the bbox - vanilla's parent-element forwarder only
        // checks {@code isDragging() + getFocused()}, not the
        // mouse position. So selection survives going off the
        // input strip.
        net.minecraft.client.gui.AbstractParentElement self =
                (net.minecraft.client.gui.AbstractParentElement) (Object) this;
        self.setFocused(chatField);
        self.setDragging(true);
    }
}
