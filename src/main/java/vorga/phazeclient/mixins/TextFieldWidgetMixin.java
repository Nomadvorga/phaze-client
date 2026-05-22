package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

/**
 * Consolidated {@link TextFieldWidget} mixin merging the previously
 * separate cursor-shape (I-beam) and password-mask features. Both
 * hook the same {@code renderWidget} lifecycle so co-locating them
 * cuts mixin processing overhead without changing observable
 * behaviour.
 *
 * <h3>Cursor I-beam</h3>
 * On TAIL of {@code renderWidget}, when the dynamic-cursor toggle is
 * on and the live mouse coords fall inside the field's bounding box,
 * request the I-beam shape. We do a direct bbox test instead of
 * reading {@code self.isHovered()} because vanilla's hovered flag is
 * updated inside the parent {@code ClickableWidget.render} and is
 * stale at this hook point on certain subclasses (search inputs in
 * world-list / server-list).
 *
 * <h3>Password mask</h3>
 * Only fires when {@code currentScreen instanceof ChatScreen} and
 * the StreamerMode "Hide Passwords" toggle is active. Pattern is a
 * swap-on-render: HEAD replaces {@code text} with the masked copy,
 * vanilla draws it, TAIL restores the original. The mask preserves
 * length so cursor / selection indices stay valid against the
 * original.
 *
 * <h3>Why both can share the file</h3>
 * They use independent {@code @Inject} methods (no shared state, no
 * conflicting cancellation, different injection points: the cursor
 * injector at TAIL only, the password pair at HEAD + TAIL). Mixin
 * processor merges all of them into the same transformed class
 * regardless.
 */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {

    @Shadow private String text;

    /**
     * Stash for the original text between the password-mask HEAD
     * swap and TAIL restore. {@code null} when no swap happened on
     * the current frame, so the TAIL handler short-circuits without
     * touching {@code text}.
     */
    @Unique
    private String phaze$savedText = null;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void phaze$maskPasswordHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        StreamerMode streamer = StreamerMode.getInstance();
        if (streamer == null || !streamer.isHidePasswordsEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || !(mc.currentScreen instanceof ChatScreen)) {
            // Only mask the chat input - other text fields (server
            // name, book editor, sign etc.) never carry chat
            // commands so a slash there is just literal text and
            // must render verbatim.
            return;
        }
        if (this.text == null || this.text.isEmpty()) {
            return;
        }
        String masked = StreamerMode.maskPasswordIfMatching(this.text);
        if (masked == null || masked.equals(this.text)) {
            return;
        }
        phaze$savedText = this.text;
        this.text = masked;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void phaze$maskPasswordTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (phaze$savedText == null) {
            return;
        }
        this.text = phaze$savedText;
        phaze$savedText = null;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void phaze$cursorRequestBeam(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        TextFieldWidget self = (TextFieldWidget) (Object) this;
        if (!self.visible || !self.active) {
            return;
        }
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            CursorManager.requestBeam();
        }
    }
}
