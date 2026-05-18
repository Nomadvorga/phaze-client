package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vorga.phazeclient.implement.features.modules.other.Crosshair;

/**
 * Forces the vanilla crosshair to render even when the camera is in
 * third-person view.
 *
 * <h3>How vanilla blocks the crosshair in F5</h3>
 * {@code InGameHud.renderCrosshair} starts with:
 * <pre>{@code
 *   if (!client.options.getPerspective().isFirstPerson()) return;
 * }</pre>
 * Once this guard returns true vanilla skips the entire draw. We
 * can't @Inject and reverse the {@code return} cleanly because by
 * then the method has already short-circuited; intercepting the
 * {@code isFirstPerson()} call itself is the precise fix.
 *
 * <h3>Implementation</h3>
 * {@code @Redirect} on the single {@code Perspective.isFirstPerson()}
 * call inside {@code renderCrosshair}. We pretend the camera is in
 * first person whenever the user has the Crosshair module enabled
 * with the third-person toggle on. Vanilla then proceeds with the
 * usual draw as if the camera was still F1, painting the cross.
 *
 * <p>Other call sites of {@code Perspective.isFirstPerson()} (e.g.
 * the entity render dispatcher's self-cull) are NOT affected,
 * because @Redirect targets only the call inside the method we
 * mixin into.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudCrosshairMixin {

    @Redirect(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z")
    )
    private boolean phaze$forceFirstPersonForCrosshair(Perspective perspective) {
        Crosshair module = Crosshair.getInstance();
        if (module != null && module.isEnabled() && module.showInThirdPerson.isValue()) {
            // Lie to vanilla: claim we're in first person so the
            // crosshair draw proceeds. The actual perspective
            // value is preserved everywhere else - this redirect
            // is scoped to the renderCrosshair method only.
            MinecraftClient mc = MinecraftClient.getInstance();
            // Still respect F1 (hudHidden) so cinematic screenshots
            // stay clean. Vanilla itself doesn't gate on hudHidden
            // in renderCrosshair - the InGameHud.render parent
            // already short-circuits before calling us in that
            // case - so this is just a defensive belt-and-braces.
            if (mc != null && mc.options != null && mc.options.hudHidden) {
                return perspective.isFirstPerson();
            }
            return true;
        }
        return perspective.isFirstPerson();
    }
}
