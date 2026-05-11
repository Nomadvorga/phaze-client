package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.Animations;

/**
 * Smooth-F5 implementation: makes the third-person camera distance ease
 * in/out instead of snapping when the player presses F5. Three coordinated
 * hooks into {@link Camera#update}:
 *
 * <ol>
 *   <li>HEAD inject - ticks the {@link Animations#tickSmoothF5(Perspective)}
 *       interpolator once per camera frame. Reading vanilla's current
 *       perspective here (not from the {@code thirdPerson} parameter)
 *       gives the {@code BACK} vs {@code FRONT} distinction the
 *       interpolator needs to skip the no-op {@code BACK<->FRONT}
 *       transitions.</li>
 *   <li>{@code @ModifyVariable} on the {@code thirdPerson} arg - forces it
 *       to {@code true} while the interpolator still has work to do
 *       ({@link Animations#isF5AnimationActive()}). This is what makes the
 *       camera physically move during a {@code THIRD -> FIRST} slide;
 *       without the override vanilla's {@code update} would skip its
 *       {@code clipToSpace}/{@code moveBy} branch the moment the
 *       perspective field reads as {@code FIRST_PERSON}.</li>
 *   <li>{@code @ModifyArg} on the {@code clipToSpace(F)} call inside the
 *       third-person branch - substitutes vanilla's fixed
 *       {@code BASE_CAMERA_DISTANCE} (4.0F) with the live interpolated
 *       distance. Vanilla's collision clamp ({@code clipToSpace} reduces
 *       the request to fit between the player and any block) still
 *       applies, so the camera respects walls during the slide.</li>
 * </ol>
 *
 * Each hook short-circuits cleanly when the feature is disabled, leaving
 * vanilla camera handling completely untouched.
 */
@Mixin(Camera.class)
public class CameraSmoothF5Mixin {

    /**
     * Ticks the interpolator once per camera frame. We deliberately poll
     * {@code mc.options.getPerspective()} instead of trusting the
     * {@code thirdPerson} boolean: the interpolator wants the full
     * {@link Perspective} enum so it can tell {@code THIRD_PERSON_BACK}
     * from {@code THIRD_PERSON_FRONT} and skip the no-op transitions.
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void phaze$tickSmoothF5(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Animations animations = Animations.getInstance();
        if (mc == null || mc.options == null || animations == null) {
            return;
        }
        animations.tickSmoothF5(mc.options.getPerspective());
    }

    /**
     * Forces {@code thirdPerson=true} for the rest of {@code update} while
     * the interpolator is mid-slide. {@code argsOnly=true} keeps the
     * override scoped to the method parameter (vanilla's local
     * {@code thirdPerson} field assignment at the top of the method picks
     * up the modified value). {@code ordinal=0} targets the first boolean
     * arg (there are two: {@code thirdPerson} and {@code inverseView};
     * we only want the first).
     */
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean phaze$forceThirdPersonDuringSlide(boolean thirdPerson) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isSmoothF5Enabled()) {
            return thirdPerson;
        }
        // OR with the active flag so vanilla third-person still goes
        // through the same code path; we only flip false -> true when the
        // user is sliding back into first-person.
        return thirdPerson || animations.isF5AnimationActive();
    }

    /**
     * Substitutes vanilla's {@code BASE_CAMERA_DISTANCE} (4.0F) passed to
     * {@code clipToSpace} with the interpolator's current distance.
     * {@code clipToSpace} itself still does the collision clamp so the
     * camera stops short of walls even mid-slide.
     */
    @ModifyArg(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float phaze$smoothCameraDistance(float distance) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isSmoothF5Enabled()) {
            return distance;
        }
        return animations.currentF5Distance();
    }
}
