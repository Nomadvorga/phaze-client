package vorga.phazeclient.mixins.sodium;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * I-beam cursor for the Reese's Sodium Options search field.
 * Reese's Sodium Options ships its own
 * {@code SearchTextFieldComponent} that extends Sodium's
 * {@code AbstractWidget} (not vanilla {@code TextFieldWidget}), so
 * neither the vanilla text-field cursor mixin nor the generic
 * Sodium control mixin emits the right shape over it. This mixin
 * hooks the component's own per-frame render and runs its
 * {@code isMouseOver} test - the exact same call Reese's uses for
 * click routing - then asks {@link CursorManager} for the I-beam
 * shape on a hit.
 *
 * <h3>Method name</h3>
 * Targets the intermediary name {@code method_25394} (the
 * {@code Renderable.render(DrawContext, int, int, float)} contract
 * Sodium's {@code AbstractWidget} implements after Loom remap).
 * The Yarn name {@code render} works in dev runtime but Mixin's
 * production-mappings refmap doesn't carry an entry for
 * Reese's-internal classes - those are pure-mod classes, not
 * Yarn-mapped Minecraft surfaces - so a {@code method = "render"}
 * inject silently fails to find the target on production launches
 * (the symptom: cursor never flips over the search field on the
 * built jar even though it works in dev).
 *
 * <p>{@code remap = false} on both the {@code @Mixin} target string
 * AND the {@code @Inject method} keeps Mixin from trying to remap
 * either name - the class name is literal-correct (Reese's package
 * is the same in dev and prod) and the method name is already the
 * intermediary form.
 *
 * <p>Gated behind {@code REESES_LOADED} in
 * {@link vorga.phazeclient.mixins.PhazeMixinPlugin}.
 */
@Mixin(targets = "me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components.SearchTextFieldComponent", remap = false)
public abstract class ReesesSearchFieldCursorMixin {

    @Inject(method = "method_25394", at = @At("TAIL"), require = 0, remap = false)
    private void phaze$cursorRequestBeam(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        // The component class itself implements isMouseOver via
        // Sodium's AbstractWidget supertype; cast gives us the
        // standard hit-test without reflection.
        if (((net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget) (Object) this).isMouseOver(mouseX, mouseY)) {
            CursorManager.requestBeam();
        }
    }
}
