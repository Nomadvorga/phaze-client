package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Crosshair;

/**
 * Forces the vanilla crosshair to render even when the camera is in
 * third-person view. Vanilla's {@code renderCrosshair} self-cancels
 * when {@code Perspective.isFirstPerson()} returns false; we re-run
 * the same draw at TAIL of {@code render} when the user has the
 * Crosshair module enabled with the third-person toggle on.
 *
 * <p>The vanilla render call already happens for us when we're in
 * first-person, so we deliberately skip the inject in that case
 * (otherwise we'd double-draw and the inversion-blend crosshair
 * would visibly flicker).
 */
@Mixin(InGameHud.class)
public abstract class InGameHudCrosshairMixin {

    /** {@code @Shadow} pulls the private vanilla method into our
     *  mixin scope so we can call it from the inject without
     *  hitting the access modifier. The method signature must
     *  match exactly - any drift between MC versions would surface
     *  here as a missing-target error at apply-time. */
    @Shadow
    protected abstract void renderCrosshair(DrawContext context, RenderTickCounter tickCounter);

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawCrosshairThirdPerson(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair module = Crosshair.getInstance();
        if (module == null || !module.isEnabled()) return;
        if (!module.showInThirdPerson.isValue()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        // F1 still suppresses the crosshair - we respect that so
        // clean screenshots stay clean.
        if (mc.options.hudHidden) return;
        // First-person already draws via vanilla. Only kick in
        // when the camera is in F5.
        if (mc.options.getPerspective().isFirstPerson()) return;
        // Skip while a screen is open - matches vanilla behaviour
        // (no crosshair when the inventory / chat is on top).
        if (mc.currentScreen != null) return;

        // Vanilla 15x15 crosshair sprite, drawn with the inversion
        // blend. Same call vanilla makes during first-person; the
        // shadow above lets us reach the private method.
        renderCrosshair(context, tickCounter);
    }
}
