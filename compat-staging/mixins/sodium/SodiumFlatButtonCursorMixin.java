package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.FlatButtonWidget;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Hand-cursor request for hovered Sodium {@link FlatButtonWidget}s.
 * Sodium 0.6.x replaces vanilla's
 * {@link net.minecraft.client.gui.widget.ClickableWidget} hierarchy
 * with its own {@link AbstractWidget} → {@code FlatButtonWidget}
 * chain that doesn't extend any vanilla button class, so the
 * generic {@code ClickableWidgetCursorMixin} doesn't fire over
 * Sodium's options panel buttons. This mixin closes that gap.
 *
 * <p>Hooks the per-frame {@code render} TAIL on {@code FlatButtonWidget}
 * (the only public Sodium widget that draws hoverable affordances
 * in 0.6.x; the {@code AbstractWidget} parent is abstract and never
 * instantiated directly). Reads the live {@code mouseX}/{@code mouseY}
 * arguments and runs Sodium's own {@code isMouseOver} test - same
 * source-of-truth Sodium uses for click routing - so the cursor
 * follows whatever Sodium considers a hit.
 *
 * <p>Gated behind {@link vorga.phazeclient.mixins.PhazeMixinPlugin}'s
 * {@code SODIUM_LOADED} check so users without Sodium don't get a
 * {@code NoClassDefFoundError} trying to resolve
 * {@code net.caffeinemc.mods.sodium.*} at classload time.
 */
@Mixin(FlatButtonWidget.class)
public abstract class SodiumFlatButtonCursorMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void phaze$cursorRequestHand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        FlatButtonWidget self = (FlatButtonWidget) (Object) this;
        if (self.isMouseOver(mouseX, mouseY)) {
            CursorManager.requestHand();
        }
    }
}
