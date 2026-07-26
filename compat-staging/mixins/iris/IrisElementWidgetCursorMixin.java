package vorga.phazeclient.mixins.iris;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Hand-cursor request for hovered Iris option widgets in the
 * shader-pack screen and per-pack option screens.
 *
 * <p>Targets every concrete {@code AbstractElementWidget} subclass
 * directly, rather than the abstract parent. The parent's
 * {@code render} is declared abstract so it has no method body for
 * a {@code TAIL} injection to land on - applying the mixin to the
 * parent crashed at class-load with "TAIL could not locate a valid
 * RETURN in the target method" the moment Iris's
 * {@code ShaderPackOptionList.rebuild} touched the parent class.
 *
 * <p>Iris's {@code render} signature includes an explicit
 * {@code hovered} flag that Iris itself sets to {@code true} when
 * the widget should display the hover affordance. Reading it (rather
 * than re-running a bounding-box test) means the cursor follows
 * whatever Iris considers "hovered", including its modifier-key
 * gating and disabled-state handling.
 *
 * <p>{@code remap = false} on the @Inject because all Iris-internal
 * names are intermediary-stable - they're not Yarn-mapped Minecraft
 * methods, just regular mod classes that survived the obfuscation
 * pipeline as-is.
 *
 * <p>Gated behind {@link vorga.phazeclient.mixins.PhazeMixinPlugin}'s
 * {@code IRIS_LOADED} check.
 */
@Mixin(targets = {
        "net.irisshaders.iris.gui.element.widget.BooleanElementWidget",
        "net.irisshaders.iris.gui.element.widget.SliderElementWidget",
        "net.irisshaders.iris.gui.element.widget.StringElementWidget",
        "net.irisshaders.iris.gui.element.widget.ProfileElementWidget",
        "net.irisshaders.iris.gui.element.widget.LinkElementWidget",
        "net.irisshaders.iris.gui.element.widget.CommentedElementWidget",
        "net.irisshaders.iris.gui.element.widget.BaseOptionElementWidget"
}, remap = false)
public abstract class IrisElementWidgetCursorMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void phaze$cursorRequestHand(DrawContext context, int mouseX, int mouseY, float delta, boolean hovered, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        if (hovered) {
            CursorManager.requestHand();
        }
    }
}
