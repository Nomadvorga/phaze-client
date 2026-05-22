package vorga.phazeclient.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Drives {@link CursorManager}'s per-frame request lifecycle from
 * the Screen render hook so EVERY screen (vanilla or Phaze) gets
 * the dynamic cursor treatment with one mixin instead of N. The
 * widgets that opt in (ClickableWidget, TextFieldWidget, our menu
 * components) call {@link CursorManager#requestHand()} /
 * {@link CursorManager#requestBeam()} between {@link #beginFrame}
 * and {@link #endFrame}; the manager picks the highest-priority
 * request and pushes it to GLFW.
 *
 * <p>HEAD clears the per-frame state, TAIL commits. This way even
 * a screen that reads zero hover requests still resets the OS
 * cursor back to the arrow when the user moves off a button - no
 * "cursor stuck on hand after navigating away" bug.
 *
 * <p>The Animations module's master enable + the Dynamic Cursor
 * sub-toggle are checked at TAIL time. When either is off we still
 * call {@code endFrame(false)} so the cursor visibly snaps back to
 * the system arrow the first frame after the user disables the
 * feature, instead of lingering on whatever shape was last applied.
 *
 * <h3>Coverage delegated to per-widget mixins</h3>
 * <ul>
 *   <li>{@link ClickableWidgetCursorMixin} - hand pointer on every
 *       hovered {@code ClickableWidget}, beam on hovered
 *       {@code TextFieldWidget}. This already covers Sodium /
 *       Iris / ModMenu / virtually every modded screen because
 *       those mods build their UIs on top of {@code ClickableWidget}
 *       subclasses (Sodium's {@code FlatButtonWidget extends
 *       AbstractWidget = ClickableWidget}, and similar).</li>
 *   <li>{@link EntryListWidgetCursorMixin} - row-precise hand for
 *       the entry under the mouse, no hand over inter-row gaps or
 *       padding.</li>
 *   <li>{@link SliderWidgetCursorMixin} +
 *       {@link ClickableWidgetDragCursorMixin} - horizontal-resize
 *       cursor while dragging vanilla / modded sliders.</li>
 * </ul>
 *
 * <p>An earlier revision of this mixin walked {@link Screen#children()}
 * and called {@code isMouseOver} on every non-vanilla {@code Element}
 * to catch layout-container layers in modded screens. That caused the
 * cursor to flip to the hand pointer over the dark empty-screen area
 * outside the world-list panel on {@code SelectWorldScreen} (the
 * vanilla screen uses a wide layout container whose {@code isMouseOver}
 * returns {@code true} for the full screen rectangle, and the probe
 * couldn't tell that apart from a real interactive widget). Removed -
 * the per-widget mixins are sufficient for every real interactive
 * element across vanilla and the modded ecosystem.
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
        CursorManager.beginFrame();
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void phaze$cursorEndFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CursorManager.endFrame(Animations.getInstance().isDynamicCursorEnabled());
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
        CursorManager.forceArrow();
    }
}
