package vorga.phazeclient.mixins.sodium;

import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Hand-cursor request for hovered Sodium option controls. Sodium
 * 0.6.x renders every option row through {@link ControlElement} (and
 * its subclasses {@code CyclingControlElement},
 * {@code TickBoxControlElement}, {@code SliderControl$Button}); the
 * row is the clickable affordance the user expects the hand pointer
 * over.
 *
 * <p>{@code render} is the Yarn name; Sodium's source compiles it as
 * {@code method_25394} after Loom remap, and the Mixin processor
 * resolves the descriptor through the standard intermediary lookup
 * even though Sodium itself isn't part of Yarn - the descriptor
 * matches because {@code Renderable.render} is the Minecraft
 * interface method.
 *
 * <p>Gated behind {@link vorga.phazeclient.mixins.PhazeMixinPlugin}'s
 * {@code SODIUM_LOADED} check so users without Sodium don't get a
 * {@code NoClassDefFoundError} trying to resolve the target class
 * at classload time.
 */
@Mixin(ControlElement.class)
public abstract class SodiumControlElementCursorMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void phaze$cursorRequestHand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        ControlElement<?> self = (ControlElement<?>) (Object) this;
        if (self.isMouseOver(mouseX, mouseY)) {
            CursorManager.requestHand();
        }
    }
}
