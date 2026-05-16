package vorga.phazeclient.mixins;

import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.NoFluid;

/**
 * Intercepts {@link Camera#getSubmersionType()} so the rest of the
 * vanilla fog / overlay / tint pipeline thinks the camera is in air
 * even when it's actually underwater or underlava.
 *
 * <h3>Why TAIL not HEAD</h3>
 * Vanilla computes the submersion type by sampling the fluid state
 * at the camera position, which we don't want to replicate. By
 * letting vanilla finish and intercepting the return, we get the
 * correct WATER / LAVA / BUBBLE_COLUMN / POWDER_SNOW / NONE without
 * any of our own block-state inspection code. We then rewrite ONLY
 * the WATER and LAVA cases - powder-snow and bubble-column overlays
 * are deliberately untouched because the user asked specifically for
 * "туман воды/лавы" and silently hiding powder-snow blindness would
 * cross from "no fog" into "wallhack-adjacent" territory.
 *
 * <h3>Per-fluid gating</h3>
 * The two paths are separate because the user wanted a single
 * SelectSetting toggle ("Water / Lava / Both"). Both branches
 * funnel through the {@link NoFluid#shouldHideWater()} /
 * {@link NoFluid#shouldHideLava()} predicates so the mixin doesn't
 * have to re-read the {@code mode} string itself - keeps the
 * gating logic colocated with the module class.
 *
 * <h3>Per-frame cost</h3>
 * {@code Camera.getSubmersionType} is called once per frame (in
 * {@code GameRenderer.renderWorld} for the fog setup, plus a small
 * handful of overlay-renderer call sites which all read the cached
 * value). One enum compare + one boolean check per frame is
 * negligible.
 */
@Mixin(Camera.class)
public abstract class CameraNoFluidMixin {

    @Inject(method = "getSubmersionType", at = @At("RETURN"), cancellable = true, require = 0)
    private void phaze$rewriteSubmersionType(CallbackInfoReturnable<CameraSubmersionType> cir) {
        NoFluid mod = NoFluid.getInstance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        CameraSubmersionType type = cir.getReturnValue();
        if (type == CameraSubmersionType.WATER && mod.shouldHideWater()) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        } else if (type == CameraSubmersionType.LAVA && mod.shouldHideLava()) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        }
    }
}
